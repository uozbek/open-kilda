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

import org.openkilda.floodlight.api.MeterSchema;
import org.openkilda.floodlight.api.MeterSchemaBand;
import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchId;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import org.projectfloodlight.openflow.protocol.OFMeterFlags;
import org.projectfloodlight.openflow.protocol.OFMeterMod;
import org.projectfloodlight.openflow.protocol.meterband.OFMeterBand;
import org.projectfloodlight.openflow.protocol.meterband.OFMeterBandDrop;
import org.projectfloodlight.openflow.types.DatapathId;

@Mapper
public abstract class MeterSchemaMapper {
    public static final MeterSchemaMapper INSTANCE = Mappers.getMapper(MeterSchemaMapper.class);

    public MeterSchema map(DatapathId datapath, OFMeterMod meterMod) {
        MeterSchema.MeterSchemaBuilder schema = MeterSchema.builder()
                .datapath(new SwitchId(datapath.getLong()))
                .meterId(new MeterId(meterMod.getMeterId()));

        for (OFMeterFlags flag : meterMod.getFlags()) {
            schema.flag(mapFlag(flag));
        }
        for (OFMeterBand rawBand : meterMod.getBands()) {
            MeterSchemaBand.MeterSchemaBandBuilder band = MeterSchemaBand.builder()
                    .type(rawBand.getType());
            if (rawBand instanceof OFMeterBandDrop) {
                OFMeterBandDrop actualBand = (OFMeterBandDrop) rawBand;
                band.rate(actualBand.getRate());
                band.burstSize(actualBand.getBurstSize());
            }
            // do not make detailed parsing of other meter's band types
        }

        return schema.build();
    }

    public String mapFlag(OFMeterFlags value) {
        return value.name();
    }
}
