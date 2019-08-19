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

package org.openkilda.floodlight.api;

import static java.util.Objects.requireNonNull;

import org.openkilda.model.FlowEncapsulationType;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.io.Serializable;

@Value
public class FlowTransitEncapsulation implements Serializable {
    @JsonProperty("id")
    private final Integer id;

    @JsonProperty("type")
    private final FlowEncapsulationType type;

    @JsonCreator
    public FlowTransitEncapsulation(
            @JsonProperty("id") Integer id,
            @JsonProperty("type") FlowEncapsulationType type) {
        requireNonNull(id, "Argument id must not be null");
        requireNonNull(type, "Argument type must not be null");

        this.id = id;
        this.type = type;
    }
}
