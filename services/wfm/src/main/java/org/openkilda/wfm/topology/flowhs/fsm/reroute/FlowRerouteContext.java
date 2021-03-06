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

package org.openkilda.wfm.topology.flowhs.fsm.reroute;

import org.openkilda.floodlight.api.response.SpeakerFlowSegmentResponse;
import org.openkilda.model.PathId;
import org.openkilda.wfm.topology.flowhs.fsm.common.FlowContext;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
public class FlowRerouteContext extends FlowContext {
    private String flowId;
    private Set<PathId> pathsToReroute;
    private boolean forceReroute;
    private String rerouteReason;

    @Builder
    public FlowRerouteContext(
            SpeakerFlowSegmentResponse speakerFlowResponse, String flowId, Set<PathId> pathsToReroute,
            boolean forceReroute, String rerouteReason) {
        super(speakerFlowResponse);
        this.flowId = flowId;
        this.pathsToReroute = pathsToReroute;
        this.forceReroute = forceReroute;
        this.rerouteReason = rerouteReason;
    }
}
