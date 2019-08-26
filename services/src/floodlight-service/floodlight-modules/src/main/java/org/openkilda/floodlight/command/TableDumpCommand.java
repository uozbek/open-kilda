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

package org.openkilda.floodlight.command;

import org.openkilda.floodlight.converter.OfFlowStatsMapper;
import org.openkilda.floodlight.service.session.Session;
import org.openkilda.floodlight.utils.CompletableFutureAdapter;
import org.openkilda.messaging.MessageContext;
import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.Getter;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowStatsEntry;
import org.projectfloodlight.openflow.protocol.OFFlowStatsReply;
import org.projectfloodlight.openflow.protocol.OFFlowStatsRequest;
import org.projectfloodlight.openflow.types.TableId;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Getter
public class TableDumpCommand extends SpeakerRemoteCommand<TableDumpReport> {
    private final int tableId;

    @JsonCreator
    public TableDumpCommand(
            @JsonProperty("message_context") MessageContext messageContext,
            @JsonProperty("switch_id") SwitchId switchId,
            @JsonProperty("command_id") UUID commandId,
            @JsonProperty("table_id") int tableId) {
        super(messageContext, switchId, commandId);
        this.tableId = tableId;
    }

    @Override
    protected CompletableFuture<TableDumpReport> makeExecutePlan(SpeakerCommandProcessor commandProcessor) {
        ListenableFuture<List<OFFlowStatsReply>> request = getSw().writeStatsRequest(
                makeOfFlowStatsRequest());
        return new CompletableFutureAdapter<>(messageContext, request)
                .thenApply(this::handleFlowsDump)
                .thenApply(this::makeSuccessResponse);
    }

    private List<FlowEntry> handleFlowsDump(List<OFFlowStatsReply> replyChain) {
        List<FlowEntry> flows = new ArrayList<>();
        for (OFFlowStatsReply reply : replyChain) {
            for (OFFlowStatsEntry entry : reply.getEntries()) {
                flows.add(OfFlowStatsMapper.INSTANCE.toFlowEntry(entry));
            }
        }
        return flows;
    }

    private TableDumpReport makeSuccessResponse(List<FlowEntry> dump) {
        return new TableDumpReport(this, dump);
    }

    private OFFlowStatsRequest makeOfFlowStatsRequest() {
        OFFactory of = getSw().getOFFactory();
        return of.buildFlowStatsRequest()
                .setTableId(TableId.of(tableId))
                .build();
    }

    @Override
    protected TableDumpReport makeReport(Exception error) {
        return new TableDumpReport(this, error);
    }
}
