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

package org.openkilda.floodlight.command.flow.ingress;

import org.openkilda.floodlight.api.FlowEndpoint;
import org.openkilda.floodlight.api.MeterConfig;
import org.openkilda.floodlight.command.SpeakerCommandProcessor;
import org.openkilda.floodlight.command.flow.AbstractFlowSegmentCommand;
import org.openkilda.floodlight.command.flow.FlowSegmentReport;
import org.openkilda.floodlight.command.meter.MeterInstallCommand;
import org.openkilda.floodlight.command.meter.MeterRemoveCommand;
import org.openkilda.floodlight.command.meter.MeterReport;
import org.openkilda.floodlight.error.UnsupportedSwitchOperationException;
import org.openkilda.floodlight.model.SwitchDescriptor;
import org.openkilda.floodlight.service.session.Session;
import org.openkilda.floodlight.utils.MetadataAdapter;
import org.openkilda.floodlight.utils.MetadataAdapter.MetadataMatch;
import org.openkilda.floodlight.utils.OfAdapter;
import org.openkilda.messaging.MessageContext;
import org.openkilda.messaging.model.SpeakerSwitchView.Feature;
import org.openkilda.model.Cookie;
import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchId;

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
import org.projectfloodlight.openflow.types.OFMetadata;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.U64;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Getter
abstract class AbstractIngressFlowSegmentCommand extends AbstractFlowSegmentCommand {
    // payload
    protected final FlowEndpoint endpoint;
    protected final MeterConfig meterConfig;

    AbstractIngressFlowSegmentCommand(
            MessageContext messageContext, SwitchId switchId, UUID commandId, String flowId, Cookie cookie,
            FlowEndpoint endpoint, MeterConfig meterConfig) {
        super(messageContext, switchId, commandId, flowId, cookie);
        this.endpoint = endpoint;
        this.meterConfig = meterConfig;
    }

    protected CompletableFuture<FlowSegmentReport> makeInstallPlan(SpeakerCommandProcessor commandProcessor) {
        CompletableFuture<FlowSegmentReport> future;
        if (meterConfig != null) {
            future = planMeterInstall(commandProcessor)
                    .thenCompose(this::planToUseMeter);
        } else {
            future = planForwardingRulesInstall(null);
        }
        return future;
    }

    protected CompletableFuture<FlowSegmentReport> makeRemovePlan(SpeakerCommandProcessor commandProcessor) {
        CompletableFuture<?> future = planForwardingRulesRemove();
        if (meterConfig != null) {
            future = future.thenCompose(ignore -> planMeterRemove(commandProcessor));
        }
        return future.thenApply(ignore -> makeSuccessReport());
    }

    private CompletableFuture<MeterReport> planMeterInstall(SpeakerCommandProcessor commandProcessor) {
        MeterInstallCommand meterCommand = new MeterInstallCommand(messageContext, switchId, meterConfig);
        return commandProcessor.chain(meterCommand);
    }

    private CompletableFuture<MeterReport> planMeterRemove(SpeakerCommandProcessor commandProcessor) {
        MeterRemoveCommand removeCommand = new MeterRemoveCommand(messageContext, switchId, meterConfig);
        return commandProcessor.chain(removeCommand)
                .thenApply(this::handleMeterRemoveReport);
    }

    private CompletableFuture<FlowSegmentReport> planToUseMeter(MeterReport report) {
        MeterId effectiveMeterId;
        try {
            report.raiseError();
            effectiveMeterId = report.getMeterId();
        } catch (UnsupportedSwitchOperationException e) {
            log.info("Do not install meter id {} on {} - {}", meterConfig.getId(), switchId, e.getMessage());
            // switch do not support meters, setup rules without meter
            effectiveMeterId = null;
        } catch (Exception e) {
            throw maskCallbackException(e);
        }

        return planForwardingRulesInstall(effectiveMeterId);
    }

    private CompletableFuture<FlowSegmentReport> planForwardingRulesInstall(MeterId effectiveMeterId) {
        List<OFFlowMod> ofMessages = makeIngressModMessages(effectiveMeterId);
        List<CompletableFuture<Optional<OFMessage>>> writeResults = new ArrayList<>(ofMessages.size());
        try (Session session = getSessionService().open(messageContext, getSw())) {
            for (OFFlowMod message : ofMessages) {
                writeResults.add(session.write(message));
            }
        }
        return CompletableFuture.allOf(writeResults.toArray(new CompletableFuture[0]))
                .thenApply(ignore -> makeSuccessReport());
    }

    private CompletableFuture<Void> planForwardingRulesRemove() {
        MeterId meterId = null;
        if (meterConfig != null) {
            meterId = meterConfig.getId();
        }
        List<OFFlowMod> ofMessages = new ArrayList<>(makeIngressModMessages(meterId));

        // TODO(surabujin): drop after migration
        // to make smooth migration between different ingress rules format remove old (pre QinQ) rule by cookie match
        OFFactory of = getSw().getOFFactory();
        ofMessages.add(of.buildFlowDelete()
                               .setTableId(getSwitchDescriptor().getTableDispatch())
                               .setCookie(U64.of(cookie.getValue()))
                               .build());

        List<CompletableFuture<?>> requests = new ArrayList<>(ofMessages.size());
        try (Session session = getSessionService().open(messageContext, getSw())) {
            for (OFFlowMod message : ofMessages) {
                requests.add(session.write(message));
            }
        }

        return CompletableFuture.allOf(requests.toArray(new CompletableFuture<?>[0]));
    }

    private MeterReport handleMeterRemoveReport(MeterReport report) {
        try {
            report.raiseError();
        } catch (UnsupportedSwitchOperationException e) {
            log.info("Do not remove meter id {} from {} - {}", meterConfig.getId(), switchId, e.getMessage());
        } catch (Exception e) {
            throw maskCallbackException(e);
        }
        return report;
    }

    private List<OFFlowMod> makeIngressModMessages(MeterId effectiveMeterId) {
        List<OFFlowMod> ofMessages = new ArrayList<>(2);
        OFFactory of = getSw().getOFFactory();
        if (FlowEndpoint.isVlanIdSet(endpoint.getOuterVlanId())) {
            ofMessages.add(makeOuterVlanMatchMessage(of));
            if (FlowEndpoint.isVlanIdSet(endpoint.getInnerVlanId())) {
                ofMessages.add(makeInnerVlanMatchAndForwardMessage(of, effectiveMeterId));
            } else {
                ofMessages.add(makeOuterVlanOnlyForwardMessage(of, effectiveMeterId));
            }
        } else {
            ofMessages.add(makeDefaultPortFlowMatchAndForwardMessage(of, effectiveMeterId));
        }

        return ofMessages;
    }

    private OFFlowMod makeOuterVlanMatchMessage(OFFactory of) {
        SwitchDescriptor swDesc = getSwitchDescriptor();
        return makeFlowModBuilder(of)
                .setTableId(swDesc.getTableDispatch())
                .setMatch(of.buildMatch()
                                  .setExact(MatchField.IN_PORT, OFPort.of(endpoint.getPortNumber()))
                                  .setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(endpoint.getOuterVlanId()))
                                  .build())
                .setInstructions(makeOuterVlanMatchMessageInstructions(of, swDesc))
                .build();
    }

    protected List<OFInstruction> makeOuterVlanMatchMessageInstructions(OFFactory of, SwitchDescriptor swDesc) {
        MetadataMatch metadata = MetadataAdapter.INSTANCE.addressOuterVlan(
                OFVlanVidMatch.ofVlan(endpoint.getOuterVlanId()));
        return ImmutableList.of(
                                of.instructions().applyActions(ImmutableList.of(of.actions().popVlan())),
                                of.instructions().writeMetadata(metadata.getValue(), metadata.getMask()),
                                of.instructions().gotoTable(swDesc.getTableIngress()));
    }

    private OFFlowMod makeInnerVlanMatchAndForwardMessage(OFFactory of, MeterId effectiveMeterId) {
        MetadataMatch metadata = MetadataAdapter.INSTANCE.addressOuterVlan(
                OFVlanVidMatch.ofVlan(endpoint.getOuterVlanId()));
        OFFlowMod.Builder builder = makeFlowModBuilder(of)
                .setTableId(getSwitchDescriptor().getTableIngress())
                .setMatch(of.buildMatch()
                                  .setExact(MatchField.IN_PORT, OFPort.of(endpoint.getPortNumber()))
                                  .setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(endpoint.getInnerVlanId()))
                                  .setMasked(MatchField.METADATA,
                                             OFMetadata.of(metadata.getValue()), OFMetadata.of(metadata.getMask()))
                                  .build());
        return makeForwardMessage(of, builder, effectiveMeterId);
    }

    private OFFlowMod makeOuterVlanOnlyForwardMessage(OFFactory of, MeterId effectiveMeterId) {
        MetadataMatch metadata = MetadataAdapter.INSTANCE.addressOuterVlan(
                OFVlanVidMatch.ofVlan(endpoint.getOuterVlanId()));
        OFFlowMod.Builder builder = makeFlowModBuilder(of)
                .setTableId(getSwitchDescriptor().getTableIngress())
                .setPriority(FLOW_PRIORITY - 10)
                .setMatch(of.buildMatch()
                                  .setExact(MatchField.IN_PORT, OFPort.of(endpoint.getPortNumber()))
                                  .setMasked(MatchField.METADATA,
                                             OFMetadata.of(metadata.getValue()), OFMetadata.of(metadata.getMask()))
                                  .build());
        return makeForwardMessage(of, builder, effectiveMeterId);
    }

    private OFFlowMod makeDefaultPortFlowMatchAndForwardMessage(OFFactory of, MeterId effectiveMeterId) {
        OFFlowMod.Builder builder = makeFlowModBuilder(of)
                // FIXME we need some space between match rules (so it should be -10 instead of -1)
                .setPriority(FLOW_PRIORITY - 1)
                .setMatch(of.buildMatch()
                                  .setExact(MatchField.IN_PORT, OFPort.of(endpoint.getPortNumber()))
                                  .build());
        return makeForwardMessage(of, builder, effectiveMeterId);
    }

    private OFFlowMod makeForwardMessage(OFFactory of, OFFlowMod.Builder builder, MeterId effectiveMeterId) {
        builder.setInstructions(makeForwardMessageInstructions(of, effectiveMeterId));
        if (getSwitchFeatures().contains(Feature.RESET_COUNTS_FLAG)) {
            builder.setFlags(ImmutableSet.of(OFFlowModFlags.RESET_COUNTS));
        }
        return builder.build();
    }

    protected List<OFInstruction> makeForwardMessageInstructions(OFFactory of, MeterId effectiveMeterId) {
        List<OFAction> applyActions = new ArrayList<>();
        List<OFInstruction> instructions = new ArrayList<>();

        if (effectiveMeterId != null) {
            OfAdapter.INSTANCE.makeMeterCall(of, effectiveMeterId, applyActions, instructions);
        }

        applyActions.addAll(makeTransformActions(of));
        applyActions.add(makeOutputAction(of));

        instructions.add(of.instructions().applyActions(applyActions));
        return instructions;
    }

    protected abstract List<OFAction> makeTransformActions(OFFactory of);

    protected abstract OFAction makeOutputAction(OFFactory of);

    protected final OFAction makeOutputAction(OFFactory of, OFPort port) {
        return of.actions().buildOutput()
                .setPort(port)
                .build();
    }
}
