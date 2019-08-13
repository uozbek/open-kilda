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
import org.openkilda.floodlight.command.flow.ingress.IngressFlowSegmentInstallCommand;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.Cookie;
import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.types.OFPort;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class InstallOneSwitchRuleCommand extends IngressFlowSegmentInstallCommand {
    private final Integer outputOuterVlanId;
    private final Integer outputInnerVlanId;

    @JsonCreator
    public InstallOneSwitchRuleCommand(@JsonProperty("command_id") UUID commandId,
                                       @JsonProperty("flowid") String flowid,
                                       @JsonProperty("message_context") MessageContext messageContext,
                                       @JsonProperty("cookie") Cookie cookie,
                                       @JsonProperty("switch_id") SwitchId switchId,
                                       @JsonProperty("input_port") Integer inputPort,
                                       @JsonProperty("output_port") Integer outputPort,
                                       @JsonProperty("bandwidth") Long bandwidth,
                                       @JsonProperty("input_vlan_id") Integer inputOuterVlanId,
                                       @JsonProperty("input_inner_vlan_id") Integer inputInnerVlanId,
                                       @JsonProperty("meter_id") MeterId meterId,
                                       @JsonProperty("output_vlan_id") Integer outputOuterVlanId,
                                       @JsonProperty("output_inner_vlan_id") Integer outputInnerVlanId) {
        super(commandId, flowid, messageContext, cookie, switchId, inputPort, outputPort, bandwidth,
                inputOuterVlanId, inputInnerVlanId, meterId, null, null);
        this.outputOuterVlanId = outputOuterVlanId;
        this.outputInnerVlanId = outputInnerVlanId;
    }

    @Override
    List<OFAction> makePacketTransformActions(OFFactory of) {
        List<Integer> currentVlanStack = new ArrayList<>(2);
        if (FlowEndpoint.isVlanIdSet(inputInnerVlanId)) {
            currentVlanStack.add(inputInnerVlanId);
        }
        // `inputOuterVlanId` was removed by pre-match rule

        List<Integer> desiredVlanStack = new ArrayList<>(2);
        if (FlowEndpoint.isVlanIdSet(outputInnerVlanId)) {
            desiredVlanStack.add(outputInnerVlanId);
        }
        if (FlowEndpoint.isVlanIdSet(outputOuterVlanId)) {
            desiredVlanStack.add(outputOuterVlanId);
        }

        return makeVlanTransformActions(of, currentVlanStack, desiredVlanStack);
    }

    @Override
    protected OFAction makeOutputAction(OFPort port) {
        if (inputPort.equals(port.getPortNumber())) {
            return super.makeOutputAction(OFPort.IN_PORT);
        }
        return super.makeOutputAction(port);
    }
}
