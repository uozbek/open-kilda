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
import org.openkilda.floodlight.error.SwitchNotFoundException;
import org.openkilda.floodlight.error.SwitchOperationException;
import org.openkilda.floodlight.switchmanager.ISwitchManager;
import org.openkilda.messaging.MessageContext;
import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.projectfloodlight.openflow.types.DatapathId;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Getter
public class DefaultFlowsSchemaCommand extends SpeakerRemoteCommand<DefaultFlowsSchemaReport> {
    private ISwitchManager switchManager;

    @JsonCreator
    public DefaultFlowsSchemaCommand(
            @JsonProperty("message_context") MessageContext messageContext,
            @JsonProperty("switch_id") SwitchId switchId,
            @JsonProperty("command_id") UUID commandId,
            @JsonProperty("table_id") int tableId) {
        super(messageContext, switchId, commandId);
    }

    @Override
    protected CompletableFuture<DefaultFlowsSchemaReport> makeExecutePlan(SpeakerCommandProcessor commandProcessor)
            throws Exception {
        List<FlowEntry> entries = evaluateSchema();
        DefaultFlowsSchemaReport report = new DefaultFlowsSchemaReport(this, entries);
        return CompletableFuture.completedFuture(report);
    }

    @Override
    protected void setup(FloodlightModuleContext moduleContext) throws SwitchNotFoundException {
        super.setup(moduleContext);
        switchManager = moduleContext.getServiceImpl(ISwitchManager.class);
    }

    private List<FlowEntry> evaluateSchema() throws SwitchOperationException {
        log.debug("Loading expected default rules for switch {}", switchId);

        return switchManager.getExpectedDefaultFlows(DatapathId.of(switchId.toLong())).stream()
                .map(OfFlowStatsMapper.INSTANCE::toFlowEntry)
                .collect(Collectors.toList());
    }

    @Override
    protected DefaultFlowsSchemaReport makeReport(Exception error) {

        return new DefaultFlowsSchemaReport(this, error);
    }
}
