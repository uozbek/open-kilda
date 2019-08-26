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
    }

    public Optional<UUID> registerMeterRequestId() {
        if (metersRequest != null) {
            return Optional.empty();
        }

        metersRequest = UUID.randomUUID();
        return Optional.of(metersRequest);
    }
}
