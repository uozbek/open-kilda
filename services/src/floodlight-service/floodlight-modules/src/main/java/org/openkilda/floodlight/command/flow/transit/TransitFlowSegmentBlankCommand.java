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
import org.openkilda.floodlight.command.flow.AbstractFlowSegmentCommand;
import org.openkilda.floodlight.command.flow.FlowSegmentReport;
import org.openkilda.floodlight.service.session.Session;
import org.openkilda.floodlight.utils.OfAdapter;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.Cookie;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.Match.Builder;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.OFPort;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

abstract class TransitFlowSegmentBlankCommand extends AbstractFlowSegmentCommand {
    protected final Integer ingressIslPort;
    protected final Integer egressIslPort;
    protected final FlowTransitEncapsulation encapsulation;

    TransitFlowSegmentBlankCommand(
            MessageContext messageContext, SwitchId switchId, UUID commandId, String flowId, Cookie cookie,
            Integer ingressIslPort, Integer egressIslPort, FlowTransitEncapsulation encapsulation) {
        super(messageContext, switchId, commandId, flowId, cookie);
        this.ingressIslPort = ingressIslPort;
        this.egressIslPort = egressIslPort;
        this.encapsulation = encapsulation;
    }

    @Override
    protected CompletableFuture<FlowSegmentReport> makeExecutePlan() {
        try (Session session = getSessionService().open(messageContext, getSw())) {
            return session.write(makeTransitRuleMessage())
                    .thenApply(ignore -> makeSuccessReport());
        }
    }

    protected OFFlowMod makeTransitRuleMessage() {
        OFFactory of = getSw().getOFFactory();
        List<OFAction> applyActions = ImmutableList.of(
                of.actions().buildOutput()
                        .setPort(OFPort.of(egressIslPort))
                        .build());

        return makeFlowModBuilder(of)
                .setInstructions(ImmutableList.of(of.instructions().applyActions(applyActions)))
                .setMatch(makeTransitMatch(of))
                .build();
    }

    private Match makeTransitMatch(OFFactory of) {
        switch (encapsulation.getType()) {
            case TRANSIT_VLAN:
                return makeTransitVlanMatch(of);
            default:
                throw new UnsupportedOperationException(String.format(
                        "%s do not support transit encapsulation type \"%s\" (dpId: %s, flowId: %s)",
                        getClass().getName(), encapsulation.getType(), switchId, flowId));
        }
    }

    private Match makeTransitVlanMatch(OFFactory of) {
        return OfAdapter.INSTANCE.matchVlanId(of, of.buildMatch(), encapsulation.getId())
                .setExact(MatchField.IN_PORT, OFPort.of(ingressIslPort))
                .build();
    }
}
