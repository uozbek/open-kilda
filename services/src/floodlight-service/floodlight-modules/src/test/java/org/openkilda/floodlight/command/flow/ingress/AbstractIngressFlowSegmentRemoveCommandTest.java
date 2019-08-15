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

import org.openkilda.floodlight.command.flow.FlowSegmentReport;
import org.openkilda.floodlight.command.meter.MeterRemoveCommand;
import org.openkilda.floodlight.command.meter.MeterReport;
import org.openkilda.floodlight.error.SwitchErrorResponseException;
import org.openkilda.floodlight.error.SwitchOperationException;
import org.openkilda.floodlight.error.UnsupportedSwitchOperationException;

import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFBadRequestCode;

import java.util.concurrent.CompletableFuture;

abstract class AbstractIngressFlowSegmentRemoveCommandTest extends AbstractIngressFlowSegmentCommandTest {
    @Test
    public void noMeterRequested() throws Exception {
        switchFeaturesSetup(false);
        replayAll();

        AbstractIngressFlowSegmentCommand command = makeCommand(endpointOuterVlan, null);
        verifySuccessCompletion(command.execute(commandProcessor));
    }

    @Test
    public void errorNoMeterSupport() throws Exception {
        switchFeaturesSetup(false);
        expectMeter(new UnsupportedSwitchOperationException(dpId, "Switch doesn't support meters (unit-test)"));
        replayAll();

        AbstractIngressFlowSegmentCommand command = makeCommand(endpointOuterVlan, meterConfig);
        verifySuccessCompletion(command.execute(commandProcessor));
    }

    @Test
    public void errorOnMeterManipulation() {
        switchFeaturesSetup(true);
        expectMeter(new SwitchErrorResponseException(dpId, "fake fail to install meter error"));
        replayAll();

        AbstractIngressFlowSegmentCommand command = makeCommand(endpointOuterVlan, meterConfig);
        CompletableFuture<FlowSegmentReport> result = command.execute(commandProcessor);
        verifyErrorCompletion(result, SwitchErrorResponseException.class);
    }

    @Test
    public void errorOnFlowMod() {
        switchFeaturesSetup(true);
        replayAll();

        AbstractIngressFlowSegmentCommand command = makeCommand(endpointOuterVlan, meterConfig);
        CompletableFuture<FlowSegmentReport> result = command.execute(commandProcessor);

        getWriteRecord(0).getFuture()
                .completeExceptionally(new SwitchErrorResponseException(
                        dpId, of.errorMsgs().buildBadRequestErrorMsg().setCode(OFBadRequestCode.BAD_LEN).build()));
        verifyErrorCompletion(result, SwitchOperationException.class);
    }

    @Override
    protected void expectMeter(MeterReport report) {
        expect(commandProcessor.chain(anyObject(MeterRemoveCommand.class)))
                .andReturn(CompletableFuture.completedFuture(report));
    }
}
