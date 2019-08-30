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
import org.openkilda.floodlight.api.MeterSchema;
import org.openkilda.floodlight.api.OfFlowSchema;
import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchId;

import com.google.common.collect.ImmutableList;
import net.floodlightcontroller.core.IOFSwitch;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import org.projectfloodlight.openflow.protocol.OFFlowMod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Mapper(uses = OfFlowSchemaMapper.class)
public abstract class FlowSegmentSchemaMapper {
    public static final FlowSegmentSchemaMapper INSTANCE = Mappers.getMapper(FlowSegmentSchemaMapper.class);

    public FlowSegmentSchema toFlowSegmentSchema(IOFSwitch sw, List<MeterSchema> meters, List<OFFlowMod> ofRequests) {
        Map<MeterId, MeterSchema> metersMap = new HashMap<>();
        for (MeterSchema entry : meters) {
            metersMap.put(entry.getMeterId(), entry);
        }

        ImmutableList<OfFlowSchema> entries = ImmutableList.copyOf(
                ofRequests.stream()
                        .map(item -> mapOfFlow(metersMap, item))
                        .collect(Collectors.toList()));
        SwitchId datapathId = new SwitchId(sw.getId().getLong());
        return new FlowSegmentSchema(datapathId, entries);
    }

    abstract OfFlowSchema mapOfFlow(Map<MeterId, MeterSchema> meters, OFFlowMod request);
}
