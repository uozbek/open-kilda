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

package org.openkilda.wfm.topology.switchmanager.service.impl;

import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchId;
import org.openkilda.model.of.MeterSchema;
import org.openkilda.model.of.OfFlowSchema;
import org.openkilda.model.validate.OfFlowReference;
import org.openkilda.model.validate.OfMeterReference;
import org.openkilda.wfm.topology.switchmanager.model.SpeakerSwitchSchema;
import org.openkilda.wfm.topology.switchmanager.model.SwitchDefaultFlowsSchema;
import org.openkilda.wfm.topology.switchmanager.model.SwitchOfMeterDump;
import org.openkilda.wfm.topology.switchmanager.model.SwitchOfTableDump;
import org.openkilda.wfm.topology.switchmanager.model.ValidateFlowSegmentDescriptor;

import lombok.Value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Value
public class ValidateContext {
    private final Map<OfFlowReference, List<OfFlowSchema>> actualOfFlows = new HashMap<>();
    private final Map<OfMeterReference, MeterSchema> actualOfMeters = new HashMap<>();

    private final List<ValidateFlowSegmentDescriptor> expectedFlowSegments = new ArrayList<>();
    private final Map<OfFlowReference, OfFlowSchema> expectedDefaultOfFlows = new HashMap<>();

    private final List<ValidateFlowSegmentDescriptor> corruptedSegments = new ArrayList<>();

    public ValidateContext(SpeakerSwitchSchema switchSchema) {
        this(Collections.singletonList(switchSchema));
    }

    public ValidateContext(List<SpeakerSwitchSchema> validateData) {
        for (SpeakerSwitchSchema schema : validateData) {
            unpackOfFlows(schema);
            expectedFlowSegments.addAll(schema.getFlowSegments());
            unpackExpectedDefaultOfFlows(schema.getDefaultFlowsSchema());
        }
    }

    private void unpackOfFlows(SpeakerSwitchSchema switchSchema) {
        unpackMeters(switchSchema);

        for (SwitchOfTableDump tableDump : switchSchema.getTables().values()) {
            SwitchId datapath = tableDump.getDatapath();
            int tableId = tableDump.getTableId();

            for (OfFlowSchema entry : tableDump.getEntries()) {
                entry = mergeMeterSchema(entry, datapath);
                OfFlowReference key = new OfFlowReference(tableId, entry.getCookie(), datapath);
                actualOfFlows.computeIfAbsent(key, ignore -> new ArrayList<>())
                        .add(entry);
            }
        }
    }

    private void unpackMeters(SpeakerSwitchSchema switchSchema) {
        SwitchOfMeterDump metersDump = switchSchema.getMeters();
        for (MeterSchema entry : metersDump.getEntries()) {
            OfMeterReference ref = new OfMeterReference(entry.getMeterId(), entry.getDatapath());
            actualOfMeters.put(ref, entry);
        }
    }

    private void unpackExpectedDefaultOfFlows(SwitchDefaultFlowsSchema defaultFlowsSchema) {
        if (defaultFlowsSchema == null) {
            return;
        }

        SwitchId datapath = defaultFlowsSchema.getDatapath();
        for (OfFlowSchema schema : defaultFlowsSchema.getEntries()) {
            OfFlowReference ref = new OfFlowReference(datapath, schema);
            expectedDefaultOfFlows.put(ref, schema);
        }
    }

    private OfFlowSchema mergeMeterSchema(OfFlowSchema flowSchema, SwitchId datapath) {
        if (flowSchema.getMeterSchema() != null) {
            return flowSchema;
        }

        MeterId meterId = flowSchema.getMeterId();
        if (meterId == null) {
            return flowSchema;
        }

        MeterSchema meterSchema = actualOfMeters.get(new OfMeterReference(meterId, datapath));
        return flowSchema.toBuilder()
                .meterSchema(meterSchema)
                .build();
    }

    public void recordCorruptedSegment(ValidateFlowSegmentDescriptor segmentDescriptor) {
        corruptedSegments.add(segmentDescriptor);
    }

    public void removeUsedMeter(OfFlowReference flowRef, MeterId meterId) {
        OfMeterReference meterRef = new OfMeterReference(meterId, flowRef.getDatapath());
        actualOfMeters.remove(meterRef);
    }
}
