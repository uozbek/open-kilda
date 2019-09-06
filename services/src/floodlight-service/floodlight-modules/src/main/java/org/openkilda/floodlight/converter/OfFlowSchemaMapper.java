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

import org.openkilda.model.Cookie;
import org.openkilda.model.MeterId;
import org.openkilda.model.of.MeterSchema;
import org.openkilda.model.of.OfFlowSchema;
import org.openkilda.model.of.OfFlowSchema.OfFlowSchemaBuilder;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFFlowStatsEntry;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionMeter;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionApplyActions;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionMeter;
import org.projectfloodlight.openflow.types.TableId;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Mapper
public abstract class OfFlowSchemaMapper {
    public static final OfFlowSchemaMapper INSTANCE = Mappers.getMapper(OfFlowSchemaMapper.class);

    public OfFlowSchema map(OFFlowMod ofRequest) {
        return map(Collections.emptyMap(), ofRequest);
    }

    /**
     * Produce {@code OfFlowSchema} from {@code OFFlowMod}.
     */
    public OfFlowSchema map(Map<MeterId, MeterSchema> meters, OFFlowMod ofRequest) {
        OfFlowSchema.OfFlowSchemaBuilder schema = OfFlowSchema.builder();

        schema.tableId(Optional.ofNullable(ofRequest.getTableId())
                                    .map(TableId::getValue)
                                    .orElse((short) 0));

        schema.cookie(Optional.ofNullable(ofRequest.getCookie())
                                   .map(ofCookie -> new Cookie(ofCookie.getValue()))
                                   .orElse(new Cookie(0)));

        decodeInstruction(meters, ofRequest.getInstructions(), schema);

        return schema.build();
    }

    /**
     * Produce {@code OfFlowSchema} from {@code OFFlowStatsEntry}.
     */
    public OfFlowSchema map(OFFlowStatsEntry ofFlow) {
        OfFlowSchema.OfFlowSchemaBuilder schema = OfFlowSchema.builder();

        schema.tableId(ofFlow.getTableId().getValue());
        schema.cookie(new Cookie(ofFlow.getCookie().getValue()));
        decodeInstruction(ofFlow.getInstructions(), schema);

        return schema.build();
    }

    private void decodeInstruction(List<OFInstruction> instructions, OfFlowSchemaBuilder schema) {
        decodeInstruction(Collections.emptyMap(), instructions, schema);
    }

    private void decodeInstruction(
            Map<MeterId, MeterSchema> meters, List<OFInstruction> instructions,
            OfFlowSchema.OfFlowSchemaBuilder schemaEntry) {
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
            OfFlowSchema.OfFlowSchemaBuilder schemaEntry) {
        MeterId flowMeterId = new MeterId(instruction.getMeterId());
        schemaEntry.meterId(flowMeterId);
        schemaEntry.meterSchema(meters.get(flowMeterId));
    }

    private void decodeInstruction(
            OFInstructionApplyActions instruction, OfFlowSchema.OfFlowSchemaBuilder schemaEntry) {
        for (OFAction actionEntry : instruction.getActions()) {
            if (actionEntry instanceof OFActionMeter) {
                decodeAction((OFActionMeter) actionEntry, schemaEntry);
            }
            // other action types are outside our field of interest
        }
    }

    private void decodeAction(OFActionMeter action, OfFlowSchema.OfFlowSchemaBuilder schemaEntry) {
        schemaEntry.meterId(new MeterId(action.getMeterId()));
    }
}
