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

import org.openkilda.messaging.MessageContext;
import org.openkilda.model.Cookie;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.projectfloodlight.openflow.protocol.OFFlowStatsEntry;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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
        return planOfFlowTableDump(cookie)
                .thenApply(this::handleStatsResponse);
    }

    @Override
    protected ReadRuleReport makeReport(Exception error) {
        return new ReadRuleReport(this, error);
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
}
