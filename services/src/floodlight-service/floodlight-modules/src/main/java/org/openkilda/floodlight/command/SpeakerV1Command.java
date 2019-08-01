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

import org.openkilda.floodlight.FloodlightResponse;
import org.openkilda.floodlight.error.SwitchOperationException;
import org.openkilda.floodlight.error.SwitchWriteException;
import org.openkilda.floodlight.service.session.SessionService;
import org.openkilda.floodlight.switchmanager.ISwitchManager;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.SwitchId;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.types.DatapathId;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public abstract class SpeakerV1Command extends SpeakerCommand {
    public SpeakerV1Command(SwitchId switchId, MessageContext messageContext) {
        super(switchId, messageContext);
    }

    /**
     * Helps to execute OF command and handle successful and error responses.
     * @param moduleContext floodlight context.
     * @return response wrapped into completable future.
     */
    public CompletableFuture<FloodlightResponse> execute(FloodlightModuleContext moduleContext) {
        ISwitchManager switchManager = moduleContext.getServiceImpl(ISwitchManager.class);
        IOFSwitch sw;
        try {
            DatapathId dpid = DatapathId.of(switchId.toLong());
            sw = switchManager.lookupSwitch(dpid);

            return writeCommands(sw, moduleContext)
                    .handle((result, error) -> {
                        if (error != null) {
                            log.error("Error occurred while processing OF command", error);
                            return buildError(error);
                        } else {
                            return result.isPresent() ? buildResponse(result.get()) : buildResponse();
                        }
                    });
        } catch (Exception e) {
            log.error("Failed to execute OF command", e);
            return CompletableFuture.completedFuture(buildError(e));
        }
    }

    /**
     * Writes command to a switch.
     */
    protected CompletableFuture<Optional<OFMessage>> writeCommands(IOFSwitch sw, FloodlightModuleContext moduleContext)
            throws SwitchOperationException {
        SessionService sessionService = moduleContext.getServiceImpl(SessionService.class);
        CompletableFuture<Optional<OFMessage>> chain = CompletableFuture.completedFuture(Optional.empty());
        for (SessionProxy message : getCommands(sw, moduleContext)) {
            chain = chain.thenCompose(res -> {
                try {
                    return message.writeTo(sw, sessionService, messageContext);
                } catch (SwitchWriteException e) {
                    throw new CompletionException(e);
                }
            });
        }
        return chain;
    }

    protected abstract FloodlightResponse buildError(Throwable error);

    protected FloodlightResponse buildResponse() {
        throw new IllegalStateException("No response received from the switch while processing command");
    }

    protected FloodlightResponse buildResponse(OFMessage response) {
        throw new IllegalStateException("Received unexpected message from switch while processing command");
    }

    public abstract List<SessionProxy> getCommands(IOFSwitch sw, FloodlightModuleContext moduleContext)
            throws SwitchOperationException;
}
