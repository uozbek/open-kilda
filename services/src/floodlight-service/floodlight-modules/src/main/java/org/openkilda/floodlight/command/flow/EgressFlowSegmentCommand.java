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

import org.openkilda.floodlight.api.FlowEndpoint;
import org.openkilda.floodlight.api.FlowTransitEncapsulation;
import org.openkilda.floodlight.service.session.Session;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.Cookie;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.types.OFPort;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class EgressFlowSegmentCommand extends AbstractFlowSegmentCommand {
    // payload
    private final FlowEndpoint endpoint;
    private final Integer islPort;
    private final FlowTransitEncapsulation encapsulation;

    @JsonCreator
    public EgressFlowSegmentCommand(
            @JsonProperty("message_context") MessageContext messageContext,
            @JsonProperty("switch_id") SwitchId switchId,
            @JsonProperty("command_id") UUID commandId,
            @JsonProperty("flowid") String flowId,
            @JsonProperty("cookie") Cookie cookie,
            @JsonProperty("endpoint") FlowEndpoint endpoint,
            @JsonProperty("islPort") Integer islPort,
            @JsonProperty("encapsulation") FlowTransitEncapsulation encapsulation) {
        super(messageContext, switchId, commandId, flowId, cookie);
        this.endpoint = endpoint;
        this.islPort = islPort;
        this.encapsulation = encapsulation;
    }

    @Override
    protected CompletableFuture<FlowSegmentReport> makeExecutePlan() {
        try (Session session = getSessionService().open(messageContext, getSw())) {
            return session.write(makeEgressRuleAddMessage())
                    .thenApply(ignore -> new FlowSegmentReport(this));
        }
    }

    private OFFlowMod makeEgressRuleAddMessage() {
        OFFactory of = getSw().getOFFactory();

        List<OFAction> applyActions = new ArrayList<>(makePacketTransformActions(of));
        applyActions.add(of.actions().buildOutput()
                                 .setPort(OFPort.of(outputPort))
                                 .build());

        return makeOfFlowAddMessageBuilder(of)
                .setMatch(matchFlow(inputPort, transitEncapsulationId, of))
                .setInstructions(ImmutableList.of(of.instructions().applyActions(applyActions)))
                .build();
    }

    private List<OFAction> makePacketTransformActions(OFFactory of) {
        List<Integer> currentVlanStack = makeCurrentVlanStack();
        List<Integer> desiredVlanStack = FlowEndpoint.makeVlanStack(outputInnerVlanId, outputOuterVlanId);

        return makeVlanTransformActions(of, currentVlanStack, desiredVlanStack);
    }

    private List<Integer> makeCurrentVlanStack() {
        final LinkedList<Integer> vlanStack = new LinkedList<>(ingressEndpoint.getVlanStack());
        if (transitEncapsulationType == FlowEncapsulationType.TRANSIT_VLAN) {
            // vlan encapsulation rewrite outer vlan if it exists
            if (! vlanStack.isEmpty()) {
                vlanStack.removeLast();
            }
            vlanStack.addLast(transitEncapsulationId);
        }
        return vlanStack;
    }
}
