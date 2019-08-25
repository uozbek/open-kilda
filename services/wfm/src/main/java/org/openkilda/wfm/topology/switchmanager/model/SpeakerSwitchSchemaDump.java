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

package org.openkilda.wfm.topology.switchmanager.model;

import org.openkilda.floodlight.api.FlowSegmentSchema;
import org.openkilda.floodlight.api.request.FlowSegmentBlankGenericResolver;

import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class SpeakerSwitchSchemaDump {
    private final Map<UUID, FlowSegmentBlankGenericResolver> requestBlanks = new HashMap<>();
    private final Map<UUID, FlowSegmentSchemaRequestResponse> ofSchema = new HashMap<>();

    private final Map<Integer, UUID> tableRequests = new HashMap<>();
    private final List<SwitchOfTableDump> tableDumps = new ArrayList<>();

    @Getter
    private UUID metersRequest = null;
    private SwitchOfMeterDump meterDump = null;

    public SpeakerSwitchSchemaDump(List<FlowSegmentBlankGenericResolver> schemaRequests) {
        for (FlowSegmentBlankGenericResolver entry : schemaRequests) {
            requestBlanks.put(entry.getCommandId(), entry);
        }
    }

    public boolean isComplete() {
        if (requestBlanks.size() != ofSchema.size()) {
            return false;
        }
        if (tableRequests.size() != tableDumps.size()) {
            return false;
        }
        return metersRequest == null || meterDump != null;
    }

    public boolean saveSchemaResponse(UUID requestId, FlowSegmentSchema schema) {
        FlowSegmentBlankGenericResolver blank = requestBlanks.remove(requestId);
        if (blank != null) {
            ofSchema.put(requestId, new FlowSegmentSchemaRequestResponse(blank, schema));
            return true;
        }
        return false;
    }

    public void saveTableDump(UUID requestId, SwitchOfTableDump dump) {
        UUID pendingId = tableRequests.get(dump.getTableId());
        if (pendingId != null && pendingId.equals(requestId)) {
            tableDumps.add(dump);
        }
    }

    public void saveMetersDump(UUID requestId, SwitchOfMeterDump dump) {
        if (metersRequest != null && metersRequest.equals(requestId)) {
            meterDump = dump;
        }
    }

    public Optional<UUID> registerTableRequestId(Integer tableId) {
        if (tableRequests.containsKey(tableId)) {
            return Optional.empty();
        }

        UUID requestId = UUID.randomUUID();
        tableRequests.put(tableId, requestId);
        return Optional.of(requestId);
    }

    public Optional<UUID> registerMeterRequestId() {
        if (metersRequest != null) {
            return Optional.empty();
        }

        metersRequest = UUID.randomUUID();
        return Optional.of(metersRequest);
    }
}
