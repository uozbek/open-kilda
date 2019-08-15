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

import org.openkilda.floodlight.command.SpeakerCommand;
import org.openkilda.floodlight.command.SpeakerCommandProcessor;
import org.openkilda.floodlight.utils.CompletableFutureAdapter;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.Cookie;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.projectfloodlight.openflow.protocol.OFFlowStatsEntry;
import org.projectfloodlight.openflow.protocol.OFFlowStatsReply;
import org.projectfloodlight.openflow.protocol.OFFlowStatsRequest;
import org.projectfloodlight.openflow.types.OFGroup;
import org.projectfloodlight.openflow.types.U64;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class ReadRuleCommand extends SpeakerCommand<ReadRuleReport> {
    protected final UUID commandId;
    protected final String flowId;
    protected final Cookie cookie;

    public ReadRuleCommand(@JsonProperty("command_id") UUID commandId,
                           @JsonProperty("flowid") String flowId,
                           @JsonProperty("message_context") MessageContext messageContext,
                           @JsonProperty("cookie") Cookie cookie,
                           @JsonProperty("switch_id") SwitchId switchId) {
        super(messageContext, switchId);
        this.commandId = commandId;
        this.flowId = flowId;
        this.cookie = cookie;
    }

    @Override
    protected CompletableFuture<ReadRuleReport> makeExecutePlan(
            SpeakerCommandProcessor commandProcessor) {
        log.debug("Getting rule with cookie {} from the switch {}", cookie, switchId);
        return planOfFlowTableDump(cookie)
                .thenApply(this::handleStatsResponse);
    }

    @Override
    protected ReadRuleReport makeReport(Exception error) {
        return new ReadRuleReport(this, error);
    }

    private final CompletableFuture<List<OFFlowStatsEntry>> planOfFlowTableDump(Cookie matchCookie) {
        OFFlowStatsRequest dumpMessage = makeOfFlowTableDumpBuilder()
                .setCookie(U64.of(matchCookie.getValue()))
                .setCookieMask(U64.NO_MASK)
                .build();
        return planOfFlowTableDump(dumpMessage);
    }

    protected final CompletableFuture<List<OFFlowStatsEntry>> planOfFlowTableDump() {
        return planOfFlowTableDump(makeOfFlowTableDumpBuilder().build());
    }

    private CompletableFuture<List<OFFlowStatsEntry>> planOfFlowTableDump(OFFlowStatsRequest dumpMessage) {
        CompletableFuture<List<OFFlowStatsReply>> future =
                new CompletableFutureAdapter<>(messageContext, getSw().writeStatsRequest(dumpMessage));
        return future.thenApply(values -> values.stream()
                .map(OFFlowStatsReply::getEntries)
                .flatMap(List::stream)
                .collect(Collectors.toList()));
    }

    private ReadRuleReport handleStatsResponse(List<OFFlowStatsEntry> statsReplies) {
        OFFlowStatsEntry entry = extractFirstReply(statsReplies);
        return new ReadRuleReport(this, entry);
    }

    private OFFlowStatsEntry extractFirstReply(List<OFFlowStatsEntry> statsReplies) {
        OFFlowStatsEntry result = null;
        int count = 0;
        for (OFFlowStatsEntry entry : statsReplies) {
            count++;
            if (result == null) {
                result = entry;
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

    private OFFlowStatsRequest.Builder makeOfFlowTableDumpBuilder() {
        return getSw().getOFFactory().buildFlowStatsRequest()
                .setOutGroup(OFGroup.ANY)
                .setCookieMask(U64.ZERO);
    }
}
