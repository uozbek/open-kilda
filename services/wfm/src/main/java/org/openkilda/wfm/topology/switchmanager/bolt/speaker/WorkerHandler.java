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

import org.openkilda.floodlight.api.response.SpeakerResponse;
import org.openkilda.messaging.Message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class WorkerHandler {
    protected final Logger log = LoggerFactory.getLogger(getClass());

    public abstract void speakerResponse(Message response);

    public abstract void speakerResponse(SpeakerResponse response);

    public abstract void timeout();

    public void replaced() {
        log.error(
                "H&S key worker key reusage/collision detected (drop partially executed command {})",
                getClass().getName());
    }

    public abstract boolean isCompleted();
}
