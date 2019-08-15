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
import org.openkilda.floodlight.api.FlowTransitEncapsulation;
import org.openkilda.floodlight.api.MeterConfig;
import org.openkilda.floodlight.model.SwitchDescriptor;
import org.openkilda.floodlight.utils.MetadataAdapter;
import org.openkilda.floodlight.utils.OfAdapter;
import org.openkilda.model.Cookie;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.SwitchId;

import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.OFMetadata;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.U64;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class IngressFlowSegmentInstallCommandTest extends AbstractIngressFlowSegmentInstallCommandTest {
    @Test
    public void happyPathDefaultPort() throws Exception {
        IngressFlowSegmentInstallCommand command = makeCommand(endpointDefaultPort, meterConfig);
        executeCommand(command, 1);

        List<OFInstruction> instructions = new ArrayList<>();
        List<OFAction> applyActions = new ArrayList<>();
        OfAdapter.INSTANCE.makeMeterCall(of, command.getMeterConfig().getId(), applyActions, instructions);
        applyActions.add(of.actions().pushVlan(EthType.VLAN_FRAME));
        applyActions.add(OfAdapter.INSTANCE.setVlanIdAction(of, command.getEncapsulation().getId()));
        applyActions.add(of.actions().buildOutput().setPort(OFPort.of(command.getIslPort())).build());
        instructions.add(of.instructions().applyActions(applyActions));
        OFFlowAdd expect = of.buildFlowAdd()
                .setPriority(IngressFlowSegmentInstallCommand.FLOW_PRIORITY - 1)
                .setCookie(U64.of(command.getCookie().getValue()))
                .setMatch(of.buildMatch()
                                  .setExact(MatchField.IN_PORT, OFPort.of(command.getEndpoint().getPortNumber()))
                                  .build())
                .setInstructions(instructions)
                .build();
        verifyOfMessageEquals(expect, getWriteRecord(0).getRequest());
    }

    @Test
    public void happyPathOuterVlan() throws Exception {
        IngressFlowSegmentInstallCommand command = makeCommand(endpointOuterVlan, meterConfig);
        executeCommand(command, 2);
        
        // table - dispatch
        OFFlowAdd expect = makeOuterVlanMatchAddReference(command);
        verifyOfMessageEquals(expect, getWriteRecord(0).getRequest());

        // table - ingress
        final SwitchDescriptor swDesc = new SwitchDescriptor(sw);
        final MetadataAdapter.MetadataMatch metadata = MetadataAdapter.INSTANCE.addressOuterVlan(
                OFVlanVidMatch.ofVlan(command.getEndpoint().getOuterVlanId()));

        List<OFAction> applyActions = new ArrayList<>();
        List<OFInstruction> instructions = new ArrayList<>();
        OfAdapter.INSTANCE.makeMeterCall(of, command.getMeterConfig().getId(), applyActions, instructions);
        instructions.add(of.instructions().applyActions(applyActions));
        applyActions.add(of.actions().pushVlan(EthType.VLAN_FRAME));
        applyActions.add(OfAdapter.INSTANCE.setVlanIdAction(of, command.getEncapsulation().getId()));
        applyActions.add(of.actions().buildOutput().setPort(OFPort.of(command.getIslPort())).build());

        expect = of.buildFlowAdd()
                .setTableId(swDesc.getTableIngress())
                .setPriority(IngressFlowSegmentInstallCommand.FLOW_PRIORITY - 10)
                .setCookie(U64.of(command.getCookie().getValue()))
                .setMatch(of.buildMatch()
                                  .setExact(MatchField.IN_PORT, OFPort.of(command.getEndpoint().getPortNumber()))
                                  .setMasked(MatchField.METADATA, OFMetadata.of(metadata.getValue()),
                                             OFMetadata.of(metadata.getMask()))
                                  .build())
                .setInstructions(instructions)
                .build();
        verifyOfMessageEquals(expect, getWriteRecord(1).getRequest());
    }

    @Test
    public void happyPathOuterAndInnerVlan() throws Exception {
        IngressFlowSegmentInstallCommand command = makeCommand(endpointOuterAndInnerVlan, meterConfig);
        executeCommand(command, 2);
        
        // table - dispatch
        OFFlowAdd expect = makeOuterVlanMatchAddReference(command);
        verifyOfMessageEquals(expect, getWriteRecord(0).getRequest());

        // table - ingress
        final SwitchDescriptor swDesc = new SwitchDescriptor(sw);
        final MetadataAdapter.MetadataMatch metadata = MetadataAdapter.INSTANCE.addressOuterVlan(
                OFVlanVidMatch.ofVlan(command.getEndpoint().getOuterVlanId()));

        List<OFAction> applyActions = new ArrayList<>();
        List<OFInstruction> instructions = new ArrayList<>();
        OfAdapter.INSTANCE.makeMeterCall(of, command.getMeterConfig().getId(), applyActions, instructions);
        instructions.add(of.instructions().applyActions(applyActions));
        applyActions.add(of.actions().pushVlan(EthType.VLAN_FRAME));
        applyActions.add(OfAdapter.INSTANCE.setVlanIdAction(of, command.getEncapsulation().getId()));
        applyActions.add(of.actions().buildOutput().setPort(OFPort.of(command.getIslPort())).build());

        expect = of.buildFlowAdd()
                .setTableId(swDesc.getTableIngress())
                .setPriority(IngressFlowSegmentInstallCommand.FLOW_PRIORITY)
                .setCookie(U64.of(command.getCookie().getValue()))
                .setMatch(OfAdapter.INSTANCE.matchVlanId(of, of.buildMatch(), command.getEndpoint().getInnerVlanId())
                                  .setExact(MatchField.IN_PORT, OFPort.of(command.getEndpoint().getPortNumber()))
                                  .setMasked(MatchField.METADATA, OFMetadata.of(metadata.getValue()),
                                             OFMetadata.of(metadata.getMask()))
                                  .build())
                .setInstructions(instructions)
                .build();
        verifyOfMessageEquals(expect, getWriteRecord(1).getRequest());
    }

    @Override
    protected IngressFlowSegmentInstallCommand makeCommand(FlowEndpoint endpoint, MeterConfig meter) {
        UUID commandId = UUID.randomUUID();
        String flowId = "ingress-segment-install-flow-id";
        Cookie cookie = new Cookie(1);
        FlowTransitEncapsulation encapsulation = new FlowTransitEncapsulation(96, FlowEncapsulationType.TRANSIT_VLAN);
        int islPort = 6;

        return new IngressFlowSegmentInstallCommand(
                messageContext, new SwitchId(dpId.getLong()), commandId, flowId, cookie, endpoint, meter,
                islPort, encapsulation);
    }
}
