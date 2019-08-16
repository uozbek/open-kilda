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
import org.openkilda.floodlight.error.NotImplementedEncapsulationException;
import org.openkilda.floodlight.utils.OfAdapter;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.Cookie;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import lombok.Getter;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.types.OFPort;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
public class EgressFlowSegmentInstallCommand extends EgressFlowSegmentBlankCommand {
    @JsonCreator
    public EgressFlowSegmentInstallCommand(
            @JsonProperty("message_context") MessageContext context,
            @JsonProperty("command_id") UUID commandId,
            @JsonProperty("flowid") String flowId,
            @JsonProperty("cookie") Cookie cookie,
            @JsonProperty("endpoint") FlowEndpoint endpoint,
            @JsonProperty("ingress_endpoint") FlowEndpoint ingressEndpoint,
            @JsonProperty("islPort") Integer islPort,
            @JsonProperty("encapsulation") FlowTransitEncapsulation encapsulation) {
        super(context, commandId, flowId, cookie, endpoint, ingressEndpoint, islPort, encapsulation);
    }

    @Override
    protected List<OFInstruction> makeEgressModMessageInstructions(OFFactory of) {
        List<OFAction> applyActions = new ArrayList<>(makeTransformActions(of));
        applyActions.add(of.actions().buildOutput()
                                 .setPort(OFPort.of(endpoint.getPortNumber()))
                                 .build());

        return ImmutableList.of(of.instructions().applyActions(applyActions));
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

    @Override
    protected OFFlowMod.Builder makeFlowModBuilder(OFFactory of) {
        return makeFlowAddBuilder(of);
    }
}
