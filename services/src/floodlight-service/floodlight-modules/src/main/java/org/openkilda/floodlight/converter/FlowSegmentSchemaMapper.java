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

package org.openkilda.floodlight.converter;

import org.openkilda.floodlight.api.FlowSegmentSchema;
import org.openkilda.floodlight.api.OfFlowSchema;
import org.openkilda.floodlight.api.MeterSchema;
import org.openkilda.model.Cookie;
import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchId;

import com.google.common.collect.ImmutableList;
import net.floodlightcontroller.core.IOFSwitch;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionMeter;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionApplyActions;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionMeter;
import org.projectfloodlight.openflow.types.TableId;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Mapper
public abstract class FlowSegmentSchemaMapper {
    public static final FlowSegmentSchemaMapper INSTANCE = Mappers.getMapper(FlowSegmentSchemaMapper.class);

    public FlowSegmentSchema toFlowSegmentSchema(IOFSwitch sw, List<MeterSchema> meters, List<OFFlowMod> ofRequests) {
        Map<MeterId, MeterSchema> metersMap = new HashMap<>();
        for (MeterSchema entry : meters) {
            metersMap.put(entry.getMeterId(), entry);
        }

        ImmutableList<OfFlowSchema> entries = ImmutableList.copyOf(
                ofRequests.stream()
                        .map(item -> toFlowSegmentSchemaEntry(metersMap, item))
                        .collect(Collectors.toList()));
        SwitchId datapathId = new SwitchId(sw.getId().getLong());
        return new FlowSegmentSchema(datapathId, entries);
    }

    public OfFlowSchema toFlowSegmentSchemaEntry(Map<MeterId, MeterSchema> meters, OFFlowMod ofRequest) {
        OfFlowSchema.FlowSegmentSchemaEntryBuilder schemaEntry = OfFlowSchema.builder();

        schemaEntry.tableId(Optional.ofNullable(ofRequest.getTableId())
                                    .map(TableId::getValue)
                                    .orElse((short) 0));

        schemaEntry.cookie(Optional.ofNullable(ofRequest.getCookie())
                                   .map(ofCookie -> new Cookie(ofCookie.getValue()))
                                   .orElse(new Cookie(0)));

        decodeInstruction(meters, ofRequest.getInstructions(), schemaEntry);

        return schemaEntry.build();
    }

    private void decodeInstruction(
            Map<MeterId, MeterSchema> meters, List<OFInstruction> instructions,
            OfFlowSchema.FlowSegmentSchemaEntryBuilder schemaEntry) {
        for (OFInstruction instructionEntry : instructions) {
            if (instructionEntry instanceof OFInstructionMeter) {
                decodeInstruction(meters, (OFInstructionMeter) instructionEntry, schemaEntry);
            } else if (instructionEntry instanceof OFInstructionApplyActions) {
                decodeInstruction((OFInstructionApplyActions) instructionEntry, schemaEntry);
            }
            // other instruction types are outside our field of interest
        }
    }

    private void decodeInstruction(
            Map<MeterId, MeterSchema> meters, OFInstructionMeter instruction,
            OfFlowSchema.FlowSegmentSchemaEntryBuilder schemaEntry) {
        MeterId flowMeterId = new MeterId(instruction.getMeterId());
        schemaEntry.meterId(flowMeterId);
        schemaEntry.meterSchema(meters.get(flowMeterId));
    }

    private void decodeInstruction(
            OFInstructionApplyActions instruction, OfFlowSchema.FlowSegmentSchemaEntryBuilder schemaEntry) {
        for (OFAction actionEntry : instruction.getActions()) {
            if (actionEntry instanceof OFActionMeter) {
                decodeAction((OFActionMeter) actionEntry, schemaEntry);
            }
            // other action types are outside our field of interest
        }
    }

    private void decodeAction(OFActionMeter action, OfFlowSchema.FlowSegmentSchemaEntryBuilder schemaEntry) {
        schemaEntry.meterId(new MeterId(action.getMeterId()));
    }

    private FlowSegmentSchemaMapper() {}
}
