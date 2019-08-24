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
import org.openkilda.floodlight.api.FlowSegmentSchemaEntry;
import org.openkilda.floodlight.api.FlowSegmentSchemaEntry.FlowSegmentSchemaEntryBuilder;
import org.openkilda.model.Cookie;
import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchId;

import com.google.common.collect.ImmutableList;
import net.floodlightcontroller.core.IOFSwitch;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionMeter;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionApplyActions;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionMeter;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.TableId;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class OfFlowModMapper {
    public static final OfFlowModMapper INSTANCE = new OfFlowModMapper();

    public FlowSegmentSchema toFlowSegmentSchema(IOFSwitch sw, List<OFFlowMod> ofRequests) {
        ImmutableList<FlowSegmentSchemaEntry> entries = ImmutableList.copyOf(
                ofRequests.stream()
                        .map(this::toFlowSegmentSchemaEntry)
                        .collect(Collectors.toList()));
        SwitchId datapathId = new SwitchId(sw.getId().getLong());
        return new FlowSegmentSchema(datapathId, entries);
    }

    public FlowSegmentSchemaEntry toFlowSegmentSchemaEntry(OFFlowMod ofRequest) {
        FlowSegmentSchemaEntry.FlowSegmentSchemaEntryBuilder schemaEntry = FlowSegmentSchemaEntry.builder();

        schemaEntry.tableId(Optional.ofNullable(ofRequest.getTableId())
                                    .map(TableId::getValue)
                                    .orElse((short) 0));

        schemaEntry.cookie(Optional.ofNullable(ofRequest.getCookie())
                                   .map(ofCookie -> new Cookie(ofCookie.getValue()))
                                   .orElse(new Cookie(0)));

        decodeInstruction(ofRequest.getInstructions(), schemaEntry);

        return schemaEntry.build();
    }

    private void decodeInstruction(
            List<OFInstruction> instructions, FlowSegmentSchemaEntry.FlowSegmentSchemaEntryBuilder schemaEntry) {
        for (OFInstruction instructionEntry : instructions) {
            if (instructionEntry instanceof OFInstructionMeter) {
                decodeInstruction((OFInstructionMeter) instructionEntry, schemaEntry);
            } else if (instructionEntry instanceof OFInstructionApplyActions) {
                decodeInstruction((OFInstructionApplyActions) instructionEntry, schemaEntry);
            }
            // other instruction types are outside our field of interest
        }
    }

    private void decodeInstruction(
            OFInstructionMeter instruction, FlowSegmentSchemaEntry.FlowSegmentSchemaEntryBuilder schemaEntry) {
        schemaEntry.meterId(new MeterId(instruction.getMeterId()));
    }

    private void decodeInstruction(
            OFInstructionApplyActions instruction, FlowSegmentSchemaEntry.FlowSegmentSchemaEntryBuilder schemaEntry) {
        for (OFAction actionEntry : instruction.getActions()) {
            if (actionEntry instanceof OFActionMeter) {
                decodeAction((OFActionMeter) actionEntry, schemaEntry);
            }
            // other action types are outside our field of interest
        }
    }

    private void decodeAction(OFActionMeter action, FlowSegmentSchemaEntry.FlowSegmentSchemaEntryBuilder schemaEntry) {
        schemaEntry.meterId(new MeterId(action.getMeterId()));
    }

    private OfFlowModMapper() {}
}
