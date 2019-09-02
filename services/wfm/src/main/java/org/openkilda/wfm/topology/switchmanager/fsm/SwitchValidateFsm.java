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

package org.openkilda.wfm.topology.switchmanager.fsm;

import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.utils.AbstractBaseFsm;
import org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateContext;
import org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateEvent;
import org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateState;
import org.openkilda.wfm.topology.switchmanager.model.SpeakerSwitchSchema;
import org.openkilda.wfm.topology.switchmanager.model.SwitchSyncData;
import org.openkilda.wfm.topology.switchmanager.model.ValidateFlowSegmentDescriptor;
import org.openkilda.wfm.topology.switchmanager.service.SwitchManagerCarrier;
import org.openkilda.wfm.topology.switchmanager.service.ValidateService;

import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;

import java.util.List;
import java.util.Optional;

@Slf4j
public class SwitchValidateFsm
        extends AbstractBaseFsm<SwitchValidateFsm, SwitchValidateState, SwitchValidateEvent, SwitchValidateContext> {

    private final SwitchManagerCarrier carrier;
    private final ValidateService validateService;
    private final SwitchId switchId;
    private final String key;

    private SwitchSyncData results;
    private ErrorData error;

    public static SwitchValidateFsmFactory factory(SwitchManagerCarrier carrier, ValidateService service) {
        return new SwitchValidateFsmFactory(carrier, service);
    }

    public SwitchValidateFsm(
            SwitchManagerCarrier carrier, ValidateService validateService, SwitchId switchId, String key) {
        this.carrier = carrier;
        this.validateService = validateService;
        this.switchId = switchId;
        this.key = key;

        log.info("Key: {}, validate FSM initialized", key);
    }

    public void fetchSchemaEnter(
            SwitchValidateState from, SwitchValidateState to, SwitchValidateEvent event,
            SwitchValidateContext context) {
        log.info("Key: {}, sending requests to get switch rules and meters", key);

        CommandContext commandContext = carrier.getCommandContext().fork("schema");
        List<ValidateFlowSegmentDescriptor> segmentDescriptors = validateService.makeSwitchValidateFlowSegments(
                commandContext, switchId);
        carrier.speakerFetchSchema(switchId, segmentDescriptors);
    }

    protected void validateEnter(
            SwitchValidateState from, SwitchValidateState to, SwitchValidateEvent event,
            SwitchValidateContext context) {
        log.info("Key: {}, validate rules", key);
        try {
            results = validateService.validateSwitch(context.getSwitchSchema());
        } catch (Exception e) {
            handleException(e);
        }
    }

    protected void errorEnter(
            SwitchValidateState from, SwitchValidateState to, SwitchValidateEvent event,
            SwitchValidateContext context) {
        ErrorType code = ErrorType.INTERNAL_ERROR;
        String message = context.getErrorMessage();
        if (message == null) {
            code = ErrorType.OPERATION_TIMED_OUT;
            message = "Unable fetch speaker schema - TIMEOUT";
        }

        error = new ErrorData(code, String.format("Unable to perform switch %s validation", switchId), message);
    }

    private void handleException(Exception e) {
        log.error("Exception inside FSM", e);

        SwitchValidateContext context = SwitchValidateContext.builder()
                .errorMessage(e.getMessage())
                .build();
        fire(SwitchValidateEvent.ERROR, context);
    }

    public Optional<SwitchSyncData> getResults() {
        return Optional.ofNullable(results);
    }

    public Optional<ErrorData> getErrorMessage() {
        return Optional.ofNullable(error);
    }

    public static class SwitchValidateFsmFactory {
        private final SwitchManagerCarrier carrier;
        private final ValidateService service;

        private final StateMachineBuilder<SwitchValidateFsm, SwitchValidateState, SwitchValidateEvent,
                SwitchValidateContext> builder;

        SwitchValidateFsmFactory(SwitchManagerCarrier carrier, ValidateService service) {
            this.carrier = carrier;
            this.service = service;

            builder = StateMachineBuilderFactory.create(
                    SwitchValidateFsm.class, SwitchValidateState.class, SwitchValidateEvent.class,
                    SwitchValidateContext.class,
                    // extra args
                    SwitchManagerCarrier.class, ValidateService.class, SwitchId.class, String.class);

            // INIT
            builder.transition()
                    .from(SwitchValidateState.INIT).to(SwitchValidateState.FETCH_SCHEMA).on(SwitchValidateEvent.NEXT);

            // FETCH_SCHEMA
            builder.transition()
                    .from(SwitchValidateState.FETCH_SCHEMA).to(SwitchValidateState.VALIDATE)
                    .on(SwitchValidateEvent.SWITCH_SCHEMA);
            builder.transition()
                    .from(SwitchValidateState.FETCH_SCHEMA).to(SwitchValidateState.ERROR)
                    .on(SwitchValidateEvent.ERROR);
            builder.transition()
                    .from(SwitchValidateState.FETCH_SCHEMA).to(SwitchValidateState.ERROR)
                    .on(SwitchValidateEvent.WORKER_ERROR);
            builder.transition()
                    .from(SwitchValidateState.FETCH_SCHEMA).to(SwitchValidateState.ERROR)
                    .on(SwitchValidateEvent.TIMEOUT);
            builder.onEntry(SwitchValidateState.FETCH_SCHEMA)
                    .callMethod("fetchSchemaEnter");

            // VALIDATE
            builder.transition()
                    .from(SwitchValidateState.VALIDATE).to(SwitchValidateState.EXIT)
                    .on(SwitchValidateEvent.NEXT);
            builder.onEntry(SwitchValidateState.VALIDATE)
                    .callMethod("validateEnter");

            // ERROR
            builder.transition()
                    .from(SwitchValidateState.ERROR).to(SwitchValidateState.EXIT)
                    .on(SwitchValidateEvent.NEXT);
            builder.onEntry(SwitchValidateState.ERROR)
                    .callMethod("errorEnter");

            // EXIT
            builder.defineFinalState(SwitchValidateState.EXIT);
        }

        public SwitchValidateFsm produce(SwitchId switchId, String key) {
            SwitchValidateFsm fsm = builder.newStateMachine(SwitchValidateState.INIT, carrier, service, switchId, key);
            fsm.start();
            return fsm;
        }
    }

    @Value
    @Builder
    public static class SwitchValidateContext {
        private final String errorMessage;
        private final SpeakerSwitchSchema switchSchema;
    }

    public enum SwitchValidateState {
        INIT,
        FETCH_SCHEMA,
        VALIDATE,
        ERROR,
        EXIT
    }

    public enum SwitchValidateEvent {
        NEXT,
        SWITCH_SCHEMA,
        TIMEOUT, ERROR, WORKER_ERROR
    }
}
