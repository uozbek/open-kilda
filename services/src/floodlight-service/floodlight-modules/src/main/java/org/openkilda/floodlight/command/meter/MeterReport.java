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

package org.openkilda.floodlight.command.meter;

import org.openkilda.floodlight.api.MeterSchema;
import org.openkilda.floodlight.command.SpeakerCommandReport;
import org.openkilda.model.MeterId;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

@Value
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class MeterReport extends SpeakerCommandReport {
    private MeterSchema schema;

    public MeterReport(MeterSchema schema) {
        this(schema, null);
    }

    public MeterReport(Exception error) {
        this(null, error);
    }

    private MeterReport(MeterSchema schema, Exception error) {
        super(error);
        this.schema = schema;
    }

    public MeterId getMeterId() {
        return schema.getMeterId();
    }
}
