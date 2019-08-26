/*
 * Copyright 2019 Telstra Open Source
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

package org.openkilda.wfm.topology.switchmanager.bolt.speaker;

import org.openkilda.floodlight.api.request.FlowSegmentBlankGenericResolver;
import org.openkilda.wfm.topology.switchmanager.model.FlowSegmentSchemaRequestResponse;
import org.openkilda.wfm.topology.switchmanager.model.SwitchOfMeterDump;
import org.openkilda.wfm.topology.switchmanager.model.SwitchOfTableDump;
import org.openkilda.wfm.topology.switchmanager.service.SpeakerWorkerCarrier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SchemaFetchHandler implements IHandler {
    private final SpeakerWorkerCarrier carrier;

    private final Map<UUID, FlowSegmentBlankGenericResolver> requestBlanks = new HashMap<>();
    private final Map<UUID, FlowSegmentSchemaRequestResponse> ofSchema = new HashMap<>();

    private final Map<Integer, UUID> tableRequests = new HashMap<>();
    private final List<SwitchOfTableDump> tableDumps = new ArrayList<>();

    private UUID metersRequest = null;
    private SwitchOfMeterDump meterDump = null;

    public SchemaFetchHandler(SpeakerWorkerCarrier carrier, List<FlowSegmentBlankGenericResolver> schemaRequests) {
        this.carrier = carrier;

        for (FlowSegmentBlankGenericResolver entry : schemaRequests) {
            carrier.sendFlowSegmentRequest(entry.makeSchemaRequest());
            requestBlanks.put(entry.getCommandId(), entry);
        }

        requestOfTableDump(0);
    }

    @Override
    public boolean isCompleted() {
        if (requestBlanks.size() != ofSchema.size()) {
            return false;
        }
        if (tableRequests.size() != tableDumps.size()) {
            return false;
        }
        return metersRequest == null || meterDump != null;
    }

    private void requestOfTableDump(Integer tableId) {
        if (tableRequests.containsKey(tableId)) {
            return;
        }

        UUID requestId = UUID.randomUUID();

        // TODO

        tableRequests.put(tableId, requestId);
    }
}
