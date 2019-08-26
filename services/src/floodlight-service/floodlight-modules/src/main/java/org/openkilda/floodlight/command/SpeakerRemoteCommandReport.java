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

package org.openkilda.floodlight.command;

import org.openkilda.floodlight.api.response.SpeakerErrorCode;
import org.openkilda.floodlight.api.response.SpeakerErrorResponse;
import org.openkilda.floodlight.api.response.SpeakerResponse;

public abstract class SpeakerRemoteCommandReport extends SpeakerCommandReport {
    private final SpeakerRemoteCommand<?> command;

    protected SpeakerRemoteCommandReport(SpeakerRemoteCommand<?> command, Exception error) {
        super(error);
        this.command = command;
    }

    @Override
    protected SpeakerResponse makeErrorReply(SpeakerErrorCode errorCode) {
        return SpeakerErrorResponse.builder()
                .messageContext(command.getMessageContext())
                .commandId(command.getCommandId())
                .switchId(command.getSwitchId())
                .errorCode(errorCode)
                .build();
    }
}
