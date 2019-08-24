/*
 * Copyright 2019 Telstra Open Source
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

import org.openkilda.floodlight.api.FlowSegmentSchema;
import org.openkilda.messaging.MessageContext;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
public class SpeakerFlowSegmentSchemaResponse extends SpeakerResponse {
    @JsonProperty("schema")
    private final FlowSegmentSchema schema;

    @Builder
    @JsonCreator
    public SpeakerFlowSegmentSchemaResponse(
            @JsonProperty("message_context") MessageContext messageContext,
            @JsonProperty("schema") FlowSegmentSchema schema) {
        super(messageContext);
        this.schema = schema;
    }
}
