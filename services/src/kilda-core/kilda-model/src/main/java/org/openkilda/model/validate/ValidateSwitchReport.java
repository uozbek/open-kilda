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

package org.openkilda.model.validate;

import org.openkilda.model.SwitchId;
import org.openkilda.model.of.MeterSchema;

import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;

import java.util.List;

@Value
@Builder(toBuilder = true)
public class ValidateSwitchReport {
    @NonNull
    private final SwitchId datapath;

    @Singular
    private final List<OfFlowReference> cookieCollisions;
    @Singular
    private final List<MeterCollision> meterCollisions;

    // TODO(surabujin): replace excess* properties with `List<ValidateDefect> defects` property
    @Singular
    private final List<OfFlowReference> excessOfFlows;
    @Singular
    private final List<MeterSchema> excessMeters;

    @Singular
    private final List<ValidateFlowSegmentReport> segmentReports;
    private final ValidateDefaultOfFlowsReport defaultFlowsReport;
}
