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

package org.openkilda.floodlight.command.flow.transit;

import org.openkilda.floodlight.api.FlowTransitEncapsulation;
import org.openkilda.floodlight.utils.OfAdapter;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.Cookie;

import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;

import java.util.UUID;

public class TransitFlowSegmentInstallCommandTest extends TransitFlowSegmentBlankCommandTest {
    @Test
    public void happyPathVlanEncapsulation() throws Exception {
        replayAll();

        TransitFlowSegmentInstallCommand command = makeCommand(encapsulationVlan);
        verifySuccessCompletion(command.execute(commandProcessor));

        verifyWriteCount(1);
        OFFlowAdd expected = of.buildFlowAdd()
                .setPriority(TransitFlowSegmentInstallCommand.FLOW_PRIORITY)
                .setCookie(U64.of(command.getCookie().getValue()))
                .setMatch(OfAdapter.INSTANCE.matchVlanId(of, of.buildMatch(), command.getEncapsulation().getId())
                                  .setExact(MatchField.IN_PORT, OFPort.of(command.getIngressIslPort()))
                                  .build())
                .setInstructions(ImmutableList.of(
                        of.instructions().applyActions(ImmutableList.of(
                                of.actions().buildOutput().setPort(OFPort.of(command.getEgressIslPort())).build()))))
                .build();
        verifyOfMessageEquals(expected, getWriteRecord(0).getRequest());
    }

    @Override
    protected TransitFlowSegmentInstallCommand makeCommand(FlowTransitEncapsulation encapsulation) {
        MessageContext messageContext = new MessageContext();
        UUID commandId = UUID.randomUUID();
        String flowId = "transit-segment-install-flow-id";
        Cookie cookie = new Cookie(5);
        int ingressIslPort = 2;
        int egressIslPort = 4;
        return new TransitFlowSegmentInstallCommand(
                messageContext, mapSwitchId(dpId), commandId, flowId, cookie,
                ingressIslPort, encapsulation, egressIslPort);
    }
}
