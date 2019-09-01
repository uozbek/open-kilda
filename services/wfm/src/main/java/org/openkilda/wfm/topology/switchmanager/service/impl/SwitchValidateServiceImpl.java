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

import org.openkilda.floodlight.api.response.SpeakerResponse;
import org.openkilda.messaging.command.switches.SwitchValidateRequest;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.meter.SwitchMeterEntries;
import org.openkilda.messaging.info.rule.SwitchExpectedDefaultFlowEntries;
import org.openkilda.messaging.info.rule.SwitchFlowEntries;
import org.openkilda.messaging.info.switches.MetersValidationEntry;
import org.openkilda.messaging.info.switches.RulesValidationEntry;
import org.openkilda.messaging.info.switches.SwitchValidationResponse;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.flow.resources.FlowResourcesConfig;
import org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm;
import org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateContext;
import org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateEvent;
import org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateFsmFactory;
import org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateState;
import org.openkilda.wfm.topology.switchmanager.model.SpeakerSwitchSchema;
import org.openkilda.wfm.topology.switchmanager.model.ValidateSwitchReport;
import org.openkilda.wfm.topology.switchmanager.model.ValidationResult;
import org.openkilda.wfm.topology.switchmanager.service.SwitchManagerCarrier;
import org.openkilda.wfm.topology.switchmanager.service.SwitchValidateService;
import org.openkilda.wfm.topology.switchmanager.service.ValidateService;

import com.google.common.annotations.VisibleForTesting;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
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
        SwitchValidateFsm fsm = fsmFactory.produce(request, key);
        skipIntermediateStates(fsm, context);
    }

    @Override
    public void handleFlowEntriesResponse(String key, SwitchFlowEntries data) {
        SwitchValidateFsm fsm = fsms.get(key);
        if (fsm == null) {
            logFsmNotFound(key);
            return;
        }

        fsm.fire(SwitchValidateEvent.RULES_RECEIVED, data.getFlowEntries());
        skipIntermediateStates(fsm, context);
    }

    @Override
    public void handleExpectedDefaultFlowEntriesResponse(String key, SwitchExpectedDefaultFlowEntries data) {
        SwitchValidateFsm fsm = fsms.get(key);
        if (fsm == null) {
            logFsmNotFound(key);
            return;
        }

        fsm.fire(SwitchValidateEvent.EXPECTED_DEFAULT_RULES_RECEIVED, data.getFlowEntries());
        skipIntermediateStates(fsm, context);
    }

    @Override
    public void handleMeterEntriesResponse(String key, SwitchMeterEntries data) {
        SwitchValidateFsm fsm = fsms.get(key);
        if (fsm == null) {
            logFsmNotFound(key);
            return;
        }

        fsm.fire(SwitchValidateEvent.METERS_RECEIVED, data.getMeterEntries());
        skipIntermediateStates(fsm, context);
    }

    @Override
    public void handleMetersUnsupportedResponse(String key) {
        SwitchValidateFsm fsm = fsms.get(key);
        if (fsm == null) {
            logFsmNotFound(key);
            return;
        }

        fsm.fire(SwitchValidateEvent.METERS_UNSUPPORTED);
        skipIntermediateStates(fsm, context);
    }

    @Override
    public void handleTaskTimeout(String key) {
        SwitchValidateFsm fsm = fsms.get(key);
        if (fsm == null) {
            return;
        }

        fsm.fire(SwitchValidateEvent.TIMEOUT);
        skipIntermediateStates(fsm, context);
    }

    @Override
    public void handleTaskError(String key, ErrorMessage message) {
        SwitchValidateFsm fsm = fsms.get(key);
        if (fsm == null) {
            return;
        }

        fsm.fire(SwitchValidateEvent.ERROR, message);
        skipIntermediateStates(fsm, context);
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
                .workerError(errorMessage)
                .build();
        feedFsm(key, SwitchValidateEvent.WORKER_ERROR, context);
    }

    @Override
    public void handleSpeakerErrorResponse(String key, SpeakerResponse response) {
        SwitchValidateContext context = SwitchValidateContext.builder()
                .speakerResponse(response)
                .build();
        SwitchValidateEvent event = response == null ? SwitchValidateEvent.TIMEOUT : SwitchValidateEvent.ERROR;
        feedFsm(key, event, context);
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
            onComplete(requestContext);
            requestsInWork.remove(key);
        }
    }

    private void logFsmNotFound(String key) {
        log.warn("Switch validate FSM with key {} not found", key);
    }

    private void onComplete(RequestContext requestContext, String key) {
        carrier.cancelTimeoutCallback(key);

        SwitchValidateFsm fsm = requestContext.getFsm();
        Optional<ValidateSwitchReport> report = fsm.getReport();
        SwitchValidateRequest request = requestContext.getRequest();
        if (report.isPresent()) {
            onSuccessComplete(request, key, report.get());
        } else {
            onErrorComplete(request, key);
        }
    }

    private void onSuccessComplete(SwitchValidateRequest request, String key, ValidateSwitchReport report) {
        if (request.isPerformSync()) {
            carrier.runSwitchSync(key, request, report);
        } else {

        }
        // TODO
    }

    private void onErrorComplete(SwitchValidateRequest request, String key) {
        RulesValidationEntry rulesValidationEntry = new RulesValidationEntry(
                validateRulesResult.getMissingRules(), validateRulesResult.getMisconfiguredRules(),
                validateRulesResult.getProperRules(), validateRulesResult.getExcessRules());

        MetersValidationEntry metersValidationEntry = null;
        if (processMeters) {
            metersValidationEntry = new MetersValidationEntry(
                    validateMetersResult.getMissingMeters(), validateMetersResult.getMisconfiguredMeters(),
                    validateMetersResult.getProperMeters(), validateMetersResult.getExcessMeters());
        }

        SwitchValidationResponse response = new SwitchValidationResponse(
                rulesValidationEntry, metersValidationEntry);
        InfoMessage message = new InfoMessage(response, System.currentTimeMillis(), key);

        carrier.response(key, message);

        // TODO
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
