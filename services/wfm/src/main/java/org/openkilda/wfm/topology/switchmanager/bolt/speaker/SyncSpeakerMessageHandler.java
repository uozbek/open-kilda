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

import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.wfm.topology.switchmanager.service.SpeakerWorkerCarrier;

import lombok.Getter;

public class SyncSpeakerMessageHandler extends WorkerHandler {
    private final SpeakerWorkerCarrier carrier;

    @Getter
    private boolean completed = false;

    private final String hubKey;
    private final CommandData requestPayload;

    public SyncSpeakerMessageHandler(SpeakerWorkerCarrier carrier, String hubKey, CommandData requestPayload) {
        this.carrier = carrier;
        this.hubKey = hubKey;
        this.requestPayload = requestPayload;

        emitRequest();
    }

    @Override
    public void speakerResponse(Message response) {
        completed = true;

        log.debug("Got a response from speaker {}", response);
        carrier.sendHubSyncResponse(hubKey, response);
    }

    @Override
    public void timeout() {
        log.debug("Send timeout error to hub {}", hubKey);

        ErrorData errorData = new ErrorData(
                ErrorType.OPERATION_TIMED_OUT, String.format("Timeout for waiting response %s", requestPayload),
                "Error in SpeakerWorkerService");
        ErrorMessage errorMessage = new ErrorMessage(errorData, System.currentTimeMillis(), hubKey);

        carrier.sendHubSyncResponse(hubKey, errorMessage);
    }

    private void emitRequest() {
        log.debug("Got a request from hub bolt {}", requestPayload);
        carrier.sendSpeakerCommand(requestPayload);
    }
}
