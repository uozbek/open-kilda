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

import org.openkilda.floodlight.api.FlowEndpoint;
import org.openkilda.floodlight.api.FlowTransitEncapsulation;
import org.openkilda.floodlight.utils.OfAdapter;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.Cookie;

import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFFlowDeleteStrict;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;

import java.util.UUID;

public class EgressFlowSegmentRemoveCommandTest extends EgressFlowSegmentBlankCommandTest {
    @Test
    public void happyPathTransitVlan() throws Exception {
        FlowEndpoint ingressEndpoint = new FlowEndpoint(mapSwitchId(ingresDpId), 1, 0, 0);
        EgressFlowSegmentRemoveCommand command = makeCommand(endpointZeroPort, ingressEndpoint, encapsulationVlan);
        executeCommand(command, 1);

        OFFlowDeleteStrict expected = of.buildFlowDeleteStrict()
                .setPriority(EgressFlowSegmentRemoveCommand.FLOW_PRIORITY)
                .setCookie(U64.of(command.getCookie().getValue()))
                .setMatch(OfAdapter.INSTANCE.matchVlanId(of, of.buildMatch(), command.getEncapsulation().getId())
                                  .setExact(MatchField.IN_PORT, OFPort.of(command.getIngressIslPort()))
                                  .build())
                .build();
        verifyOfMessageEquals(expected, getWriteRecord(0).getRequest());
    }

    @Override
    protected EgressFlowSegmentRemoveCommand makeCommand(
            FlowEndpoint endpoint, FlowEndpoint ingressEndpoint, FlowTransitEncapsulation encapsulation) {
        MessageContext messageContext = new MessageContext();
        UUID commandId = UUID.randomUUID();
        String flowId = "egress-flow-segment-remove-flow-id";
        Cookie cookie = new Cookie(101);
        int islPort = 8;
        return new EgressFlowSegmentRemoveCommand(
                messageContext, mapSwitchId(dpId), commandId, flowId, cookie,
                endpoint, ingressEndpoint, islPort, encapsulation);
    }
}
