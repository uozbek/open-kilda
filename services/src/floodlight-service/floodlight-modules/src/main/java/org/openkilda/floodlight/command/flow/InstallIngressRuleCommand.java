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

import static org.projectfloodlight.openflow.protocol.OFVersion.OF_15;

import org.openkilda.floodlight.command.meter.InstallMeterCommand;
import org.openkilda.floodlight.command.meter.MeterReport;
import org.openkilda.floodlight.error.SwitchNotFoundException;
import org.openkilda.floodlight.error.UnsupportedSwitchOperationException;
import org.openkilda.floodlight.service.session.Session;
import org.openkilda.floodlight.switchmanager.SwitchManager;
import org.openkilda.messaging.MessageContext;
import org.openkilda.messaging.model.SpeakerSwitchView.Feature;
import org.openkilda.model.Cookie;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.MeterId;
import org.openkilda.model.OutputVlanType;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableSet;
import lombok.Getter;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFFlowModFlags;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.OFPort;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Getter
public class InstallIngressRuleCommand extends FlowInstallCommand {
    private final Long bandwidth;
    private final Integer inputVlanId;
    private final OutputVlanType outputVlanType;
    private final MeterId meterId;

    @JsonCreator
    public InstallIngressRuleCommand(@JsonProperty("command_id") UUID commandId,
                                     @JsonProperty("flowid") String flowId,
                                     @JsonProperty("message_context") MessageContext messageContext,
                                     @JsonProperty("cookie") Cookie cookie,
                                     @JsonProperty("switch_id") SwitchId switchId,
                                     @JsonProperty("input_port") Integer inputPort,
                                     @JsonProperty("output_port") Integer outputPort,
                                     @JsonProperty("bandwidth") Long bandwidth,
                                     @JsonProperty("input_vlan_id") Integer inputVlanId,
                                     @JsonProperty("output_vlan_type") OutputVlanType outputVlanType,
                                     @JsonProperty("meter_id") MeterId meterId,
                                     @JsonProperty("transit_encapsulation_id") Integer transitEncapsulationId,
                                     @JsonProperty("transit_encapsulation_type")
                                             FlowEncapsulationType transitEncapsulationType) {
        super(commandId, flowId, messageContext, cookie, switchId, inputPort, outputPort,
                transitEncapsulationId, transitEncapsulationType);
        this.bandwidth = bandwidth;
        this.inputVlanId = inputVlanId;
        this.outputVlanType = outputVlanType;
        this.meterId = meterId;
    }

    @Override
    protected CompletableFuture<FlowReport> makeExecutePlan() {
        CompletableFuture<FlowReport> future;
        if (meterId != null) {
            future = planMeterInstall()
                .thenCompose(this::planToUseMeter);
        } else {
            future = planForwardRuleInstall(null);
        }
        return future;
    }

    private CompletableFuture<MeterReport> planMeterInstall() {
        InstallMeterCommand meterCommand = new InstallMeterCommand(messageContext, switchId, meterId, bandwidth);
        return meterCommand.execute(getModuleContext());
    }

    private CompletableFuture<FlowReport> planToUseMeter(MeterReport report) {
        MeterId effectiveMeterId;
        try {
            report.raiseError();
            effectiveMeterId = report.getMeterId();
        } catch (UnsupportedSwitchOperationException e) {
            // switch do not support meters, setup rules without meter
            effectiveMeterId = null;
        } catch (Exception e) {
            throw maskCallbackException(e);
        }

        return planForwardRuleInstall(effectiveMeterId);
    }

    protected CompletableFuture<FlowReport> planForwardRuleInstall(MeterId effectiveMeterId) {
        try (Session session = getSessionService().open(messageContext, getSw())) {
            return session.write(makeForwardRuleAddMessage(effectiveMeterId))
                    .thenApply(response -> makeSuccessReport());
        }
    }

    private OFFlowMod makeForwardRuleAddMessage(MeterId effectiveMeterId) {
        final OFFactory of = getSw().getOFFactory();

        List<OFAction> applyActions = new ArrayList<>();
        List<OFInstruction> instructions = new ArrayList<>();

        if (effectiveMeterId != null) {
            makeMeterApplyCall(of, effectiveMeterId, applyActions, instructions);
        }
        applyActions.addAll(makePacketTransformActions(of));
        applyActions.add(makeOutputAction(OFPort.of(outputPort)));

        instructions.add(of.instructions().applyActions(applyActions));

        // build FLOW_MOD command with meter
        OFFlowAdd.Builder builder = makeFlowAddMessageBuilder(of)
                .setMatch(matchFlow(inputPort, inputVlanId, of))
                .setInstructions(instructions)
                .setPriority(inputVlanId == 0 ? SwitchManager.DEFAULT_FLOW_PRIORITY : FLOW_PRIORITY);
        if (getSwitchFeatures().contains(Feature.RESET_COUNTS_FLAG)) {
            builder.setFlags(ImmutableSet.of(OFFlowModFlags.RESET_COUNTS));
        }

        return builder.build();
    }

    List<OFAction> makePacketTransformActions(OFFactory ofFactory) {
        return inputVlanTypeToOfActionList(ofFactory);
    }

    protected OFAction makeOutputAction(OFPort port) {
        return getSw().getOFFactory().actions().buildOutput()
                .setPort(port)
                .build();
    }

    private List<OFAction> inputVlanTypeToOfActionList(OFFactory of) {
        List<OFAction> actionList = new ArrayList<>(3);
        if (outputVlanType == OutputVlanType.PUSH || outputVlanType == OutputVlanType.NONE) {
            of.actions().pushVlan(EthType.VLAN_FRAME);
        }
        actionList.add(makeSetVlanIdAction(of, transitEncapsulationId));
        return actionList;
    }

    private static void makeMeterApplyCall(OFFactory of, MeterId effectiveMeterId,
                                           List<OFAction> actionList, List<OFInstruction> instructions) {
        if (of.getVersion().compareTo(OF_15) == 0) {
            actionList.add(of.actions().buildMeter().setMeterId(effectiveMeterId.getValue()).build());
        } else /* OF_13, OF_14 */ {
            instructions.add(of.instructions().buildMeter()
                    .setMeterId(effectiveMeterId.getValue())
                    .build());
        }
    }
}
