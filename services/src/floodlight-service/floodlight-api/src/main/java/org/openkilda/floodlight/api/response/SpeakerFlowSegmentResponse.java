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

package org.openkilda.floodlight.api.response;

import org.openkilda.messaging.MessageContext;
import org.openkilda.model.Cookie;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.UUID;

@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class SpeakerFlowSegmentResponse extends SpeakerResponse {

    @JsonProperty("command_id")
    private final UUID commandId;

    @JsonProperty("flowid")
    private final String flowId;

    @JsonProperty("switch_id")
    private final SwitchId switchId;

    @JsonProperty("cookie")
    protected final Cookie cookie;

    @JsonProperty
    private final boolean success;

    @JsonCreator
    @Builder
    public SpeakerFlowSegmentResponse(
            @JsonProperty("success") boolean success,
            @JsonProperty("command_context") MessageContext messageContext,
            @JsonProperty("command_id") UUID commandId,
            @JsonProperty("flowid") String flowId,
            @JsonProperty("cookie") Cookie cookie,
            @JsonProperty("switch_id") SwitchId switchId) {
        super(messageContext);

        this.commandId = commandId;
        this.flowId = flowId;
        this.switchId = switchId;
        this.cookie = cookie;
        this.success = success;
    }
}
