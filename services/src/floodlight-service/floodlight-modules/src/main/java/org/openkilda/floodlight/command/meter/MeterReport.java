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

import org.openkilda.floodlight.KafkaChannel;
import org.openkilda.floodlight.command.SpeakerCommandReport;
import org.openkilda.floodlight.service.kafka.IKafkaProducerService;
import org.openkilda.model.MeterId;

import lombok.Value;

@Value
public class MeterReport extends SpeakerCommandReport {
    private MeterId meterId;

    public MeterReport(MeterId meterId) {
        this(null, meterId);
    }

    public MeterReport(Exception error) {
        this(error, null);
    }

    private MeterReport(Exception error, MeterId meterId) {
        super(error);
        this.meterId = meterId;
    }

    @Override
    public void reply(KafkaChannel kafkaChannel, IKafkaProducerService kafkaProducerService, String requestKey) {
        // at this moment meters command used only as nested command from flow command, so no reply required
    }
}
