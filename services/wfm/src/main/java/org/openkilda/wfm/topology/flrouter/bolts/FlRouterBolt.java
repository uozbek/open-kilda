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
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.topology.AbstractTopology;

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
        }

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
        outputFieldsDeclarer.declare(AbstractTopology.fieldMessage);
    }

    /**
     * Process request to destination FL instance.
     *
     * @param message a command message.
     */
    private void processRequest(Tuple input, Message message) {
        message.setDestination(Destination.CONTROLLER);
        CommandMessage command = (CommandMessage) message;
        try {
            collector.emit(input, new Values(MAPPER.writeValueAsString(command)));
        } catch (JsonProcessingException e) {
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
