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

package org.openkilda.floodlight.command.flow;

import org.openkilda.floodlight.KafkaChannel;
import org.openkilda.floodlight.command.SpeakerCommandReport;
import org.openkilda.floodlight.flow.response.FlowResponse;
import org.openkilda.floodlight.service.kafka.IKafkaProducerService;

public class FlowInstallReport extends SpeakerCommandReport {
    private final FlowInstallCommand command;

    public FlowInstallReport(FlowInstallCommand command) {
        this(command, null);
    }

    public FlowInstallReport(FlowInstallCommand command, Exception error) {
        super(error);
        this.command = command;
    }

    @Override
    public void reply(KafkaChannel kafkaChannel, IKafkaProducerService kafkaProducerService, String requestKey) {
        FlowResponse reply = FlowResponse.builder()
                .commandId(command.getCommandId())
                .flowId(command.getFlowId())
                .messageContext(command.getMessageContext())
                .switchId(command.getSwitchId())
                .success(true)
                .build();
        kafkaProducerService.sendMessageAndTrack(getResponseTopic(kafkaChannel), requestKey, reply);
    }

    private String getResponseTopic(KafkaChannel kafkaChannel) {
        return kafkaChannel.getSpeakerFlowHsTopic();
    }
}
