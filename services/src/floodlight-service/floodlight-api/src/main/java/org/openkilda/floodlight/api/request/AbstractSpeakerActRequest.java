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

package org.openkilda.floodlight.api.request;

import static java.util.Objects.requireNonNull;

import org.openkilda.floodlight.api.ActOperation;
import org.openkilda.messaging.AbstractMessage;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.Cookie;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.UUID;

@Getter
@ToString
@EqualsAndHashCode(callSuper = true)
public abstract class AbstractSpeakerActRequest extends AbstractMessage {
    @JsonProperty("operation")
    protected final ActOperation operation;

    @JsonProperty("command_id")
    private final UUID commandId;

    @JsonProperty("flowid")
    private final String flowId;

    @JsonProperty("switch_id")
    final SwitchId switchId;

    @JsonProperty("cookie")
    private final Cookie cookie;

    public AbstractSpeakerActRequest(
            MessageContext context, ActOperation operation, UUID commandId, SwitchId switchId, String flowId,
            Cookie cookie) {
        super(context);

        requireNonNull(operation, "Argument operation must no be null");
        requireNonNull(commandId, "Argument commandId must not be null");
        requireNonNull(switchId, "Argument switchId must not be null");
        requireNonNull(flowId, "Argument flowId must not be null");

        this.operation = operation;

        this.commandId = commandId;
        this.switchId = switchId;
        this.flowId = flowId;
        this.cookie = cookie;
    }
}
