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

import org.openkilda.messaging.MessageContext;
import org.openkilda.model.Cookie;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.UUID;

@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public abstract class FlowSegmentRequest extends SpeakerRequest {
    @JsonProperty("flowid")
    protected final String flowId;

    @JsonProperty("cookie")
    protected final Cookie cookie;

    public FlowSegmentRequest(
            MessageContext context, SwitchId switchId, UUID commandId, String flowId, Cookie cookie) {
        super(context, switchId, commandId);

        requireNonNull(flowId, "Argument flowId must not be null");

        this.flowId = flowId;
        this.cookie = cookie;
    }
}
