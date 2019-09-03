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

package org.openkilda.floodlight.command.flow;

import org.openkilda.floodlight.error.NotImplementedEncapsulationException;
import org.openkilda.floodlight.utils.OfAdapter;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.Cookie;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.SwitchId;

import lombok.Getter;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.OFPort;

import java.util.UUID;

@Getter
public abstract class NotIngressFlowSegmentCommand extends FlowSegmentCommand {
    protected final Integer ingressIslPort;
    protected final FlowTransitEncapsulation encapsulation;

    public NotIngressFlowSegmentCommand(
            MessageContext messageContext, SwitchId switchId, UUID commandId, String flowId, Cookie cookie,
            Integer ingressIslPort, FlowTransitEncapsulation encapsulation) {
        super(messageContext, switchId, commandId, flowId, cookie);
        this.ingressIslPort = ingressIslPort;
        this.encapsulation = encapsulation;
    }

    protected Match makeTransitMatch(OFFactory of) {
        Match.Builder match = of.buildMatch()
                .setExact(MatchField.IN_PORT, OFPort.of(ingressIslPort));
        switch (encapsulation.getType()) {
            case TRANSIT_VLAN:
                makeTransitVlanMatch(of, match);
                break;
            default:
                throw new NotImplementedEncapsulationException(getClass(), encapsulation.getType(), switchId, flowId);
        }
        return match.build();
    }

    private void makeTransitVlanMatch(OFFactory of, Match.Builder match) {
        OfAdapter.INSTANCE.matchVlanId(of, match, encapsulation.getId());
    }
}
