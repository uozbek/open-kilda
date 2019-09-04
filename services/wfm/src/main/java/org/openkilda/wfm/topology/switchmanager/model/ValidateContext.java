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

import org.openkilda.model.SwitchId;
import org.openkilda.model.of.FlowSegmentSchema;
import org.openkilda.model.of.MeterSchema;
import org.openkilda.model.of.OfFlowSchema;
import org.openkilda.model.validate.OfFlowReference;
import org.openkilda.model.validate.OfMeterReference;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Value
public class ValidateContext {
    private final Map<OfFlowReference, List<OfFlowSchema>> actualOfFlows = new HashMap<>();
    private final Map<OfMeterReference, MeterSchema> actualOfMeters = new HashMap<>();
    private final Set<OfMeterReference> seenMeters = new HashSet<>();

    private final List<ValidateFlowSegmentDescriptor> expectedFlowSegments = new ArrayList<>();
    private final Map<OfFlowReference, OfFlowSchema> expectedDefaultOfFlows = new HashMap<>();
    private final Map<OfMeterReference, MeterSchema> expectedOfMeters = new HashMap<>();

    private final List<ValidateFlowSegmentDescriptor> corruptedSegments = new ArrayList<>();

    public ValidateContext(SpeakerSwitchSchema switchSchema) {
        this(Collections.singletonList(switchSchema));
    }

    public ValidateContext(List<SpeakerSwitchSchema> validateData) {
        for (SpeakerSwitchSchema schema : validateData) {
            unpackOfFlows(schema);
            unpackMeters(schema);
            unpackExpectedFlowSegments(schema);
            unpackExpectedDefaultOfFlows(schema.getDefaultFlowsSchema());
        }
    }

    /**
     * Lookup thought actual OF flows return first match and remove it from the list of actual OF flows.
     * Remove it required to be able to validate setup with 2 exact OF flows.
     */
    public Optional<OfFlowSchema> lookupAndExtractActualOfFlowSchema(OfFlowReference ref, OfFlowSchema match) {
        List<OfFlowSchema> matchCandidates = actualOfFlows.getOrDefault(ref, Collections.emptyList());
        Iterator<OfFlowSchema> iter = matchCandidates.iterator();

        OfFlowSchema actual = null;
        boolean isFound = false;
        while (iter.hasNext()) {
            actual = iter.next();
            if (! match.equals(actual)) {
                continue;
            }

            // full match
            isFound = true;
            iter.remove();
        }

        if (isFound) {
            return Optional.of(actual);
        }
        return Optional.empty();
    }

    public Optional<MeterSchema> lookupExpectedMeterSchema(OfMeterReference ref) {
        return Optional.ofNullable(expectedOfMeters.get(ref));
    }

    public Optional<MeterSchema> lookupActualMeterSchema(OfMeterReference ref) {
        return Optional.ofNullable(actualOfMeters.get(ref));
    }

    private void unpackOfFlows(SpeakerSwitchSchema switchSchema) {
        for (SwitchOfTableDump tableDump : switchSchema.getTables().values()) {
            SwitchId datapath = tableDump.getDatapath();
            int tableId = tableDump.getTableId();

            for (OfFlowSchema entry : tableDump.getEntries()) {
                if (entry.getMeterSchema() != null) {
                    log.error("Got table dump from speaker with filled .meterSchema property: {}", entry);
                    entry = extractMeterSchema(entry);
                }
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

    private void unpackExpectedFlowSegments(SpeakerSwitchSchema switchSchema) {
        for (ValidateFlowSegmentDescriptor entry : switchSchema.getFlowSegments()) {
            FlowSegmentSchema segmentSchema = entry.getSchema();
            unpackExpectedMeter(segmentSchema.getDatapath(), segmentSchema.getEntries());
            expectedFlowSegments.add(patchExpectedFlowSegment(entry));
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

    private void unpackExpectedMeter(SwitchId datapath, Collection<OfFlowSchema> expectedEntries) {
        for (OfFlowSchema entry : expectedEntries) {
            MeterSchema meterSchema = entry.getMeterSchema();
            if (meterSchema != null) {
                OfMeterReference ref = new OfMeterReference(meterSchema.getMeterId(), datapath);
                expectedOfMeters.put(ref, meterSchema);
            }
        }
    }

    private ValidateFlowSegmentDescriptor patchExpectedFlowSegment(ValidateFlowSegmentDescriptor segmentDescriptor) {
        FlowSegmentSchema schema = segmentDescriptor.getSchema();
        List<OfFlowSchema> patchedFlows = new ArrayList<>(schema.getEntries().size());
        boolean needPatch = false;
        for (OfFlowSchema entry : schema.getEntries()) {
            OfFlowSchema replace = extractMeterSchema(entry);
            needPatch |= entry != replace;
            patchedFlows.add(replace);
        }

        if (! needPatch) {
            return segmentDescriptor;
        }
        return segmentDescriptor.toBuilder()
                .schema(new FlowSegmentSchema(schema.getDatapath(), patchedFlows))
                .build();
    }

    private OfFlowSchema extractMeterSchema(OfFlowSchema flowSchema) {
        if (flowSchema.getMeterSchema() == null) {
            return flowSchema;
        }
        return flowSchema.toBuilder()
                .meterSchema(null)
                .build();
    }

    public void recordCorruptedSegment(ValidateFlowSegmentDescriptor segmentDescriptor) {
        corruptedSegments.add(segmentDescriptor);
    }

    public void recordSeenMeter(OfMeterReference ref) {
        seenMeters.add(ref);
    }
}
