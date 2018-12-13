/* Copyright 2018 Telstra Open Source
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

package org.openkilda.wfm.topology.flrouter.bolts;

import static org.openkilda.messaging.Utils.MAPPER;

import org.openkilda.messaging.Destination;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.Utils;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.flrouter.StreamType;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.storm.state.KeyValueState;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseStatefulBolt;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Floodlight Router bolt.
 */
public class FlRouterBolt extends BaseStatefulBolt<KeyValueState<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(FlRouterBolt.class);
    private static final String FL_INSTANCE_DATA = "FL_INSTANCE_DATA";

    private Map<String, Set<SwitchId>> flInstanceData;
    private OutputCollector collector;

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(Tuple tuple) {
        String request = tuple.getString(0);

        Message message;
        try {
            message = Utils.MAPPER.readValue(request, Message.class);
        } catch (IOException e) {
            logger.error("Error during parsing request for FLRouter topology", e);
            return;
        }

        if (message instanceof CommandMessage) {
            processRequest(tuple, message);
        } else if (message instanceof InfoMessage) {
            processResponse(tuple, message);
        } else if (message instanceof ErrorMessage) {
            processErrorResponse(tuple, message);
        }
        // todo: implement unhandled input logic
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    public void initState(KeyValueState<String, Object> entries) {
        flInstanceData = (Map<String, Set<SwitchId>>) entries.get(FL_INSTANCE_DATA);
        if (flInstanceData == null) {
            flInstanceData = new HashMap<>();
            entries.put(FL_INSTANCE_DATA, flInstanceData);
        }
    }

    /**
     * Get a Floodlight instance by switch id.
     *
     * @param switchId a switch id.
     * @return a fl instance id.
     */
    private String getFlInstanceBySwitchId(SwitchId switchId) {
        for (Entry<String, Set<SwitchId>> entry : flInstanceData.entrySet()) {
            for (SwitchId id : entry.getValue()) {
                if (switchId.equals(id)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        outputFieldsDeclarer.declareStream(StreamType.REQUEST.toString(), AbstractTopology.fieldMessage);
        outputFieldsDeclarer.declareStream(StreamType.RESPONSE.toString(), AbstractTopology.fieldMessage);
        outputFieldsDeclarer.declareStream(StreamType.ERROR.toString(), AbstractTopology.fieldMessage);
    }

    /**
     * Process request to destination FL instance.
     *
     * @param input a tuple.
     * @param message a command message.
     */
    private void processRequest(Tuple input, Message message) {
        message.setDestination(Destination.CONTROLLER);
        CommandMessage command = (CommandMessage) message;
        try {
            collector.emit(StreamType.REQUEST.toString(), input, new Values(MAPPER.writeValueAsString(command)));
        } catch (JsonProcessingException e) {
            // todo: resolve catch cause
            logger.error("JSON processing error: {}", e);
        } finally {
            collector.ack(input);
        }
    }

    /**
     * Process response from Floodlight.
     *
     * @param input a tuple.
     * @param message a response message.
     */
    private void processResponse(Tuple input, Message message) {
        InfoMessage infoMessage = (InfoMessage) message;
        try {
            collector.emit(StreamType.RESPONSE.toString(), input, new Values(MAPPER.writeValueAsString(infoMessage)));
        } catch (JsonProcessingException e) {
            // todo: resolve catch cause
            logger.error("JSON processing error: {}", e);
        } finally {
            collector.ack(input);
        }
    }

    /**
     * Process error response from floodlight.
     *
     * @param input a tuple.
     * @param message an error message.
     */
    private void processErrorResponse(Tuple input, Message message) {
        ErrorMessage errorMessage = (ErrorMessage) message;
        try {
            collector.emit(StreamType.ERROR.toString(), input, new Values(MAPPER.writeValueAsString(errorMessage)));
        } catch (JsonProcessingException e) {
            // todo: resolve catch cause
            logger.error("JSON processing error: {}", e);
        } finally {
            collector.ack(input);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        this.collector = collector;
    }
}
