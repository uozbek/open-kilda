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

package org.openkilda.floodlight.command.flow.egress;

import org.openkilda.floodlight.utils.OfAdapter;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.Cookie;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowTransitEncapsulation;

import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;

import java.util.UUID;

public class EgressFlowSegmentInstallCommandTest extends EgressFlowSegmentBlankCommandTest {
    @Test
    public void happyPathTransitVlanZeroVlanToZeroVlan() throws Exception {
        FlowEndpoint ingressEndpoint = new FlowEndpoint(mapSwitchId(ingresDpId), 1, 0, 0);
        EgressFlowSegmentInstallCommand command = makeCommand(endpointZeroPort, ingressEndpoint, encapsulationVlan);
        executeCommand(command, 1);

        OFFlowAdd expected = of.buildFlowAdd()
                .setPriority(EgressFlowSegmentInstallCommand.FLOW_PRIORITY)
                .setCookie(U64.of(command.getCookie().getValue()))
                .setMatch(OfAdapter.INSTANCE.matchVlanId(of, of.buildMatch(), command.getEncapsulation().getId())
                                  .setExact(MatchField.IN_PORT, OFPort.of(command.getIngressIslPort()))
                                  .build())
                .setInstructions(ImmutableList.of(
                        of.instructions().applyActions(ImmutableList.of(
                                of.actions().popVlan(),
                                of.actions().buildOutput()
                                        .setPort(OFPort.of(command.getEndpoint().getPortNumber()))
                                        .build()))))
                .build();
        verifyOfMessageEquals(expected, getWriteRecord(0).getRequest());
    }

    @Test
    public void happyPathTransitVlanZeroVlanToSingleVlan() throws Exception {
        FlowEndpoint ingressEndpoint = new FlowEndpoint(mapSwitchId(ingresDpId), 2, 0, 0);
        EgressFlowSegmentInstallCommand command = makeCommand(endpointSingleVlan, ingressEndpoint, encapsulationVlan);
        executeCommand(command, 1);

        OFFlowAdd expected = of.buildFlowAdd()
                .setPriority(EgressFlowSegmentInstallCommand.FLOW_PRIORITY)
                .setCookie(U64.of(command.getCookie().getValue()))
                .setMatch(OfAdapter.INSTANCE.matchVlanId(of, of.buildMatch(), command.getEncapsulation().getId())
                                  .setExact(MatchField.IN_PORT, OFPort.of(command.getIngressIslPort()))
                                  .build())
                .setInstructions(ImmutableList.of(
                        of.instructions().applyActions(ImmutableList.of(
                                OfAdapter.INSTANCE.setVlanIdAction(of, command.getEndpoint().getOuterVlanId()),
                                of.actions().buildOutput()
                                        .setPort(OFPort.of(command.getEndpoint().getPortNumber()))
                                        .build()))))
                .build();
        verifyOfMessageEquals(expected, getWriteRecord(0).getRequest());
    }

    @Test
    public void happyPathTransitVlanZeroVlanToDoubleVlan() throws Exception {
        FlowEndpoint ingressEndpoint = new FlowEndpoint(mapSwitchId(ingresDpId), 3, 0, 0);
        EgressFlowSegmentInstallCommand command = makeCommand(
                endpointDoubleVlan, ingressEndpoint, encapsulationVlan);
        executeCommand(command, 1);

        OFFlowAdd expected = of.buildFlowAdd()
                .setPriority(EgressFlowSegmentInstallCommand.FLOW_PRIORITY)
                .setCookie(U64.of(command.getCookie().getValue()))
                .setMatch(OfAdapter.INSTANCE.matchVlanId(of, of.buildMatch(), command.getEncapsulation().getId())
                                  .setExact(MatchField.IN_PORT, OFPort.of(command.getIngressIslPort()))
                                  .build())
                .setInstructions(ImmutableList.of(
                        of.instructions().applyActions(ImmutableList.of(
                                OfAdapter.INSTANCE.setVlanIdAction(of, command.getEndpoint().getInnerVlanId()),
                                of.actions().pushVlan(EthType.VLAN_FRAME),
                                OfAdapter.INSTANCE.setVlanIdAction(of, command.getEndpoint().getOuterVlanId()),
                                of.actions().buildOutput()
                                        .setPort(OFPort.of(command.getEndpoint().getPortNumber()))
                                        .build()))))
                .build();
        verifyOfMessageEquals(expected, getWriteRecord(0).getRequest());
    }

    @Test
    public void happyPathTransitVlanSingleVlanToZeroVlan() throws Exception {
        FlowEndpoint ingressEndpoint = new FlowEndpoint(mapSwitchId(ingresDpId), 4, 2, 0);
        EgressFlowSegmentInstallCommand command = makeCommand(
                endpointZeroPort, ingressEndpoint, encapsulationVlan);
        executeCommand(command, 1);

        OFFlowAdd expected = of.buildFlowAdd()
                .setPriority(EgressFlowSegmentInstallCommand.FLOW_PRIORITY)
                .setCookie(U64.of(command.getCookie().getValue()))
                .setMatch(OfAdapter.INSTANCE.matchVlanId(of, of.buildMatch(), command.getEncapsulation().getId())
                                  .setExact(MatchField.IN_PORT, OFPort.of(command.getIngressIslPort()))
                                  .build())
                .setInstructions(ImmutableList.of(
                        of.instructions().applyActions(ImmutableList.of(
                                of.actions().popVlan(),
                                of.actions().buildOutput()
                                        .setPort(OFPort.of(command.getEndpoint().getPortNumber()))
                                        .build()))))
                .build();
        verifyOfMessageEquals(expected, getWriteRecord(0).getRequest());
    }

    @Test
    public void happyPathTransitVlanSingleVlanToSingleVlan() throws Exception {
        FlowEndpoint ingressEndpoint = new FlowEndpoint(mapSwitchId(ingresDpId), 1, 3, 0);
        EgressFlowSegmentInstallCommand command = makeCommand(endpointSingleVlan, ingressEndpoint, encapsulationVlan);
        executeCommand(command, 1);

        OFFlowAdd expected = of.buildFlowAdd()
                .setPriority(EgressFlowSegmentInstallCommand.FLOW_PRIORITY)
                .setCookie(U64.of(command.getCookie().getValue()))
                .setMatch(OfAdapter.INSTANCE.matchVlanId(of, of.buildMatch(), command.getEncapsulation().getId())
                         .setExact(MatchField.IN_PORT, OFPort.of(command.getIngressIslPort()))
                         .build())
                .setInstructions(ImmutableList.of(
                        of.instructions().applyActions(ImmutableList.of(
                                OfAdapter.INSTANCE.setVlanIdAction(of, command.getEndpoint().getOuterVlanId()),
                                of.actions().buildOutput()
                                        .setPort(OFPort.of(command.getEndpoint().getPortNumber()))
                                        .build()))))
                .build();
        verifyOfMessageEquals(expected, getWriteRecord(0).getRequest());
    }

    @Test
    public void happyPathTransitVlanSingleVlanToDoubleVlan() throws Exception {
        FlowEndpoint ingressEndpoint = new FlowEndpoint(mapSwitchId(ingresDpId), 4, 2, 0);
        EgressFlowSegmentInstallCommand command = makeCommand(
                endpointDoubleVlan, ingressEndpoint, encapsulationVlan);
        executeCommand(command, 1);

        OFFlowAdd expected = of.buildFlowAdd()
                .setPriority(EgressFlowSegmentInstallCommand.FLOW_PRIORITY)
                .setCookie(U64.of(command.getCookie().getValue()))
                .setMatch(OfAdapter.INSTANCE.matchVlanId(of, of.buildMatch(), command.getEncapsulation().getId())
                                  .setExact(MatchField.IN_PORT, OFPort.of(command.getIngressIslPort()))
                                  .build())
                .setInstructions(ImmutableList.of(
                        of.instructions().applyActions(ImmutableList.of(
                                OfAdapter.INSTANCE.setVlanIdAction(of, command.getEndpoint().getInnerVlanId()),
                                of.actions().pushVlan(EthType.VLAN_FRAME),
                                OfAdapter.INSTANCE.setVlanIdAction(of, command.getEndpoint().getOuterVlanId()),
                                of.actions().buildOutput()
                                        .setPort(OFPort.of(command.getEndpoint().getPortNumber()))
                                        .build()))))
                .build();
        verifyOfMessageEquals(expected, getWriteRecord(0).getRequest());
    }

    @Test
    public void happyPathTransitVlanDoubleVlanToZeroVlan() throws Exception {
        FlowEndpoint ingressEndpoint = new FlowEndpoint(mapSwitchId(ingresDpId), 5, 2, 4);
        EgressFlowSegmentInstallCommand command = makeCommand(
                endpointZeroPort, ingressEndpoint, encapsulationVlan);
        executeCommand(command, 1);

        OFFlowAdd expected = of.buildFlowAdd()
                .setPriority(EgressFlowSegmentInstallCommand.FLOW_PRIORITY)
                .setCookie(U64.of(command.getCookie().getValue()))
                .setMatch(OfAdapter.INSTANCE.matchVlanId(of, of.buildMatch(), command.getEncapsulation().getId())
                                  .setExact(MatchField.IN_PORT, OFPort.of(command.getIngressIslPort()))
                                  .build())
                .setInstructions(ImmutableList.of(
                        of.instructions().applyActions(ImmutableList.of(
                                of.actions().popVlan(),
                                of.actions().popVlan(),
                                of.actions().buildOutput()
                                        .setPort(OFPort.of(command.getEndpoint().getPortNumber()))
                                        .build()))))
                .build();
        verifyOfMessageEquals(expected, getWriteRecord(0).getRequest());
    }

    @Test
    public void happyPathTransitVlanDoubleVlanToSingleVlan() throws Exception {
        FlowEndpoint ingressEndpoint = new FlowEndpoint(mapSwitchId(ingresDpId), 6, 2, 4);
        EgressFlowSegmentInstallCommand command = makeCommand(
                endpointSingleVlan, ingressEndpoint, encapsulationVlan);
        executeCommand(command, 1);

        OFFlowAdd expected = of.buildFlowAdd()
                .setPriority(EgressFlowSegmentInstallCommand.FLOW_PRIORITY)
                .setCookie(U64.of(command.getCookie().getValue()))
                .setMatch(OfAdapter.INSTANCE.matchVlanId(of, of.buildMatch(), command.getEncapsulation().getId())
                                  .setExact(MatchField.IN_PORT, OFPort.of(command.getIngressIslPort()))
                                  .build())
                .setInstructions(ImmutableList.of(
                        of.instructions().applyActions(ImmutableList.of(
                                of.actions().popVlan(),
                                OfAdapter.INSTANCE.setVlanIdAction(of, command.getEndpoint().getOuterVlanId()),
                                of.actions().buildOutput()
                                        .setPort(OFPort.of(command.getEndpoint().getPortNumber()))
                                        .build()))))
                .build();
        verifyOfMessageEquals(expected, getWriteRecord(0).getRequest());
    }

    @Test
    public void happyPathTransitVlanDoubleVlanToDoubleVlan() throws Exception {
        FlowEndpoint ingressEndpoint = new FlowEndpoint(mapSwitchId(ingresDpId), 1, 4, 5);
        EgressFlowSegmentInstallCommand command = makeCommand(
                endpointDoubleVlan, ingressEndpoint, encapsulationVlan);
        executeCommand(command, 1);

        OFFlowAdd expected = of.buildFlowAdd()
                .setPriority(EgressFlowSegmentInstallCommand.FLOW_PRIORITY)
                .setCookie(U64.of(command.getCookie().getValue()))
                .setMatch(OfAdapter.INSTANCE.matchVlanId(of, of.buildMatch(), command.getEncapsulation().getId())
                                  .setExact(MatchField.IN_PORT, OFPort.of(command.getIngressIslPort()))
                                  .build())
                .setInstructions(ImmutableList.of(
                        of.instructions().applyActions(ImmutableList.of(
                                of.actions().popVlan(),
                                OfAdapter.INSTANCE.setVlanIdAction(of, command.getEndpoint().getInnerVlanId()),
                                of.actions().pushVlan(EthType.VLAN_FRAME),
                                OfAdapter.INSTANCE.setVlanIdAction(of, command.getEndpoint().getOuterVlanId()),
                                of.actions().buildOutput()
                                        .setPort(OFPort.of(command.getEndpoint().getPortNumber()))
                                        .build()))))
                .build();
        verifyOfMessageEquals(expected, getWriteRecord(0).getRequest());
    }

    @Override
    protected EgressFlowSegmentInstallCommand makeCommand(
            FlowEndpoint endpoint, FlowEndpoint ingressEndpoint, FlowTransitEncapsulation encapsulation) {
        MessageContext messageContext = new MessageContext();
        UUID commandId = UUID.randomUUID();
        String flowId = "egress-flow-segment-install-flow-id";
        Cookie cookie = new Cookie(3);
        int islPort = 6;
        return new EgressFlowSegmentInstallCommand(
                messageContext, commandId, flowId, cookie, endpoint, ingressEndpoint, islPort, encapsulation);
    }
}
