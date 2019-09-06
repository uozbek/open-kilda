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

import static java.util.Collections.emptyList;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent.ERROR;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent.METERS_REMOVED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent.NEXT;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent.RULES_REINSTALLED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent.RULES_REMOVED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent.RULES_SYNCHRONIZED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent.SEGMENT_INSTALLED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent.TIMEOUT;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.COMPUTE_REMOVE_METERS;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.COMPUTE_REMOVE_RULES;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.FINISHED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.FINISHED_WITH_ERROR;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.INITIALIZED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.METERS_COMMANDS_SEND;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.RULES_COMMANDS_SEND;

import org.openkilda.floodlight.api.request.FlowSegmentRequest;
import org.openkilda.floodlight.api.response.SpeakerFlowSegmentResponse;
import org.openkilda.floodlight.flow.response.FlowErrorResponse;
import org.openkilda.messaging.command.flow.ReinstallDefaultFlowForSwitchManagerRequest;
import org.openkilda.messaging.command.flow.RemoveFlow;
import org.openkilda.messaging.command.flow.RemoveFlowForSwitchManagerRequest;
import org.openkilda.messaging.command.switches.DeleterMeterForSwitchManagerRequest;
import org.openkilda.messaging.command.switches.SwitchValidateRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.error.rule.SwitchSyncErrorData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.flow.FlowReinstallResponse;
import org.openkilda.messaging.info.switches.SwitchSyncResponse;
import org.openkilda.model.SwitchId;
import org.openkilda.model.validate.OfFlowReference;
import org.openkilda.model.validate.ValidateSwitchReport;
import org.openkilda.wfm.share.utils.AbstractBaseFsm;
import org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent;
import org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState;
import org.openkilda.wfm.topology.switchmanager.model.SwitchSyncData;
import org.openkilda.wfm.topology.switchmanager.model.ValidateFlowSegmentDescriptor;
import org.openkilda.wfm.topology.switchmanager.service.SwitchManagerCarrier;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
// FIXME(surabujin): context must not be represented with Object type
public class SwitchSyncFsm extends AbstractBaseFsm<SwitchSyncFsm, SwitchSyncState, SwitchSyncEvent, Object> {

    private static final String FINISHED_WITH_ERROR_METHOD_NAME = "finishedWithError";
    private static final String FINISHED_METHOD_NAME = "finished";
    private static final String ERROR_LOG_MESSAGE = "Key: {}, message: {}";

    private final NoArgGenerator transactionIdGenerator = Generators.timeBasedGenerator();

    private final String key;
    private final SwitchValidateRequest request;
    private final SwitchManagerCarrier carrier;
    private SwitchId switchId;

    private List<Long> removeDefaultRules = new ArrayList<>();
    private final SwitchSyncData syncData;

    private List<RemoveFlow> excessRules = emptyList();
    private List<Long> excessMeters = emptyList();

    private final Set<UUID> flowSegmentRequests = new HashSet<>();
    private final List<SpeakerFlowSegmentResponse> flowSegmentResponses = new ArrayList<>();

    private int excessRulesPendingResponsesCount = 0;
    private int reinstallDefaultRulesPendingResponsesCount = 0;
    private int excessMetersPendingResponsesCount = 0;

    public SwitchSyncFsm(
            SwitchManagerCarrier carrier, String key, SwitchValidateRequest request, SwitchSyncData syncData) {
        this.carrier = carrier;
        this.key = key;
        this.request = request;
        this.syncData = syncData;
        this.switchId = syncData.getSwitchId();

        log.info("Key: {}, sync FSM initialized", key);
    }

    /**
     * FSM builder.
     */
    public static StateMachineBuilder<SwitchSyncFsm, SwitchSyncState,
            SwitchSyncEvent, Object> builder() {
        StateMachineBuilder<SwitchSyncFsm, SwitchSyncState, SwitchSyncEvent, Object> builder =
                StateMachineBuilderFactory.create(
                        SwitchSyncFsm.class,
                        SwitchSyncState.class,
                        SwitchSyncEvent.class,
                        Object.class,
                        SwitchManagerCarrier.class,
                        String.class,
                        SwitchValidateRequest.class,
                        SwitchSyncData.class);

        builder.externalTransition().from(INITIALIZED).to(COMPUTE_REMOVE_RULES).on(NEXT)
                .callMethod("computeRemoveRules");
        builder.externalTransition().from(COMPUTE_REMOVE_RULES).to(FINISHED_WITH_ERROR).on(ERROR)
                .callMethod(FINISHED_WITH_ERROR_METHOD_NAME);

        builder.externalTransition().from(COMPUTE_REMOVE_RULES).to(COMPUTE_REMOVE_METERS).on(NEXT)
                .callMethod("computeRemoveMeters");
        builder.externalTransition().from(COMPUTE_REMOVE_METERS).to(FINISHED_WITH_ERROR).on(ERROR)
                .callMethod(FINISHED_WITH_ERROR_METHOD_NAME);

        builder.externalTransition().from(COMPUTE_REMOVE_METERS).to(RULES_COMMANDS_SEND).on(NEXT)
                .callMethod("sendRulesCommands");
        builder.internalTransition().within(RULES_COMMANDS_SEND).on(SEGMENT_INSTALLED).callMethod("segmentInstalled");
        builder.internalTransition().within(RULES_COMMANDS_SEND).on(RULES_REMOVED).callMethod("ruleRemoved");
        builder.internalTransition().within(RULES_COMMANDS_SEND).on(RULES_REINSTALLED)
                .callMethod("defaultRuleReinstalled");

        builder.externalTransition().from(RULES_COMMANDS_SEND).to(FINISHED_WITH_ERROR).on(TIMEOUT)
                .callMethod("commandsProcessingFailedByTimeout");
        builder.externalTransition().from(RULES_COMMANDS_SEND).to(FINISHED_WITH_ERROR).on(ERROR)
                .callMethod(FINISHED_WITH_ERROR_METHOD_NAME);

        builder.externalTransition().from(RULES_COMMANDS_SEND).to(METERS_COMMANDS_SEND).on(RULES_SYNCHRONIZED)
                .callMethod("sendMetersCommands");
        builder.internalTransition().within(METERS_COMMANDS_SEND).on(METERS_REMOVED).callMethod("meterRemoved");

        builder.externalTransition().from(METERS_COMMANDS_SEND).to(FINISHED_WITH_ERROR).on(TIMEOUT)
                .callMethod("commandsProcessingFailedByTimeout");
        builder.externalTransition().from(METERS_COMMANDS_SEND).to(FINISHED_WITH_ERROR).on(ERROR)
                .callMethod(FINISHED_WITH_ERROR_METHOD_NAME);
        builder.externalTransition().from(METERS_COMMANDS_SEND).to(FINISHED).on(NEXT)
                .callMethod(FINISHED_METHOD_NAME);

        return builder;
    }

    public String getKey() {
        return key;
    }

    protected void computeRemoveRules(SwitchSyncState from, SwitchSyncState to,
                                      SwitchSyncEvent event, Object context) {
        if (! request.isRemoveExcess()) {
            return;
        }

        log.info("Key: {}, compute remove rules", key);
        try {
            excessRules = buildCommandsToRemoveExcessRules();
        } catch (Exception e) {
            routeInternalError(e);
        }
    }

    protected void computeRemoveMeters(SwitchSyncState from, SwitchSyncState to,
                                       SwitchSyncEvent event, Object context) {
        if (! request.isRemoveExcess() || ! request.isProcessMeters()) {
            return;
        }

        log.info("Key: {}, compute remove meters", key);
        ValidateSwitchReport validateReport = syncData.getValidateReport();
        excessMeters = validateReport.getExcessMeters().stream()
                .map(entry -> entry.getMeterId().getValue())
                .collect(Collectors.toList());
    }

    protected void sendRulesCommands(SwitchSyncState from, SwitchSyncState to,
                                     SwitchSyncEvent event, Object context) {
        if (!excessRules.isEmpty()) {
            log.info("Key: {}, request to remove switch rules has been sent", key);
            excessRulesPendingResponsesCount = excessRules.size();

            for (RemoveFlow command : excessRules) {
                carrier.syncSpeakerMessageRequest(new RemoveFlowForSwitchManagerRequest(switchId, command));
            }
        }

        List<ValidateFlowSegmentDescriptor> corruptedSegments = syncData.getCorruptedFlowSegments();
        if (! corruptedSegments.isEmpty()) {
            log.info("Key: {}, request to install switch rules has been sent", key);
            for (ValidateFlowSegmentDescriptor segment : corruptedSegments) {
                FlowSegmentRequest segmentRequest = segment.getRequestBlank().makeInstallRequest();
                carrier.syncSpeakerFlowSegmentRequest(segmentRequest);
                flowSegmentRequests.add(segmentRequest.getCommandId());
            }
        }

        List<Long> reinstallRules = getReinstallDefaultRules();
        if (!reinstallRules.isEmpty()) {
            log.info("Key: {}, request to reinstall default switch rules has been sent", key);
            reinstallDefaultRulesPendingResponsesCount = reinstallRules.size();

            for (Long rule : reinstallRules) {
                carrier.syncSpeakerMessageRequest(new ReinstallDefaultFlowForSwitchManagerRequest(switchId, rule));
            }
        }

        // workaround for case when we do not install any segment/OF flow
        continueIfRulesCommandsDone();
    }

    private List<Long> getReinstallDefaultRules() {
        // FIXME(surabujin): implement using data from
        //  {@code org.openkilda.model.validate.ValidateSwitchReport.defaultFlowsReport}
        /*
        ValidateRulesResult validateRulesResult = validationResult.getValidateRulesResult();
        List<Long> reinstallRules = validateRulesResult.getMisconfiguredRules().stream()
                .filter(Cookie::isDefaultRule)
                .collect(Collectors.toList());
        if (request.isRemoveExcess()) {
            validateRulesResult.getExcessRules().stream()
                    .filter(Cookie::isDefaultRule)
                    .forEach(reinstallRules::add);
        }
        */
        return Collections.emptyList();
    }

    protected void sendMetersCommands(SwitchSyncState from, SwitchSyncState to,
                                      SwitchSyncEvent event, Object context) {
        if (! excessMeters.isEmpty()) {
            log.info("Key: {}, request to remove switch meters has been sent", key);
            excessMetersPendingResponsesCount = excessMeters.size();

            for (Long meterId : excessMeters) {
                carrier.syncSpeakerMessageRequest(new DeleterMeterForSwitchManagerRequest(switchId, meterId));
            }
        }
    }

    protected void segmentInstalled(SwitchSyncState from, SwitchSyncState to,
                                    SwitchSyncEvent event, Object context) {
        log.info("Key: {}, switch rule installed", key);
        if (context instanceof SpeakerFlowSegmentResponse) {
            SpeakerFlowSegmentResponse response = (SpeakerFlowSegmentResponse) context;
            if (flowSegmentRequests.remove(response.getCommandId())) {
                flowSegmentResponses.add(response);
            }
        } else {
            routeInternalError(String.format("Unexpected speaker response: %s", context));
        }

        continueIfRulesCommandsDone();
    }

    protected void ruleRemoved(SwitchSyncState from, SwitchSyncState to,
                               SwitchSyncEvent event, Object context) {
        log.info("Key: {}, switch rule removed", key);
        excessRulesPendingResponsesCount--;
        continueIfRulesCommandsDone();
    }

    protected void defaultRuleReinstalled(SwitchSyncState from, SwitchSyncState to,
                                          SwitchSyncEvent event, Object context) {
        log.info("Key: {}, default switch rule reinstalled", key);
        FlowReinstallResponse response = (FlowReinstallResponse) context;

        Long removedRule = response.getRemovedRule();
        if (removedRule != null && !removeDefaultRules.contains(removedRule)) {
            removeDefaultRules.add(removedRule);
        }

        reinstallDefaultRulesPendingResponsesCount--;
        continueIfRulesCommandsDone();
    }

    protected void meterRemoved(SwitchSyncState from, SwitchSyncState to,
                                SwitchSyncEvent event, Object context) {
        log.info("Key: {}, switch meter removed", key);
        excessMetersPendingResponsesCount--;
        continueIfMetersCommandsDone();
    }

    private void continueIfRulesCommandsDone() {
        if (flowSegmentRequests.isEmpty() && excessRulesPendingResponsesCount == 0) {
            handleSegmentResponses();
            fire(NEXT);
        }
    }

    private void continueIfMetersCommandsDone() {
        if (excessMetersPendingResponsesCount == 0) {
            fire(NEXT);
        }
    }

    protected void commandsProcessingFailedByTimeout(SwitchSyncState from, SwitchSyncState to,
                                                     SwitchSyncEvent event, Object context) {
        ErrorData errorData = new ErrorData(ErrorType.OPERATION_TIMED_OUT, "Commands processing failed by timeout",
                "Error when processing switch commands");
        ErrorMessage errorMessage = new ErrorMessage(errorData, System.currentTimeMillis(), key);

        log.warn(ERROR_LOG_MESSAGE, key, errorData.getErrorMessage());
        carrier.response(key, errorMessage);
    }

    protected void finished(SwitchSyncState from, SwitchSyncState to,
                            SwitchSyncEvent event, Object context) {

        // FIXME(surabujin): must be able to produce "partial" success report
        SwitchSyncResponse response = new SwitchSyncResponse(
                syncData.getValidateReport(), true, request.isRemoveExcess(), request.isProcessMeters());

        InfoMessage message = new InfoMessage(response, System.currentTimeMillis(), key);

        carrier.cancelTimeoutCallback(key);
        carrier.response(key, message);
    }

    protected void finishedWithError(SwitchSyncState from, SwitchSyncState to,
                                     SwitchSyncEvent event, Object context) {
        ErrorMessage sourceError = (ErrorMessage) context;
        ErrorMessage message = new ErrorMessage(sourceError.getData(), System.currentTimeMillis(), key);

        log.error(ERROR_LOG_MESSAGE, key, message.getData().getErrorMessage());

        carrier.cancelTimeoutCallback(key);
        carrier.response(key, message);
    }

    private void routeInternalError(Exception e) {
        routeInternalError(e.getMessage());
    }

    private void routeInternalError(String errorMessage) {
        ErrorData errorData = new SwitchSyncErrorData(
                switchId, ErrorType.INTERNAL_ERROR, errorMessage, "Error in SwitchSyncFsm");
        fire(ERROR, new ErrorMessage(errorData, System.currentTimeMillis(), key));
    }

    // -- service code --

    private void handleSegmentResponses() {
        int errorsCount = 0;
        for (SpeakerFlowSegmentResponse response : flowSegmentResponses) {
            if (response instanceof FlowErrorResponse) {
                errorsCount += 1;
                handleSegmentErrorResponse((FlowErrorResponse) response);
            } else {
                handleSegmentSuccessResponse(response);
            }
        }
        if (0 < errorsCount) {
            routeInternalError(String.format("Unable to sync %d flow segments", errorsCount));
        }
    }

    private void handleSegmentErrorResponse(FlowErrorResponse errorResponse) {
        log.error(
                "Got error response on flow segment install(restore) request from {} (flow:{}): {} {}",
                errorResponse.getSwitchId(), errorResponse.getFlowId(), errorResponse.getErrorCode(),
                errorResponse.getDescription());
    }

    private void handleSegmentSuccessResponse(SpeakerFlowSegmentResponse response) {
        log.info(
                "Got success response on flow segment install(restore) request from {} (flow:{})",
                response.getSwitchId(), response.getFlowId());
    }

    private List<RemoveFlow> buildCommandsToRemoveExcessRules() {
        ValidateSwitchReport validateReport = syncData.getValidateReport();
        return validateReport.getExcessOfFlows().stream()
                .map(this::makeOfFlowRemoveRequest)
                .collect(Collectors.toList());
    }

    private RemoveFlow makeOfFlowRemoveRequest(OfFlowReference ref) {
        // FIXME(surabujin): will never work if it require multi-table flag
        return new RemoveFlow(
                transactionIdGenerator.generate(), "SWMANAGER_BATCH_REMOVE", ref.getCookie().getValue(),
                ref.getDatapath(), null, null, false);
    }

    // -- service data types --

    public enum SwitchSyncState {
        INITIALIZED,
        COMPUTE_REMOVE_RULES,
        COMPUTE_REMOVE_METERS,
        RULES_COMMANDS_SEND,
        METERS_COMMANDS_SEND,
        FINISHED_WITH_ERROR,
        FINISHED
    }

    public enum SwitchSyncEvent {
        NEXT,
        SEGMENT_INSTALLED,
        RULES_REMOVED,
        RULES_REINSTALLED,
        RULES_SYNCHRONIZED,
        METERS_REMOVED,
        TIMEOUT,
        ERROR,
        FINISH
    }
}
