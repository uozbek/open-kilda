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

package org.openkilda.wfm.topology.switchmanager.bolt.speaker;

import org.openkilda.floodlight.api.request.SpeakerRequest;
import org.openkilda.floodlight.api.response.SpeakerResponse;
import org.openkilda.wfm.topology.switchmanager.service.SpeakerWorkerCarrier;

public class SyncSpeakerRequestHandler extends WorkerHandler {
    private final SpeakerWorkerCarrier carrier;
    private final String hubKey;

    private final SpeakerRequest request;
    private boolean completed = false;

    public SyncSpeakerRequestHandler(SpeakerWorkerCarrier carrier, String hubKey, SpeakerRequest request) {
        this.carrier = carrier;
        this.hubKey = hubKey;

        this.request = request;

        carrier.sendSpeakerCommand(request);
    }

    @Override
    public void speakerResponse(SpeakerResponse response) {
        if (! request.getCommandId().equals(response.getCommandId())) {
            log.warn(
                    "Receive unwanted speaker response - sw:{} commandID: {}",
                    response.getSwitchId(), response.getCommandId());
            return;
        }

        handleResponse(response);
    }

    @Override
    public void timeout() {
        carrier.sendHubSyncError(hubKey, null);
    }

    @Override
    public boolean isCompleted() {
        return completed;
    }

    private void handleResponse(SpeakerResponse response) {
        carrier.sendHubSyncResponse(hubKey, response);
        completed = true;
    }
}
