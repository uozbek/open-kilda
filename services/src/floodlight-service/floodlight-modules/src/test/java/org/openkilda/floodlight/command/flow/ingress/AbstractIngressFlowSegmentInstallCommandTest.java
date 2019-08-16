/* Copyright 2019 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.floodlight.command.flow.ingress;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.reset;

import org.openkilda.floodlight.command.flow.FlowSegmentReport;
import org.openkilda.floodlight.command.meter.MeterInstallCommand;
import org.openkilda.floodlight.command.meter.MeterReport;
import org.openkilda.floodlight.error.SwitchErrorResponseException;
import org.openkilda.floodlight.error.SwitchOperationException;
import org.openkilda.floodlight.error.UnsupportedSwitchOperationException;
import org.openkilda.floodlight.model.SwitchDescriptor;
import org.openkilda.floodlight.utils.MetadataAdapter;
import org.openkilda.floodlight.utils.OfAdapter;

import com.google.common.collect.ImmutableList;
import org.junit.Assert;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFBadRequestCode;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionMeter;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionApplyActions;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionMeter;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionWriteActions;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.U64;

import java.util.List;
import java.util.concurrent.CompletableFuture;

abstract class AbstractIngressFlowSegmentInstallCommandTest extends AbstractIngressFlowSegmentCommandTest {
    @Test
    public void noMeterRequested() throws Exception {
        switchFeaturesSetup(true);
        replayAll();

        AbstractIngressFlowSegmentCommand command = makeCommand(endpointSingleVlan, null);
        CompletableFuture<FlowSegmentReport> result = command.execute(commandProcessor);
        verifyWriteCount(2);
        verifySuccessCompletion(result);
        verifyNoMeterCall((OFFlowAdd) getWriteRecord(1).getRequest());
    }

    @Test
    public void errorNoMeterSupport() throws Exception {
        switchFeaturesSetup(false);
        expectMeter(new UnsupportedSwitchOperationException(dpId, "Switch doesn't support meters (unit-test)"));
        replayAll();

        AbstractIngressFlowSegmentCommand command = makeCommand(endpointSingleVlan, meterConfig);
        CompletableFuture<FlowSegmentReport> result = command.execute(commandProcessor);
        verifyWriteCount(2);
        verifySuccessCompletion(result);
        verifyNoMeterCall((OFFlowAdd) getWriteRecord(1).getRequest());
    }

    @Test
    public void errorOnMeterManipulation() {
        switchFeaturesSetup(true);
        expectMeter(new SwitchErrorResponseException(dpId, "fake fail to install meter error"));
        reset(sessionService);
        reset(session);
        replayAll();

        AbstractIngressFlowSegmentCommand command = makeCommand(endpointSingleVlan, meterConfig);
        CompletableFuture<FlowSegmentReport> result = command.execute(commandProcessor);
        verifyErrorCompletion(result, SwitchErrorResponseException.class);
    }

    @Test
    public void errorOnFlowMod() {
        switchFeaturesSetup(true);
        expectMeter();
        replayAll();

        AbstractIngressFlowSegmentCommand command = makeCommand(endpointSingleVlan, meterConfig);
        CompletableFuture<FlowSegmentReport> result = command.execute(commandProcessor);

        getWriteRecord(0).getFuture()
                .completeExceptionally(new SwitchErrorResponseException(
                        dpId, of.errorMsgs().buildBadRequestErrorMsg().setCode(OFBadRequestCode.BAD_LEN).build()));
        verifyErrorCompletion(result, SwitchOperationException.class);
    }

    private void verifyNoMeterCall(OFFlowAdd request) {
        for (OFInstruction instructiuon : request.getInstructions()) {
            if (instructiuon instanceof OFInstructionMeter) {
                Assert.fail("Found unexpected meter call");
            } else if (instructiuon instanceof OFInstructionApplyActions) {
                verifyNoMeterCall(((OFInstructionApplyActions) instructiuon).getActions());
            } else if (instructiuon instanceof OFInstructionWriteActions) {
                verifyNoMeterCall(((OFInstructionWriteActions) instructiuon).getActions());
            }
        }
    }

    private void verifyNoMeterCall(List<OFAction> actions) {
        for (OFAction entry : actions) {
            if (entry instanceof OFActionMeter) {
                Assert.fail("Found unexpected meter call");
            }
        }
    }

    @Override
    protected void expectMeter(MeterReport report) {
        expect(commandProcessor.chain(anyObject(MeterInstallCommand.class)))
                .andReturn(CompletableFuture.completedFuture(report));
    }

    protected OFFlowAdd makeOuterVlanMatchAddReference(AbstractIngressFlowSegmentCommand command) {
        SwitchDescriptor swDesc = new SwitchDescriptor(sw);
        MetadataAdapter.MetadataMatch metadata = MetadataAdapter.INSTANCE.addressOuterVlan(
                OFVlanVidMatch.ofVlan(command.getEndpoint().getOuterVlanId()));

        return of.buildFlowAdd()
                .setPriority(AbstractIngressFlowSegmentCommand.FLOW_PRIORITY)
                .setCookie(U64.of(command.getCookie().getValue()))
                .setMatch(OfAdapter.INSTANCE.matchVlanId(of, of.buildMatch(), command.getEndpoint().getOuterVlanId())
                                  .setExact(MatchField.IN_PORT, OFPort.of(command.getEndpoint().getPortNumber()))
                                  .build())
                .setInstructions(ImmutableList.of(
                        of.instructions().applyActions(ImmutableList.of(of.actions().popVlan())),
                        of.instructions().writeMetadata(metadata.getValue(), metadata.getMask()),
                        of.instructions().gotoTable(swDesc.getTableIngress())))
                .build();
    }
}
