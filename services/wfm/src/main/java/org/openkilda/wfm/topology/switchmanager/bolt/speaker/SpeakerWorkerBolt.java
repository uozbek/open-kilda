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

import org.openkilda.floodlight.api.request.FlowSegmentBlankGenericResolver;
import org.openkilda.floodlight.api.request.FlowSegmentRequest;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.Utils;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.share.hubandspoke.WorkerBolt;
import org.openkilda.wfm.topology.switchmanager.StreamType;
import org.openkilda.wfm.topology.switchmanager.bolt.speaker.command.SpeakerWorkerCommand;
import org.openkilda.wfm.topology.switchmanager.service.SpeakerWorkerCarrier;
import org.openkilda.wfm.topology.utils.MessageTranslator;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.storm.kafka.bolt.mapper.FieldNameBasedTupleToKafkaMapper;
import org.apache.storm.shade.org.eclipse.jetty.server.handler.HandlerWrapper;
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

    private transient Map<String, IHandler> handlers;

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
        String key = input.getStringByField(MessageTranslator.FIELD_ID_KEY);
        try (HandlerWrapper w = new HandlerWrapper(key)) {
            w.speakerResponse(pullValue(input, MessageTranslator.FIELD_ID_PAYLOAD, Message.class));
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
    public void sendSpeakerMessage(String key, CommandMessage message) {
        try {
            emitSpeaker(encodeSpeakerStreamPayload(message));
        } catch (JsonProcessingException e) {
            log.error("Unable to encode speaker message {}: {}", message, e.getMessage());
        }
    }

    @Override
    public void sendFlowSegmentRequest(FlowSegmentRequest request) {
        try {
            emitSpeaker(encodeSpeakerStreamPayload(request));
        } catch (JsonProcessingException e) {
            log.error("Unable to encode speaker request {}: {}", request, e.getMessage());
        }
    }

    // FIXME
    @Override
    public void sendResponse(String key, Message response) {
        Values values = new Values(key, response, getCommandContext());
        emitResponseToHub(getCurrentTuple(), values);
    }


    @Override
    public CommandContext getCommandContext() {
        return super.getCommandContext();
    }

    // -- commands processing --

    public void processProxyRequest(String key, CommandData payload) {
        installHandler(key, new ProxyRequestHandler(this, key, payload));
    }

    public void processFetchSchema(String key, List<FlowSegmentBlankGenericResolver> requests) {
        installHandler(key, new SchemaFetchHandler(this, requests));
    }

    // -- storm interface --

    @Override
    public void declareOutputFields(OutputFieldsDeclarer streamManager) {
        super.declareOutputFields(streamManager);

        streamManager.declareStream(StreamType.TO_FLOODLIGHT.toString(), STREAM_FIELDS);
    }

    // -- service code --

    private void emitSpeaker(String json) {
        String key = getCurrentTuple().getStringByField(MessageTranslator.FIELD_ID_KEY);
        Values output = makeTuple(key, json);
        emit(StreamType.TO_FLOODLIGHT.toString(), getCurrentTuple(), output);
    }

    private String encodeSpeakerStreamPayload(Message message) throws JsonProcessingException {
        return Utils.MAPPER.writeValueAsString(message);
    }

    private String encodeSpeakerStreamPayload(FlowSegmentRequest request) throws JsonProcessingException {
        return Utils.MAPPER.writeValueAsString(request);
    }

    private Values makeTuple(String key, String json) {
        return new Values(key, json, getCommandContext());
    }

    private void installHandler(String key, IHandler h) {
        IHandler p;
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
        private final IHandler h;
        private boolean forceComplete = false;

        private HandlerWrapper(String key) {
            this.key = key;
            h = handlers.getOrDefault(key, DummyHandler.INSTANCE);
        }

        void speakerResponse(Message response) {
            h.speakerResponse(response);
        }

        void timeout() {
            forceComplete = true;
            h.timeout();
        }

        public void close() {
            if (forceComplete || h.isCompleted()) {
                handlers.remove(key, h);
            }
        }
    }

    private static class DummyHandler implements IHandler {
        private final static DummyHandler INSTANCE = new DummyHandler();

        @Override
        public void speakerResponse(Message response) {
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
