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

package org.openkilda.wfm.topology.switchmanager.service.impl;

import org.openkilda.messaging.command.switches.SwitchValidateRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.switches.SwitchValidationResponse;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.flow.resources.FlowResourcesConfig;
import org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm;
import org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateContext;
import org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateEvent;
import org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateFsmFactory;
import org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateState;
import org.openkilda.wfm.topology.switchmanager.model.SpeakerSwitchSchema;
import org.openkilda.wfm.topology.switchmanager.model.SwitchSyncData;
import org.openkilda.wfm.topology.switchmanager.service.SwitchManagerCarrier;
import org.openkilda.wfm.topology.switchmanager.service.SwitchValidateService;
import org.openkilda.wfm.topology.switchmanager.service.ValidateService;

import com.google.common.annotations.VisibleForTesting;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
public class SwitchValidateServiceImpl implements SwitchValidateService {
    private Map<String, RequestContext> requestsInWork = new HashMap<>();

    @VisibleForTesting
    ValidateService validationService;
    private SwitchManagerCarrier carrier;
    private SwitchValidateFsmFactory fsmFactory;

    public SwitchValidateServiceImpl(
            SwitchManagerCarrier carrier, FlowResourcesConfig resourcesConfig, PersistenceManager persistenceManager) {
        this.carrier = carrier;

        validationService = new ValidateServiceImpl(resourcesConfig, persistenceManager);
        fsmFactory = SwitchValidateFsm.factory(carrier, validationService);
    }

    @Override
    public void handleSwitchValidateRequest(String key, SwitchValidateRequest request) {
        SwitchValidateFsm fsm = fsmFactory.produce(request.getSwitchId(), key);
        requestsInWork.put(key, new RequestContext(request, fsm));

        SwitchValidateContext context = SwitchValidateContext.builder().build();
        feedFsm(key, SwitchValidateEvent.NEXT, context);
    }

    @Override
    public void handleSwitchSchema(String key, SpeakerSwitchSchema switchSchema) {
        SwitchValidateContext context = SwitchValidateContext.builder()
                .switchSchema(switchSchema)
                .build();
        feedFsm(key, SwitchValidateEvent.SWITCH_SCHEMA, context);
    }

    @Override
    public void handleWorkerError(String key, String errorMessage) {
        SwitchValidateContext context = SwitchValidateContext.builder()
                .errorMessage(errorMessage)
                .build();
        feedFsm(key, SwitchValidateEvent.WORKER_ERROR, context);
    }

    @Override
    public void handleSpeakerErrorResponse(String key, String errorMessage) {
        SwitchValidateContext context = SwitchValidateContext.builder()
                .errorMessage(errorMessage)
                .build();
        SwitchValidateEvent event = errorMessage == null ? SwitchValidateEvent.TIMEOUT : SwitchValidateEvent.ERROR;
        feedFsm(key, event, context);
    }

    @Override
    public void handleGlobalTimeout(String key) {
        SwitchValidateContext context = SwitchValidateContext.builder().build();
        feedFsm(key, SwitchValidateEvent.TIMEOUT, context);
    }

    private void feedFsm(String key, SwitchValidateEvent event, SwitchValidateContext context) {
        RequestContext requestContext = requestsInWork.get(key);
        if (requestContext == null) {
            log.debug("There is no FSM to receive {} with context {}", event, context);
            return;
        }

        SwitchValidateFsm fsm = requestContext.getFsm();
        fsm.fire(event, context);
        if (skipIntermediateStates(fsm, context)) {
            onComplete(requestContext, key);
            requestsInWork.remove(key);
        }
    }

    private void onComplete(RequestContext requestContext, String key) {
        carrier.cancelTimeoutCallback(key);

        SwitchValidateFsm fsm = requestContext.getFsm();
        Optional<SwitchSyncData> results = fsm.getResults();
        SwitchValidateRequest request = requestContext.getRequest();
        if (results.isPresent()) {
            onSuccessComplete(request, key, results.get());
        } else {
            ErrorData error = fsm.getErrorMessage()
                    .orElse(new ErrorData(ErrorType.INTERNAL_ERROR, "internal error",
                                          "no error description (FSM died without error description)"));
            onErrorComplete(key, error);
        }
    }

    private void onSuccessComplete(SwitchValidateRequest request, String key, SwitchSyncData syncData) {
        if (request.isPerformSync()) {
            carrier.runSwitchSync(key, request, syncData);
        } else {
            SwitchValidationResponse response = new SwitchValidationResponse(syncData.getValidateReport());
            InfoMessage message = new InfoMessage(response, System.currentTimeMillis(), key);
            carrier.response(key, message);
        }
    }

    private void onErrorComplete(String key, ErrorData error) {
        ErrorMessage message = new ErrorMessage(error, System.currentTimeMillis(), key);
        carrier.response(key, message);
    }

    private boolean skipIntermediateStates(SwitchValidateFsm fsm, SwitchValidateContext context) {
        SwitchValidateState previousState;
        SwitchValidateState state = fsm.getCurrentState();
        do {
            previousState = state;
            fsm.fire(SwitchValidateEvent.NEXT, context);
            state = fsm.getCurrentState();
        } while (!fsm.isTerminated() && previousState != state);
        return fsm.isTerminated();
    }

    @Value
    private static class RequestContext {
        private final SwitchValidateRequest request;

        private final SwitchValidateFsm fsm;
    }
}
