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

import org.openkilda.floodlight.command.meter.MeterReport;
import org.openkilda.floodlight.command.meter.MeterRemoveCommand;
import org.openkilda.floodlight.error.OfDeleteException;
import org.openkilda.floodlight.error.UnsupportedSwitchOperationException;
import org.openkilda.floodlight.service.session.Session;
import org.openkilda.messaging.MessageContext;
import org.openkilda.messaging.command.switches.DeleteRulesCriteria;
import org.openkilda.model.Cookie;
import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonProperty;
import net.floodlightcontroller.core.IOFSwitch;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowDelete;
import org.projectfloodlight.openflow.protocol.OFFlowStatsEntry;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class FlowRemoveCommand extends AbstractFlowSegmentCommand<FlowSegmentReport> {

    private final MeterId meterId;
    private final DeleteRulesCriteria criteria;

    public FlowRemoveCommand(@JsonProperty("command_id") UUID commandId,
                             @JsonProperty("flowid") String flowId,
                             @JsonProperty("message_context") MessageContext messageContext,
                             @JsonProperty("cookie") Cookie cookie,
                             @JsonProperty("switch_id") SwitchId switchId,
                             @JsonProperty("meter_id") MeterId meterId,
                             @JsonProperty("criteria") DeleteRulesCriteria criteria) {
        super(commandId, flowId, messageContext, cookie, switchId);
        this.meterId = meterId;
        this.criteria = criteria;
    }

    @Override
    protected CompletableFuture<FlowSegmentReport> makeExecutePlan() throws Exception {
        CompletableFuture<List<OFFlowStatsEntry>> ofFlowBefore = planOfFlowTableDump();
        CompletableFuture<List<OFFlowStatsEntry>> ofFlowAfter = ofFlowBefore
                .thenCompose(ignored -> planDelete())
                .thenCompose(ignored -> planOfFlowTableDump());

        return ofFlowAfter
                .thenAcceptBoth(ofFlowBefore, (after, before) -> ensureRulesDeleted(before, after))
                .thenApply(ignore -> new FlowSegmentReport(this));
    }

    @Override
    protected FlowSegmentReport makeReport(Exception error) {
        return new FlowSegmentReport(this, error);
    }

    private CompletableFuture<Void> planDelete() {
        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
        if (meterId != null) {
            future = future.thenCompose(ignore -> planMeterDelete());
        }
        return future.thenCompose(ignore -> planOfFlowDelete());
    }

    private CompletableFuture<Void> planMeterDelete() {
        MeterRemoveCommand meterRemoveCommand = new MeterRemoveCommand(messageContext, switchId, meterId);
        return meterRemoveCommand.execute(getModuleContext())
                .thenAccept(this::handleMeterDelete);
    }

    private CompletableFuture<Void> planOfFlowDelete() {
        IOFSwitch sw = getSw();

        OFFlowDelete deleteMessage = makeOfFlowDeleteMessage();
        try (Session session = getSessionService().open(messageContext, sw)) {
            return session.write(deleteMessage)
                    .thenAccept(ignore -> log.info(
                            "Delete OF flow by criteria {} from switch {}.", criteria, sw.getId()));
        }
    }

    private void handleMeterDelete(MeterReport report) {
        try {
            report.raiseError();
        } catch (UnsupportedSwitchOperationException e) {
            log.debug("Skip meter {} deletion for flow {} on switch {}: {}", meterId, flowId, switchId, e.getMessage());
        } catch (Exception e) {
            throw maskCallbackException(e);
        }
    }

    private OFFlowDelete makeOfFlowDeleteMessage() {
        OFFactory ofFactory = getSw().getOFFactory();
        OFFlowDelete.Builder builder = ofFactory.buildFlowDelete();
        Optional.ofNullable(criteria.getCookie())
                .ifPresent(flowCookie -> {
                    builder.setCookie(U64.of(criteria.getCookie()));
                    builder.setCookieMask(U64.NO_MASK);
                });

        if (criteria.getInPort() != null) {
            // Match either In Port or both Port & Vlan criteria.
            Match match = matchFlow(criteria.getInPort(),
                    Optional.ofNullable(criteria.getEncapsulationId()).orElse(0), ofFactory);
            builder.setMatch(match);
        } else if (criteria.getEncapsulationId() != null) {
            // Match In Vlan criterion if In Port is not specified
            Match.Builder matchBuilder = ofFactory.buildMatch();
            matchVlan(ofFactory, matchBuilder, criteria.getEncapsulationId());
            builder.setMatch(matchBuilder.build());
        }

        Optional.ofNullable(criteria.getPriority())
                .ifPresent(priority -> builder.setPriority(criteria.getPriority()));

        Optional.ofNullable(criteria.getOutPort())
                .ifPresent(outPort -> builder.setOutPort(OFPort.of(criteria.getOutPort())));

        return builder.build();
    }

    private void ensureRulesDeleted(List<OFFlowStatsEntry> entriesBefore, List<OFFlowStatsEntry> entriesAfter) {
        entriesAfter.stream()
                .filter(entry -> entry.getCookie().equals(U64.of(cookie.getValue())))
                .findAny()
                .ifPresent(nonDeleted -> {
                    throw maskCallbackException(new OfDeleteException(getSw().getId(), cookie.getValue()));
                });

        // we might accidentally delete rules belong to another flows. In order to be able to track it we write all
        // deleted rules into the log.
        entriesBefore.stream()
                .map(entry -> entry.getCookie().getValue())
                .filter(cookieBefore -> entriesAfter.stream()
                        .noneMatch(after -> after.getCookie().getValue() == cookieBefore))
                .forEach(deleted -> log.info("Rule with cookie {} has been removed from switch {}.",
                        deleted, switchId));
    }
}
