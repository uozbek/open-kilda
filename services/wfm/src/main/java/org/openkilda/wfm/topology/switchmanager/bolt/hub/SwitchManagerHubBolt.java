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

package org.openkilda.wfm.topology.switchmanager.bolt.hub;

import org.openkilda.floodlight.api.request.FlowSegmentRequest;
import org.openkilda.floodlight.api.request.SpeakerRequest;
import org.openkilda.floodlight.api.response.SpeakerResponse;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.switches.SwitchValidateRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.flow.FlowRemoveResponse;
import org.openkilda.messaging.info.switches.DeleteMeterResponse;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.share.flow.resources.FlowResourcesConfig;
import org.openkilda.wfm.share.hubandspoke.HubBolt;
import org.openkilda.wfm.topology.switchmanager.StreamType;
import org.openkilda.wfm.topology.switchmanager.bolt.hub.command.HubCommand;
import org.openkilda.wfm.topology.switchmanager.bolt.speaker.SpeakerWorkerBolt;
import org.openkilda.wfm.topology.switchmanager.bolt.speaker.command.SpeakerSwitchSchemaDumpCommand;
import org.openkilda.wfm.topology.switchmanager.bolt.speaker.command.SpeakerSyncMessageCommand;
import org.openkilda.wfm.topology.switchmanager.bolt.speaker.command.SpeakerSyncRequestCommand;
import org.openkilda.wfm.topology.switchmanager.bolt.speaker.command.SpeakerWorkerCommand;
import org.openkilda.wfm.topology.switchmanager.model.SpeakerSwitchSchema;
import org.openkilda.wfm.topology.switchmanager.model.SwitchSyncData;
import org.openkilda.wfm.topology.switchmanager.model.ValidateFlowSegmentDescriptor;
import org.openkilda.wfm.topology.switchmanager.service.SwitchManagerCarrier;
import org.openkilda.wfm.topology.switchmanager.service.SwitchSyncService;
import org.openkilda.wfm.topology.switchmanager.service.SwitchValidateService;
import org.openkilda.wfm.topology.switchmanager.service.impl.SwitchSyncServiceImpl;
import org.openkilda.wfm.topology.switchmanager.service.impl.SwitchValidateServiceImpl;
import org.openkilda.wfm.topology.utils.MessageTranslator;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.List;
import java.util.Map;

public class SwitchManagerHubBolt extends HubBolt implements SwitchManagerCarrier {
    public static final String ID = "switch.manager.hub";
    public static final String INCOME_STREAM = "switch.manage.command";

    private transient NoArgGenerator keyChunksGenerator;

    private final PersistenceManager persistenceManager;
    private final FlowResourcesConfig flowResourcesConfig;
    private transient SwitchValidateService validateService;
    private transient SwitchSyncService syncService;

    public SwitchManagerHubBolt(
            Config hubConfig, PersistenceManager persistenceManager, FlowResourcesConfig flowResourcesConfig) {
        super(hubConfig);
        this.persistenceManager = persistenceManager;
        this.flowResourcesConfig = flowResourcesConfig;
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        super.prepare(stormConf, context, collector);

        keyChunksGenerator = Generators.timeBasedGenerator();

        validateService = new SwitchValidateServiceImpl(this, flowResourcesConfig, persistenceManager);
        syncService = new SwitchSyncServiceImpl(this);
    }

    @Override
    protected void onRequest(Tuple input) throws PipelineException {
        String key = input.getStringByField(MessageTranslator.FIELD_ID_KEY);
        CommandMessage message = pullValue(input, MessageTranslator.FIELD_ID_PAYLOAD, CommandMessage.class);

        CommandData data = message.getData();
        if (data instanceof SwitchValidateRequest) {
            validateService.handleSwitchValidateRequest(key, (SwitchValidateRequest) data);
        } else {
            log.warn("Receive unexpected CommandMessage for key {}: {}", key, data);
        }
    }

    @Override
    protected void onWorkerResponse(Tuple input) throws PipelineException {
        HubCommand command = pullValue(input, MessageTranslator.FIELD_ID_PAYLOAD, HubCommand.class);
        command.apply(this);
    }

    @Override
    public void onTimeout(String key, Tuple tuple) {
        log.warn("Receive TaskTimeout for key {}", key);
        validateService.handleGlobalTimeout(key);
        syncService.handleTaskTimeout(key);
    }

    @Override
    public void cancelTimeoutCallback(String key) {
        cancelCallback(key, getCurrentTuple());
    }

    @Override
    public void response(String key, Message message) {
        getOutput().emit(StreamType.TO_NORTHBOUND.toString(), new Values(key, message));
    }

    // -- commands processing --

    public void processSwitchSchemaDump(String key, SpeakerSwitchSchema switchSchema) {
        validateService.handleSwitchSchema(key, switchSchema);
    }

    public void processValidateWorkerError(String key, String errorMessage) {
        validateService.handleWorkerError(key, errorMessage);
    }

    public void processValidateErrorResponse(String key, String errorResponse) {
        validateService.handleSpeakerErrorResponse(key, errorResponse);
    }

    /**
     * Route worker response on sync command.
     */
    public void processSyncResponse(Message message) {
        String key = getKey();
        if (message instanceof InfoMessage) {
            InfoData data = ((InfoMessage) message).getData();
            if (data instanceof FlowRemoveResponse) {
                syncService.handleRemoveRulesResponse(key);
            } else if (data instanceof DeleteMeterResponse) {
                syncService.handleRemoveMetersResponse(key);
            } else {
                log.warn("Receive unexpected InfoData for key {}: {}", key, data);
            }
        } else if (message instanceof ErrorMessage) {
            log.warn("Receive ErrorMessage for key {}", key);
            syncService.handleTaskError(key, (ErrorMessage) message);
        }
    }

    public void processSyncResponse(SpeakerResponse response) {
        syncService.handleSegmentInstallResponse(getKey(), response);
    }

    /**
     * Route worker error response on sync command.
     */
    public void processSyncWorkerError(String key, String errorMessage) {
        ErrorData errorPayload = new ErrorData(ErrorType.INTERNAL_ERROR, "Speaker worker report error", errorMessage);
        syncService.handleTaskError(key, new ErrorMessage(
                errorPayload, System.currentTimeMillis(), getCommandContext().getCorrelationId()));
    }

    // -- carrier implementation --

    @Override
    public void speakerFetchSchema(SwitchId switchId, List<ValidateFlowSegmentDescriptor> segmentDescriptors) {
        SpeakerSwitchSchemaDumpCommand command = new SpeakerSwitchSchemaDumpCommand(
                getKey(), switchId, segmentDescriptors);
        emit(SpeakerWorkerBolt.INCOME_STREAM, getCurrentTuple(), makeSpeakerWorkerTuple(command));
    }

    @Override
    public void syncSpeakerMessageRequest(CommandData payload) {
        String hubKey = getKey();
        SpeakerSyncMessageCommand command = new SpeakerSyncMessageCommand(
                makeWorkerKey(hubKey, payload), hubKey, payload);
        emit(SpeakerWorkerBolt.INCOME_STREAM, getCurrentTuple(), makeSpeakerWorkerTuple(command));
    }

    @Override
    public void syncSpeakerFlowSegmentRequest(FlowSegmentRequest segmentRequest) {
        String hubKey = getKey();
        SpeakerSyncRequestCommand command = new SpeakerSyncRequestCommand(
                makeWorkerKey(hubKey, segmentRequest), hubKey, segmentRequest);
        emit(SpeakerWorkerBolt.INCOME_STREAM, getCurrentTuple(), makeSpeakerWorkerTuple(command));
    }

    @Override
    public void runSwitchSync(String key, SwitchValidateRequest request, SwitchSyncData syncData) {
        syncService.handleSwitchSync(key, request, syncData);
    }

    @Override
    public CommandContext getCommandContext() {
        return super.getCommandContext();
    }

    // -- storm interface --

    private Values makeSpeakerWorkerTuple(SpeakerWorkerCommand command) {
        return new Values(command.getKey(), command, getCommandContext());
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        super.declareOutputFields(declarer);
        declarer.declareStream(SpeakerWorkerBolt.INCOME_STREAM, MessageTranslator.STREAM_FIELDS);

        Fields fields = new Fields(MessageTranslator.FIELD_ID_KEY, MessageTranslator.FIELD_ID_PAYLOAD);
        declarer.declareStream(StreamType.TO_NORTHBOUND.toString(), fields);
    }

    // -- service code --

    private String makeWorkerKey(String hubKey, SpeakerRequest request) {
        return hubKey + " : " + request.getCommandId();
    }

    private String makeWorkerKey(String hubKey, CommandData payload) {
        return hubKey + " : " + keyChunksGenerator.generate();
    }

    private String getKey() {
        return getCurrentTuple().getStringByField(MessageTranslator.FIELD_ID_KEY);
    }
}
