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

package org.openkilda.wfm.topology.switchmanager.bolt.speaker.command;

import org.openkilda.floodlight.api.request.FlowSegmentBlankGenericResolver;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.topology.switchmanager.bolt.speaker.SpeakerWorkerBolt;

import java.util.List;

public class SpeakerFetchSchemaCommand extends SpeakerWorkerCommand {
    private final SwitchId switchId;

    private final List<FlowSegmentBlankGenericResolver> requests;

    public SpeakerFetchSchemaCommand(String key, SwitchId switchId, List<FlowSegmentBlankGenericResolver> requests) {
        super(key);
        this.switchId = switchId;
        this.requests = requests;
    }

    @Override
    public void apply(SpeakerWorkerBolt handler) {
        handler.processFetchSchema(key, switchId, requests);
    }
}
