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

import static java.lang.String.format;

import org.openkilda.floodlight.FloodlightResponse;
import org.openkilda.floodlight.flow.response.FlowRuleResponse;
import org.openkilda.floodlight.utils.CompletableFutureAdapter;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.Cookie;
import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonProperty;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowStatsEntry;
import org.projectfloodlight.openflow.protocol.OFFlowStatsReply;
import org.projectfloodlight.openflow.protocol.OFFlowStatsRequest;
import org.projectfloodlight.openflow.protocol.OFInstructionType;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFStatsReply;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.action.OFActionSetField;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionApplyActions;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionMeter;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmVlanVid;
import org.projectfloodlight.openflow.types.OFGroup;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.U64;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class ReadRuleCommand extends FlowCommand<ReadRuleReport> {

    public ReadRuleCommand(@JsonProperty("command_id") UUID commandId,
                           @JsonProperty("flowid") String flowId,
                           @JsonProperty("message_context") MessageContext messageContext,
                           @JsonProperty("cookie") Cookie cookie,
                           @JsonProperty("switch_id") SwitchId switchId) {
        super(commandId, flowId, messageContext, cookie, switchId);
    }

    @Override
    protected CompletableFuture<ReadRuleReport> makeExecutePlan() {
        log.debug("Getting rule with cookie {} from the switch {}", cookie, switchId);
        return new CompletableFutureAdapter<>(messageContext, getSw().writeStatsRequest(makeDumpRulesMessage()))
                .thenApply(this::handleStatsResponse);
    }

    @Override
    protected ReadRuleReport makeReport(Exception error) {
        return new ReadRuleReport(error);
    }

    private ReadRuleReport handleStatsResponse(List<OFFlowStatsReply> statsReplies) {
        OFFlowStatsEntry entry = extractFirstReply(statsReplies);
        return new ReadRuleReport(this, entry);
    }

    private OFFlowStatsEntry extractFirstReply(List<OFFlowStatsReply> statsReplies) {
        OFFlowStatsEntry result = null;
        int count = 0;
        for (OFFlowStatsReply reply : statsReplies) {
            for (OFFlowStatsEntry entry : reply.getEntries()) {
                count++;
                if (result == null) {
                    result = entry;
                }
            }
        }

        if (1 < count) {
            throw new IllegalStateException(
                    format("Found more than one rule with cookie %s on the switch %s. Total rules %s",
                           cookie, switchId, count));
        }
        if (result == null) {
            throw new IllegalStateException(format("Failed to find rule with cookie %s on the switch %s",
                                                   cookie, switchId));
        }

        return result;
    }

    private OFFlowStatsRequest makeDumpRulesMessage() {
        OFFactory of = getSw().getOFFactory();
        return of.buildFlowStatsRequest()
                .setCookie(U64.of(cookie.getValue()))
                .setCookieMask(U64.NO_MASK)
                .setOutGroup(OFGroup.ANY)
                .build();
    }

    private FloodlightResponse toFlowRuleResponse(OFMessage message) {
        Map<OFInstructionType, OFInstruction> instructionMap = entry.getInstructions()
                .stream()
                .collect(Collectors.toMap(OFInstruction::getType, instruction -> instruction));
        OFInstructionApplyActions applyActions =
                (OFInstructionApplyActions) instructionMap.get(OFInstructionType.APPLY_ACTIONS);

        return FlowRuleResponse.flowRuleResponseBuilder()
                .commandId(commandId)
                .messageContext(messageContext)
                .switchId(switchId)
                .cookie(new Cookie(entry.getCookie().getValue()))
                .meterId(getMeter(instructionMap))
                .inPort(getInputPort(entry.getMatch()))
                .outPort(getOutputPort(applyActions))
                .inVlan(getInVlan(entry.getMatch()))
                .outVlan(getOutVlan(applyActions))
                .ofVersion(message.getVersion().toString())
                .flowId(flowId)
                .build();
    }

    private Integer getInputPort(Match match) {
        return Optional.ofNullable(match.get(MatchField.IN_PORT))
                .map(OFPort::getPortNumber)
                .orElse(null);
    }

    private int getInVlan(Match match) {
        return Optional.ofNullable(match.get(MatchField.VLAN_VID))
                .map(OFVlanVidMatch::getVlan)
                .map(Integer::valueOf)
                .orElse(0);
    }

    private MeterId getMeter(Map<OFInstructionType, OFInstruction> instructionMap) {
        return Optional.ofNullable(instructionMap.get(OFInstructionType.METER))
                .map(OFInstructionMeter.class::cast)
                .map(OFInstructionMeter::getMeterId)
                .map(MeterId::new)
                .orElse(null);
    }

    private Integer getOutputPort(OFInstructionApplyActions applyActions) {
        return applyActions.getActions().stream()
                .filter(OFActionOutput.class::isInstance)
                .map(OFActionOutput.class::cast)
                .map(OFActionOutput::getPort)
                .map(OFPort::getPortNumber)
                .findFirst()
                .orElse(null);
    }

    private int getOutVlan(OFInstructionApplyActions applyActions) {
        return applyActions.getActions().stream()
                .filter(OFActionSetField.class::isInstance)
                .map(OFActionSetField.class::cast)
                .map(OFActionSetField::getField)
                .filter(OFOxmVlanVid.class::isInstance)
                .map(OFOxmVlanVid.class::cast)
                .map(OFOxmVlanVid::getValue)
                .map(OFVlanVidMatch::getVlan)
                .map(Integer::valueOf)
                .findAny()
                .orElse(0);
    }
}
