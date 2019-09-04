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

package org.openkilda.wfm.topology.switchmanager.bolt.speaker;

import org.openkilda.floodlight.api.request.SpeakerRequest;
import org.openkilda.floodlight.api.response.SpeakerResponse;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.Utils;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.share.hubandspoke.WorkerBolt;
import org.openkilda.wfm.topology.switchmanager.StreamType;
import org.openkilda.wfm.topology.switchmanager.bolt.hub.command.HubCommand;
import org.openkilda.wfm.topology.switchmanager.bolt.hub.command.HubSwitchSchemaDumpCommand;
import org.openkilda.wfm.topology.switchmanager.bolt.hub.command.HubSyncMessageResponseCommand;
import org.openkilda.wfm.topology.switchmanager.bolt.hub.command.HubSyncRequestResponseCommand;
import org.openkilda.wfm.topology.switchmanager.bolt.hub.command.HubSyncWorkerErrorCommand;
import org.openkilda.wfm.topology.switchmanager.bolt.hub.command.HubValidateErrorResponseCommand;
import org.openkilda.wfm.topology.switchmanager.bolt.hub.command.HubValidateWorkerErrorCommand;
import org.openkilda.wfm.topology.switchmanager.bolt.speaker.command.SpeakerWorkerCommand;
import org.openkilda.wfm.topology.switchmanager.model.SpeakerSwitchSchema;
import org.openkilda.wfm.topology.switchmanager.model.ValidateFlowSegmentDescriptor;
import org.openkilda.wfm.topology.switchmanager.service.SpeakerWorkerCarrier;
import org.openkilda.wfm.topology.utils.MessageTranslator;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.storm.kafka.bolt.mapper.FieldNameBasedTupleToKafkaMapper;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.io.Closeable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpeakerWorkerBolt extends WorkerBolt implements SpeakerWorkerCarrier {
    public static final String ID = "speaker.worker.bolt";
    public static final String INCOME_STREAM = "speaker.worker.stream";

    public static final Fields STREAM_FIELDS = new Fields(
            FieldNameBasedTupleToKafkaMapper.BOLT_KEY, FieldNameBasedTupleToKafkaMapper.BOLT_MESSAGE,
            FIELD_ID_CONTEXT);

    private transient Map<String, WorkerHandler> handlers;

    public SpeakerWorkerBolt(Config config) {
        super(config);
    }

    @Override
    protected void init() {
        handlers = new HashMap<>();
    }

    @Override
    protected void onHubRequest(Tuple input) throws PipelineException {
        SpeakerWorkerCommand command = pullValue(input, MessageTranslator.FIELD_ID_PAYLOAD, SpeakerWorkerCommand.class);
        command.apply(this);
    }

    @Override
    protected void onAsyncResponse(Tuple input) throws PipelineException {
        String key = pullValue(input, MessageTranslator.FIELD_ID_KEY, String.class);
        Object raw = pullValue(input, MessageTranslator.FIELD_ID_PAYLOAD, Object.class);

        try (HandlerWrapper w = new HandlerWrapper(key)) {
            if (raw instanceof Message) {
                w.speakerResponse((Message) raw);
            } else if (raw instanceof SpeakerResponse) {
                w.speakerResponse((SpeakerResponse) raw);
            } else {
                unhandledInput(input);
            }
        }
    }

    @Override
    public void onTimeout(String key, Tuple tuple) {
        try (HandlerWrapper w = new HandlerWrapper(key)) {
            w.timeout();
        }
    }

    // -- carrier implementation --

    @Override
    public void sendSpeakerCommand(CommandData messagePayload) {
        CommandMessage message = new CommandMessage(messagePayload, System.currentTimeMillis(), getKey());
        try {
            emitSpeaker(encodeSpeakerStreamPayload(message));
        } catch (JsonProcessingException e) {
            log.error("Unable to encode speaker message {}: {}", message, e.getMessage());
        }
    }

    @Override
    public void sendSpeakerCommand(SpeakerRequest request) {
        try {
            emitSpeaker(encodeSpeakerStreamPayload(request));
        } catch (JsonProcessingException e) {
            log.error("Unable to encode speaker request {}: {}", request, e.getMessage());
        }
    }

    @Override
    public void sendHubValidationWorkerError(String errorMessage) {
        HubValidateWorkerErrorCommand command = new HubValidateWorkerErrorCommand(getKey(), errorMessage);
        emitResponseToHub(getCurrentTuple(), makeHubTuple(command));
    }

    @Override
    public void sendHubValidationError(String errorMessage) {
        HubValidateErrorResponseCommand command = new HubValidateErrorResponseCommand(getKey(), errorMessage);
        emitResponseToHub(getCurrentTuple(), makeHubTuple(command));
    }

    @Override
    public void sendHubSyncError(String hubKey, String errorMessage) {
        HubSyncWorkerErrorCommand command = new HubSyncWorkerErrorCommand(hubKey, errorMessage);
        emitResponseToHub(getCurrentTuple(), makeHubTuple(command));
    }

    @Override
    public void sendHubSyncResponse(String hubKey, Message response) {
        HubSyncMessageResponseCommand command = new HubSyncMessageResponseCommand(hubKey, response);
        emitResponseToHub(getCurrentTuple(), makeHubTuple(command));
    }

    @Override
    public void sendHubSyncResponse(String hubKey, SpeakerResponse response) {
        HubSyncRequestResponseCommand command = new HubSyncRequestResponseCommand(hubKey, response);
        emitResponseToHub(getCurrentTuple(), makeHubTuple(command));
    }

    @Override
    public void sendHubSwitchSchema(SpeakerSwitchSchema switchSchema) {
        HubSwitchSchemaDumpCommand command = new HubSwitchSchemaDumpCommand(getKey(), switchSchema);
        emitResponseToHub(getCurrentTuple(), makeHubTuple(command));
    }

    @Override
    public CommandContext getCommandContext() {
        return super.getCommandContext();
    }

    // -- commands processing --

    public void processFetchSchema(
            String key, SwitchId switchId, List<ValidateFlowSegmentDescriptor> segmentDescriptors) {
        installHandler(key, new SwitchSchemaFetchHandler(this, switchId, segmentDescriptors));
    }

    public void processSyncMessageRequest(String hubKey, CommandData messagePayload) {
        installHandler(getKey(), new SyncSpeakerMessageHandler(this, hubKey, messagePayload));
    }

    public void processSyncSpeakerRequest(String hubKey, SpeakerRequest request) {
        installHandler(getKey(), new SyncSpeakerRequestHandler(this, hubKey, request));
    }

    // -- storm interface --

    @Override
    public void declareOutputFields(OutputFieldsDeclarer streamManager) {
        super.declareOutputFields(streamManager);

        streamManager.declareStream(StreamType.TO_FLOODLIGHT.toString(), STREAM_FIELDS);
    }

    // -- service code --

    private void emitSpeaker(String json) {
        Values output = makeSpeakerTuple(getKey(), json);
        emit(StreamType.TO_FLOODLIGHT.toString(), getCurrentTuple(), output);
    }

    private String encodeSpeakerStreamPayload(Message message) throws JsonProcessingException {
        return Utils.MAPPER.writeValueAsString(message);
    }

    private String encodeSpeakerStreamPayload(SpeakerRequest request) throws JsonProcessingException {
        return Utils.MAPPER.writeValueAsString(request);
    }

    private Values makeHubTuple(HubCommand command) {
        return new Values(command.getKey(), command, getCommandContext());
    }

    private Values makeSpeakerTuple(String key, String json) {
        return new Values(key, json, getCommandContext());
    }

    private String getKey() {
        return getCurrentTuple().getStringByField(MessageTranslator.FIELD_ID_KEY);
    }

    private void installHandler(String key, WorkerHandler h) {
        WorkerHandler p;
        if (! h.isCompleted()) {
            p = handlers.put(key, h);
        } else {
            p = handlers.remove(key);
        }

        if (p != null) {
            p.replaced();
        }
    }

    private class HandlerWrapper implements Closeable {
        private final String key;
        private final WorkerHandler target;
        private boolean forceComplete = false;

        HandlerWrapper(String key) {
            this.key = key;
            target = handlers.getOrDefault(key, DummyHandler.INSTANCE);
        }

        void speakerResponse(Message response) {
            target.speakerResponse(response);
        }

        void speakerResponse(SpeakerResponse response) {
            target.speakerResponse(response);
        }

        void timeout() {
            forceComplete = true;
            target.timeout();
        }

        public void close() {
            if (forceComplete || target.isCompleted()) {
                handlers.remove(key, target);
            }
        }
    }

    private static class DummyHandler extends WorkerHandler {
        private static final DummyHandler INSTANCE = new DummyHandler();

        @Override
        public void speakerResponse(Message response) {
            // dummy handler
        }

        @Override
        public void speakerResponse(SpeakerResponse response) {
            // dummy handler
        }

        @Override
        public void timeout() {
            // dummy handler
        }

        @Override
        public void replaced() {
            // dummy handler
        }

        @Override
        public boolean isCompleted() {
            return false;
        }
    }
}
