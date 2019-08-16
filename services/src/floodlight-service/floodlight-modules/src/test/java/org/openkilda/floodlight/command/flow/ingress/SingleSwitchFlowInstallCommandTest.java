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

import org.openkilda.floodlight.api.FlowEndpoint;
import org.openkilda.floodlight.api.MeterConfig;
import org.openkilda.floodlight.model.SwitchDescriptor;
import org.openkilda.floodlight.utils.MetadataAdapter;
import org.openkilda.floodlight.utils.MetadataAdapter.MetadataMatch;
import org.openkilda.floodlight.utils.OfAdapter;
import org.openkilda.model.Cookie;

import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.OFMetadata;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.U64;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SingleSwitchFlowInstallCommandTest extends AbstractIngressFlowSegmentInstallCommandTest {
    private final static FlowEndpoint defaultEgressEndpoint = new FlowEndpoint(mapSwitchId(dpId), 3, 0, 0);

    @Test
    public void happyPathDefaultPort() throws Exception {
        SingleSwitchFlowInstallCommand command = makeCommand(endpointZeroVlan, meterConfig);
        executeCommand(command, 1);

        List<OFAction> applyActions = new ArrayList<>();
        List<OFInstruction> instructions = new ArrayList<>();
        OfAdapter.INSTANCE.makeMeterCall(of, command.getMeterConfig().getId(), applyActions, instructions);
        applyActions.add(of.actions().buildOutput().setPort(
                OFPort.of(command.getEgressEndpoint().getPortNumber())).build());
        instructions.add(of.instructions().applyActions(applyActions));

        OFFlowAdd expected = of.buildFlowAdd()
                .setPriority(SingleSwitchFlowInstallCommand.FLOW_PRIORITY - 1)
                .setCookie(U64.of(command.getCookie().getValue()))
                .setMatch(of.buildMatch()
                         .setExact(MatchField.IN_PORT, OFPort.of(command.getEndpoint().getPortNumber()))
                         .build())
                .setInstructions(instructions)
                .build();
        verifyOfMessageEquals(expected, getWriteRecord(0).getRequest());
    }

    @Test
    public void happyPathOuterVlan() throws Exception {
        SingleSwitchFlowInstallCommand command = makeCommand(endpointSingleVlan, meterConfig);
        executeCommand(command, 2);

        verifyOfMessageEquals(makeOuterVlanMatchAddReference(command), getWriteRecord(0).getRequest());

        final SwitchDescriptor swDef = new SwitchDescriptor(sw);

        List<OFAction> applyActions = new ArrayList<>();
        List<OFInstruction> instructions = new ArrayList<>();
        OfAdapter.INSTANCE.makeMeterCall(of, command.getMeterConfig().getId(), applyActions, instructions);
        applyActions.add(of.actions().buildOutput().setPort(
                OFPort.of(command.getEgressEndpoint().getPortNumber())).build());
        instructions.add(of.instructions().applyActions(applyActions));

        MetadataMatch metadata = MetadataAdapter.INSTANCE.addressOuterVlan(
                OFVlanVidMatch.ofVlan(command.getEndpoint().getOuterVlanId()));
        OFFlowAdd expected = of.buildFlowAdd()
                .setTableId(swDef.getTableIngress())
                .setPriority(SingleSwitchFlowInstallCommand.FLOW_PRIORITY - 10)
                .setCookie(U64.of(command.getCookie().getValue()))
                .setMatch(of.buildMatch()
                        .setExact(MatchField.IN_PORT, OFPort.of(command.getEndpoint().getPortNumber()))
                        .setMasked(MatchField.METADATA,
                                   OFMetadata.of(metadata.getValue()), OFMetadata.of(metadata.getMask()))
                                  .build())
                .setInstructions(instructions)
                .build();
        verifyOfMessageEquals(expected, getWriteRecord(1).getRequest());
    }

    @Test
    public void happyPathOuterAndInnerVlan() throws Exception {
        SingleSwitchFlowInstallCommand command = makeCommand(endpointDoubleVlan, meterConfig);
        executeCommand(command, 2);

        verifyOfMessageEquals(makeOuterVlanMatchAddReference(command), getWriteRecord(0).getRequest());

        final SwitchDescriptor swDef = new SwitchDescriptor(sw);

        List<OFAction> applyActions = new ArrayList<>();
        List<OFInstruction> instructions = new ArrayList<>();
        OfAdapter.INSTANCE.makeMeterCall(of, command.getMeterConfig().getId(), applyActions, instructions);
        applyActions.add(of.actions().popVlan());
        applyActions.add(of.actions().buildOutput().setPort(
                OFPort.of(command.getEgressEndpoint().getPortNumber())).build());
        instructions.add(of.instructions().applyActions(applyActions));

        MetadataMatch metadata = MetadataAdapter.INSTANCE.addressOuterVlan(
                OFVlanVidMatch.ofVlan(command.getEndpoint().getOuterVlanId()));
        OFFlowAdd expected = of.buildFlowAdd()
                .setTableId(swDef.getTableIngress())
                .setPriority(SingleSwitchFlowInstallCommand.FLOW_PRIORITY)
                .setCookie(U64.of(command.getCookie().getValue()))
                .setMatch(OfAdapter.INSTANCE.matchVlanId(of, of.buildMatch(), command.getEndpoint().getInnerVlanId())
                                  .setExact(MatchField.IN_PORT, OFPort.of(command.getEndpoint().getPortNumber()))
                                  .setMasked(MatchField.METADATA,
                                             OFMetadata.of(metadata.getValue()), OFMetadata.of(metadata.getMask()))
                                  .build())
                .setInstructions(instructions)
                .build();
        verifyOfMessageEquals(expected, getWriteRecord(1).getRequest());
    }

    @Test
    public void happyPathDefaultPortSameOutput() throws Exception {
        FlowEndpoint egressEndpoint = new FlowEndpoint(
                endpointZeroVlan.getDatapath(), endpointZeroVlan.getPortNumber(), 0, 0);
        SingleSwitchFlowInstallCommand command = makeCommand(endpointZeroVlan, egressEndpoint, meterConfig);
        executeCommand(command, 1);

        List<OFAction> applyActions = new ArrayList<>();
        List<OFInstruction> instructions = new ArrayList<>();
        OfAdapter.INSTANCE.makeMeterCall(of, command.getMeterConfig().getId(), applyActions, instructions);
        applyActions.add(of.actions().buildOutput().setPort(OFPort.IN_PORT).build());
        instructions.add(of.instructions().applyActions(applyActions));

        OFFlowAdd expected = of.buildFlowAdd()
                .setPriority(SingleSwitchFlowInstallCommand.FLOW_PRIORITY - 1)
                .setCookie(U64.of(command.getCookie().getValue()))
                .setMatch(of.buildMatch()
                                  .setExact(MatchField.IN_PORT, OFPort.of(command.getEndpoint().getPortNumber()))
                                  .build())
                .setInstructions(instructions)
                .build();
        verifyOfMessageEquals(expected, getWriteRecord(0).getRequest());
    }

    @Override
    protected SingleSwitchFlowInstallCommand makeCommand(FlowEndpoint endpoint, MeterConfig meter) {
        return makeCommand(endpoint, defaultEgressEndpoint, meter);
    }

    protected SingleSwitchFlowInstallCommand makeCommand(
            FlowEndpoint endpoint, FlowEndpoint endpointEgress, MeterConfig meter) {
        UUID commandId = UUID.randomUUID();
        String flowId = "single-switch-flow-install-flow-id";
        Cookie cookie = new Cookie(1);

        return new SingleSwitchFlowInstallCommand(
                messageContext, commandId, flowId, cookie, endpoint, meter, endpointEgress);
    }
}
