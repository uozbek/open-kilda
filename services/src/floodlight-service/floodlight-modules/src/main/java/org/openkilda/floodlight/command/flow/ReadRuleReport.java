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

import org.openkilda.floodlight.flow.response.FlowRuleResponse;
import org.openkilda.messaging.AbstractMessage;
import org.openkilda.model.Cookie;
import org.openkilda.model.MeterId;

import lombok.Value;
import org.projectfloodlight.openflow.protocol.OFFlowStatsEntry;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionMeter;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.action.OFActionSetField;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionApplyActions;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionMeter;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.oxm.OFOxm;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmVlanVid;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;

import java.util.List;

// FIXME(surabujin) - drop it
@Value
public class ReadRuleReport extends FlowSegmentReport {
    private final OFFlowStatsEntry rule;

    public ReadRuleReport(ReadRuleCommand command, Exception error) {
        this(command, null, error);
    }

    public ReadRuleReport(ReadRuleCommand command, OFFlowStatsEntry rule) {
        this(command, rule, null);
    }

    private ReadRuleReport(ReadRuleCommand command, OFFlowStatsEntry rule, Exception error) {
        super(null, error);
        this.rule = rule;
    }

    @Override
    protected AbstractMessage assembleSuccessResponse() {
        AbstractFlowSegmentCommand command = getCommand();
        FlowRuleResponse.FlowRuleResponseBuilder builder = FlowRuleResponse.flowRuleResponseBuilder()
                .commandId(command.getCommandId())
                .messageContext(command.getMessageContext())
                .switchId(command.getSwitchId())
                .flowId(command.getFlowId())
                .cookie(new Cookie(rule.getCookie().getValue()))
                .ofVersion(rule.getVersion().toString());

        // TODO(surabujin): move into separate OF decode tool
        decodeMatch(builder, rule.getMatch());
        decodeInstructions(builder, rule.getInstructions());

        return builder.build();
    }

    private void decodeMatch(FlowRuleResponse.FlowRuleResponseBuilder builder, Match match) {
        // in port
        OFPort inPort = match.get(MatchField.IN_PORT);
        if (inPort != null) {
            builder.inPort(inPort.getPortNumber());
        }

        // vlan id
        OFVlanVidMatch vlanId = match.get(MatchField.VLAN_VID);
        if (vlanId != null) {
            builder.inVlan((int) vlanId.getVlan());
        }
    }

    private void decodeInstructions(FlowRuleResponse.FlowRuleResponseBuilder builder,
                                    List<OFInstruction> instructions) {
        for (OFInstruction entry : instructions) {
            if (entry instanceof OFInstructionApplyActions) {
                decodeInstructions(builder, (OFInstructionApplyActions) entry);
            } else if (entry instanceof OFInstructionMeter) {
                decodeInstructions(builder, (OFInstructionMeter) entry);
            }
        }
    }

    private void decodeInstructions(FlowRuleResponse.FlowRuleResponseBuilder builder,
                                    OFInstructionApplyActions applyActions) {
        decodeApplyActions(builder, applyActions.getActions());
    }

    private void decodeInstructions(FlowRuleResponse.FlowRuleResponseBuilder builder,
                                    OFInstructionMeter applyMeter) {
        builder.meterId(new MeterId(applyMeter.getMeterId()));
    }

    private void decodeApplyActions(FlowRuleResponse.FlowRuleResponseBuilder builder, List<OFAction> actions) {
        for (OFAction entry : actions) {
            if (entry instanceof OFActionSetField) {
                decodeApplyActions(builder, (OFActionSetField) entry);
            } else if (entry instanceof OFActionOutput) {
                decodeApplyActions(builder, (OFActionOutput) entry);
            } else if (entry instanceof OFActionMeter) {
                decodeApplyActions(builder, (OFActionMeter) entry);
            }
        }
    }

    private void decodeApplyActions(FlowRuleResponse.FlowRuleResponseBuilder builder, OFActionSetField setField) {
        OFOxm<?> field = setField.getField();
        if (field instanceof OFOxmVlanVid) {
            OFOxmVlanVid setVlanId = (OFOxmVlanVid) field;
            builder.outVlan((int) setVlanId.getValue().getVlan());
        }
    }

    private void decodeApplyActions(FlowRuleResponse.FlowRuleResponseBuilder builder, OFActionOutput output) {
        builder.outPort(output.getPort().getPortNumber());
    }

    private void decodeApplyActions(FlowRuleResponse.FlowRuleResponseBuilder builder, OFActionMeter meter) {
        builder.meterId(new MeterId(meter.getMeterId()));
    }
}
