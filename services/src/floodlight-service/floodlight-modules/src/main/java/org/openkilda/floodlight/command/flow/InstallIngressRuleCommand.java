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
import org.openkilda.floodlight.error.UnsupportedSwitchOperationException;
import org.openkilda.floodlight.api.FlowEndpoint;
import org.openkilda.floodlight.model.SwitchDescriptor;
import org.openkilda.floodlight.service.session.Session;
import org.openkilda.floodlight.utils.MetadataAdapter;
import org.openkilda.floodlight.utils.MetadataAdapter.MetadataMatch;
import org.openkilda.messaging.MessageContext;
import org.openkilda.messaging.model.SpeakerSwitchView.Feature;
import org.openkilda.model.Cookie;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import lombok.Getter;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFFlowModFlags;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.OFMetadata;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Getter
public class InstallIngressRuleCommand extends FlowInstallCommand {
    protected final Long bandwidth;
    protected final Integer inputOuterVlanId;
    protected final Integer inputInnerVlanId;
    protected final MeterId meterId;

    @JsonCreator
    public InstallIngressRuleCommand(@JsonProperty("command_id") UUID commandId,
                                     @JsonProperty("flowid") String flowId,
                                     @JsonProperty("message_context") MessageContext messageContext,
                                     @JsonProperty("cookie") Cookie cookie,
                                     @JsonProperty("switch_id") SwitchId switchId,
                                     @JsonProperty("input_port") Integer inputPort,
                                     @JsonProperty("output_port") Integer outputPort,
                                     @JsonProperty("bandwidth") Long bandwidth,
                                     @JsonProperty("input_vlan_id") Integer inputOuterVlanId,
                                     @JsonProperty("input_inner_vlan_id") Integer inputInnerVlanId,
                                     @JsonProperty("meter_id") MeterId meterId,
                                     @JsonProperty("transit_encapsulation_id") Integer transitEncapsulationId,
                                     @JsonProperty("transit_encapsulation_type")
                                             FlowEncapsulationType transitEncapsulationType) {
        super(commandId, flowId, messageContext, cookie, switchId, inputPort, outputPort,
              transitEncapsulationId, transitEncapsulationType);
        this.bandwidth = bandwidth;
        this.inputOuterVlanId = inputOuterVlanId;
        this.inputInnerVlanId = inputInnerVlanId;
        this.meterId = meterId;
    }

    @Override
    protected CompletableFuture<FlowReport> makeExecutePlan() {
        CompletableFuture<FlowReport> future;
        if (meterId != null) {
            future = planMeterInstall()
                    .thenCompose(this::planToUseMeter);
        } else {
            future = planForwardingRulesInstall(null);
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

        return planForwardingRulesInstall(effectiveMeterId);
    }

    private CompletableFuture<FlowReport> planForwardingRulesInstall(MeterId effectiveMeterId) {
        List<OFFlowMod> ofMessages = new ArrayList<>(2);
        OFFactory of = getSw().getOFFactory();
        if (FlowEndpoint.isVlanIdSet(inputOuterVlanId)) {
            ofMessages.add(makeOuterVlanMatchMessage(of));
            if (FlowEndpoint.isVlanIdSet(inputInnerVlanId)) {
                ofMessages.add(makeInnerVlanMatchAndForwardMessage(of, effectiveMeterId));
            } else {
                ofMessages.add(makeOuterVlanOnlyForwardMessage(of, effectiveMeterId));
            }
        } else {
            ofMessages.add(makeDefaultPortFlowMatchAndForwardMessage(of, effectiveMeterId));
        }

        List<CompletableFuture<Optional<OFMessage>>> writeResults = new ArrayList<>(ofMessages.size());
        try (Session session = getSessionService().open(messageContext, getSw())) {
            for (OFFlowMod message : ofMessages) {
                writeResults.add(session.write(message));
            }
        }
        return CompletableFuture.allOf(writeResults.toArray(new CompletableFuture[0]))
                .thenApply(ignore -> makeSuccessReport());
    }

    private OFFlowMod makeOuterVlanMatchMessage(OFFactory of) {
        SwitchDescriptor swDesc = getSwitchDescriptor();
        MetadataMatch metadata = MetadataAdapter.INSTANCE.addressOuterVlan(
                OFVlanVidMatch.ofVlan(inputOuterVlanId));
        return makeOfFlowAddMessageBuilder(of)
                .setTableId(swDesc.getTableDispatch())
                .setMatch(of.buildMatch()
                                  .setExact(MatchField.IN_PORT, OFPort.of(inputPort))
                                  .setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(inputOuterVlanId))
                                  .build())
                .setInstructions(ImmutableList.of(
                        of.instructions().applyActions(ImmutableList.of(of.actions().popVlan())),
                        of.instructions().writeMetadata(metadata.getValue(), metadata.getMask()),
                        of.instructions().gotoTable(swDesc.getTableIngress())))
                .build();
    }

    private OFFlowMod makeInnerVlanMatchAndForwardMessage(OFFactory of, MeterId effectiveMeterId) {
        MetadataMatch metadata = MetadataAdapter.INSTANCE.addressOuterVlan(OFVlanVidMatch.ofVlan(inputOuterVlanId));
        OFFlowMod.Builder builder = makeOfFlowAddMessageBuilder(of)
                .setTableId(getSwitchDescriptor().getTableIngress())
                .setMatch(of.buildMatch()
                                  .setExact(MatchField.IN_PORT, OFPort.of(inputPort))
                                  .setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(inputInnerVlanId))
                                  .setMasked(MatchField.METADATA,
                                             OFMetadata.of(metadata.getValue()), OFMetadata.of(metadata.getMask()))
                                  .build());
        return makeForwardMessage(of, builder, effectiveMeterId);
    }

    private OFFlowMod makeOuterVlanOnlyForwardMessage(OFFactory of, MeterId effectiveMeterId) {
        MetadataMatch metadata = MetadataAdapter.INSTANCE.addressOuterVlan(OFVlanVidMatch.ofVlan(inputOuterVlanId));
        OFFlowMod.Builder builder = makeOfFlowAddMessageBuilder(of)
                .setTableId(getSwitchDescriptor().getTableIngress())
                .setPriority(FLOW_PRIORITY - 10)
                .setMatch(of.buildMatch()
                                  .setExact(MatchField.IN_PORT, OFPort.of(inputPort))
                                  .setMasked(MatchField.METADATA,
                                             OFMetadata.of(metadata.getValue()), OFMetadata.of(metadata.getMask()))
                                  .build());
        return makeForwardMessage(of, builder, effectiveMeterId);
    }

    private OFFlowMod makeDefaultPortFlowMatchAndForwardMessage(OFFactory of, MeterId effectiveMeterId) {
        OFFlowMod.Builder builder = makeOfFlowAddMessageBuilder(of)
                // FIXME we need some space between match rules (so it should be -10 instead of -1)
                .setPriority(FLOW_PRIORITY - 1)
                .setMatch(of.buildMatch()
                                  .setExact(MatchField.IN_PORT, OFPort.of(inputPort))
                                  .build());
        return makeForwardMessage(of, builder, effectiveMeterId);
    }

    private OFFlowMod makeForwardMessage(OFFactory of, OFFlowMod.Builder builder, MeterId effectiveMeterId) {
        List<OFAction> applyActions = new ArrayList<>();
        List<OFInstruction> instructions = new ArrayList<>();

        if (effectiveMeterId != null) {
            makeMeterApplyCall(of, effectiveMeterId, applyActions, instructions);
        }

        applyActions.addAll(makePacketTransformActions(of));
        applyActions.add(makeOutputAction(OFPort.of(outputPort)));

        instructions.add(of.instructions().applyActions(applyActions));

        builder.setInstructions(instructions);
        if (getSwitchFeatures().contains(Feature.RESET_COUNTS_FLAG)) {
            builder.setFlags(ImmutableSet.of(OFFlowModFlags.RESET_COUNTS));
        }
        return builder.build();
    }

    List<OFAction> makePacketTransformActions(OFFactory of) {
        List<OFAction> actions = new ArrayList<>();
        if (FlowEndpoint.isVlanIdSet(inputOuterVlanId)) {
            // restore outer vlan removed by 'pre-match' rule
            actions.add(of.actions().pushVlan(EthType.VLAN_FRAME));
            actions.add(makeSetVlanIdAction(of, inputOuterVlanId));
        }

        switch (transitEncapsulationType) {
            case TRANSIT_VLAN:
                actions.addAll(makePacketTransformForVlanEncapsulation(of));
                break;
            default:
                throw new UnsupportedOperationException(String.format(
                        "%s do not support transit encapsulation type \"%s\" (dpId: %s, flowId: %s)",
                        getClass().getName(), transitEncapsulationType, switchId, flowId));
        }
        return actions;
    }

    private List<OFAction> makePacketTransformForVlanEncapsulation(OFFactory of) {
        List<OFAction> actions = new ArrayList<>();
        if (! FlowEndpoint.isVlanIdSet(inputOuterVlanId)) {
            actions.add(of.actions().pushVlan(EthType.VLAN_FRAME));
        }
        actions.add(makeSetVlanIdAction(of, transitEncapsulationId));
        return actions;
    }

    protected OFAction makeOutputAction(OFPort port) {
        return getSw().getOFFactory().actions().buildOutput()
                .setPort(port)
                .build();
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
