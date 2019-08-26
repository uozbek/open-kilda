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

package org.openkilda.wfm.topology.switchmanager.bolt.speaker;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.wfm.topology.switchmanager.service.SpeakerWorkerCarrier;

import lombok.Getter;

public class ProxyRequestHandler extends WorkerHandler {
    private final SpeakerWorkerCarrier carrier;

    @Getter
    private boolean completed = false;

    private final String key;
    private final CommandData requestPayload;

    public ProxyRequestHandler(SpeakerWorkerCarrier carrier, String key, CommandData requestPayload) {
        this.carrier = carrier;
        this.key = key;
        this.requestPayload = requestPayload;

        emitRequest();
    }

    @Override
    public void speakerResponse(Message response) {
        completed = true;

        log.debug("Got a response from speaker {}", response);
        carrier.sendResponse(key, response);
    }

    @Override
    public void timeout() {
        log.debug("Send timeout error to hub {}", key);

        ErrorData errorData = new ErrorData(
                ErrorType.OPERATION_TIMED_OUT, String.format("Timeout for waiting response %s", requestPayload),
                "Error in SpeakerWorkerService");
        ErrorMessage errorMessage = new ErrorMessage(errorData, System.currentTimeMillis(), key);

        carrier.sendResponse(key, errorMessage);
    }

    @Override
    public void replaced() {
        // nothing to do here
    }

    private void emitRequest() {
        log.debug("Got a request from hub bolt {}", requestPayload);
        carrier.sendSpeakerMessage(key, new CommandMessage(requestPayload, System.currentTimeMillis(), key));
    }
}
