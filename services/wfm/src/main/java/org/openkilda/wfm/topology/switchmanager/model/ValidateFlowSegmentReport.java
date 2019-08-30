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

package org.openkilda.wfm.topology.switchmanager.model;

import org.openkilda.floodlight.api.request.FlowSegmentBlankGenericResolver;
import org.openkilda.model.MeterId;

import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class ValidateFlowSegmentReport {
    @NonNull
    private final FlowSegmentBlankGenericResolver requestBlank;

    @Singular
    private final List<OfFlowReference> properFlows;
    @Singular
    private final List<OfFlowReference> missingFlows;

    @Singular
    private final List<MeterId> properMeters;
    @Singular
    private final List<MeterDefect> meters;
}
