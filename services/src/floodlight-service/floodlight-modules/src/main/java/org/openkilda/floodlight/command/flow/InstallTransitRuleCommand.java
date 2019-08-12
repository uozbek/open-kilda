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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class InstallTransitRuleCommand extends FlowInstallCommand {
    @JsonCreator
    public InstallTransitRuleCommand(@JsonProperty("command_id") UUID commandId,
                                     @JsonProperty("flowid") String flowId,
                                     @JsonProperty("message_context") MessageContext messageContext,
                                     @JsonProperty("cookie") Cookie cookie,
                                     @JsonProperty("switch_id") SwitchId switchId,
                                     @JsonProperty("input_port") Integer inputPort,
                                     @JsonProperty("output_port") Integer outputPort,
                                     @JsonProperty("transit_encapsulation_id") Integer transitEncapsulationId,
                                     @JsonProperty("transit_encapsulation_type")
                                             FlowEncapsulationType transitEncapsulationType) {
        super(commandId, flowId, messageContext, cookie, switchId, inputPort, outputPort, transitEncapsulationId,
              transitEncapsulationType);
    }

    @Override
    protected CompletableFuture<FlowSegmentReport> makeExecutePlan() {
        try (Session session = getSessionService().open(messageContext, getSw())) {
            return session.write(makeTransitRuleAddMessage())
                    .thenApply(ignore -> new FlowSegmentReport(this));
        }
    }

    private OFFlowMod makeTransitRuleAddMessage() {
        OFFactory of = getSw().getOFFactory();
        List<OFAction> applyActions = ImmutableList.of(
                of.actions().buildOutput()
                        .setPort(OFPort.of(outputPort))
                        .build());

        return makeOfFlowAddMessageBuilder(of)
                .setInstructions(ImmutableList.of(of.instructions().applyActions(applyActions)))
                .setMatch(matchFlow(inputPort, transitEncapsulationId, of))
                .build();
    }
}
