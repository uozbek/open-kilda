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

import org.openkilda.floodlight.error.SessionErrorResponseException;
import org.openkilda.floodlight.error.SwitchNotFoundException;
import org.openkilda.floodlight.service.session.SessionService;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.SwitchId;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.OFSwitchManager;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.projectfloodlight.openflow.protocol.OFErrorMsg;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.types.DatapathId;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public abstract class SpeakerCommandV2<T extends SpeakerCommandReport> extends SpeakerCommand<T> {
    private SessionService sessionService;
    private IOFSwitch sw;

    public SpeakerCommandV2(SwitchId switchId, MessageContext messageContext) {
        super(switchId, messageContext);
    }

    @Override
    public CompletableFuture<T> execute(FloodlightModuleContext moduleContext) {
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            setup(moduleContext);
            makeExecutePlan()
                    .whenComplete((result, error) -> {
                        if (error == null) {
                            future.complete(result);
                        } else {
                            handleError(future, error);
                        }
                    });
        } catch (Exception e) {
            future.complete(makeReport(e));
        }
        return future;
    }

    protected abstract CompletableFuture<T> makeExecutePlan() throws Exception;

    protected abstract T makeReport(Exception error);

    protected void setup(FloodlightModuleContext moduleContext) throws SwitchNotFoundException {
        OFSwitchManager ofSwitchManager = moduleContext.getServiceImpl(OFSwitchManager.class);
        sessionService = moduleContext.getServiceImpl(SessionService.class);

        DatapathId dpId = DatapathId.of(switchId.toLong());
        sw = ofSwitchManager.getActiveSwitch(dpId);
        if (sw == null) {
            throw new SwitchNotFoundException(dpId);
        }
    }

    protected CompletableFuture<Optional<OFMessage>> setupErrorHandler(
            CompletableFuture<Optional<OFMessage>> future, IOfErrorResponseHandler handler) {
        CompletableFuture<Optional<OFMessage>> branch = new CompletableFuture<>();

        future.whenComplete((response, error) -> {
            if (error == null) {
                branch.complete(response);
            } else {
                Throwable actualError = unwrapError(error);
                if (actualError instanceof SessionErrorResponseException) {
                    OFErrorMsg errorResponse = ((SessionErrorResponseException) error).getErrorResponse();
                    propagateFutureResponse(branch, handler.handleOfError(errorResponse));
                } else {
                    branch.completeExceptionally(actualError);
                }
            }
        });
        return branch;
    }

    protected IOFSwitch getSw() {
        return sw;
    }

    protected SessionService getSessionService() {
        return sessionService;
    }

    protected <K> void propagateFutureResponse(CompletableFuture<K> outerStream, CompletableFuture<K> nested) {
        nested.whenComplete((result, error) -> {
            if (error == null) {
                outerStream.complete(result);
            } else {
                outerStream.completeExceptionally(error);
            }
        });
    }

    private void handleError(CompletableFuture<T> future, Throwable error) {
        if (error instanceof Exception) {
            future.complete(makeReport((Exception) error));
        } else {
            future.completeExceptionally(error);
        }
    }
}
