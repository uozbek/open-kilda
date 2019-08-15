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
import org.openkilda.floodlight.command.SpeakerCommandProcessor;
import org.openkilda.floodlight.command.flow.AbstractNotIngressFlowSegmentCommand;
import org.openkilda.floodlight.command.flow.FlowSegmentReport;
import org.openkilda.floodlight.error.NotImplementedEncapsulationException;
import org.openkilda.floodlight.service.session.Session;
import org.openkilda.floodlight.utils.OfAdapter;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.Cookie;
import org.openkilda.model.SwitchId;

import com.google.common.collect.ImmutableList;
import lombok.Getter;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.types.OFPort;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Getter
abstract class EgressFlowSegmentBlankCommand extends AbstractNotIngressFlowSegmentCommand {
    protected final FlowEndpoint endpoint;
    protected final FlowEndpoint ingressEndpoint;

    EgressFlowSegmentBlankCommand(
            MessageContext messageContext, SwitchId switchId, UUID commandId, String flowId, Cookie cookie,
            FlowEndpoint endpoint, FlowEndpoint ingressEndpoint, Integer islPort,
            FlowTransitEncapsulation encapsulation) {
        super(messageContext, switchId, commandId, flowId, cookie, islPort, encapsulation);
        this.endpoint = endpoint;
        this.ingressEndpoint = ingressEndpoint;
    }

    @Override
    protected CompletableFuture<FlowSegmentReport> makeExecutePlan(
            SpeakerCommandProcessor commandProcessor) {
        try (Session session = getSessionService().open(messageContext, getSw())) {
            return session.write(makeEgressModMessage())
                    .thenApply(ignore -> makeSuccessReport());
        }
    }

    private OFFlowMod makeEgressModMessage() {
        OFFactory of = getSw().getOFFactory();

        List<OFAction> applyActions = new ArrayList<>(makeTransformActions(of));
        applyActions.add(of.actions().buildOutput()
                                 .setPort(OFPort.of(endpoint.getPortNumber()))
                                 .build());

        return makeFlowModBuilder(of)
                .setMatch(makeTransitMatch(of))
                .setInstructions(ImmutableList.of(of.instructions().applyActions(applyActions)))
                .build();
    }

    private List<OFAction> makeTransformActions(OFFactory of) {
        switch (encapsulation.getType()) {
            case TRANSIT_VLAN:
                return makeVlanTransformActions(of);
            default:
                throw new NotImplementedEncapsulationException(getClass(), encapsulation.getType(), switchId, flowId);
        }
    }

    private List<OFAction> makeVlanTransformActions(OFFactory of) {
        List<Integer> currentVlanStack = FlowEndpoint.makeVlanStack(
                ingressEndpoint.getInnerVlanId(), encapsulation.getId());
        List<Integer> desiredVlanStack = endpoint.getVlanStack();
        return OfAdapter.INSTANCE.makeVlanReplaceActions(of, currentVlanStack, desiredVlanStack);
    }
}
