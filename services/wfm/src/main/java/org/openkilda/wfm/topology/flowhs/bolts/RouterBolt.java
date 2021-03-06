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

package org.openkilda.wfm.topology.flowhs.bolts;

import static java.lang.String.format;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.ROUTER_TO_FLOW_CREATE_HUB;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.ROUTER_TO_FLOW_DELETE_HUB;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.ROUTER_TO_FLOW_REROUTE_HUB;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.ROUTER_TO_FLOW_UPDATE_HUB;
import static org.openkilda.wfm.topology.utils.KafkaRecordTranslator.FIELD_ID_KEY;
import static org.openkilda.wfm.topology.utils.KafkaRecordTranslator.FIELD_ID_PAYLOAD;

import org.openkilda.messaging.MessageData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.flow.FlowDeleteRequest;
import org.openkilda.messaging.command.flow.FlowRequest;
import org.openkilda.messaging.command.flow.FlowRerouteRequest;
import org.openkilda.wfm.AbstractBolt;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

@Slf4j
public class RouterBolt extends AbstractBolt {

    public static final String FLOW_ID_FIELD = "flow-id";
    private static final Fields STREAM_FIELDS =
            new Fields(FIELD_ID_KEY, FLOW_ID_FIELD, FIELD_ID_PAYLOAD, FIELD_ID_CONTEXT);

    @Override
    protected void handleInput(Tuple input) {
        String key = input.getStringByField(FIELD_ID_KEY);
        if (StringUtils.isBlank(key)) {
            //TODO: the key must be unique, but the correlationId comes in from outside and we can't guarantee that.
            //IMPORTANT: Storm may initiate reprocessing of the same tuple (e.g. in the case of timeout) and
            // cause creating multiple FSMs for the same tuple. This must be avoided.
            // As for now tuples are routed by the key field, and services can check FSM uniqueness.
            key = getCommandContext().getCorrelationId();
        }

        CommandMessage message = (CommandMessage) input.getValueByField(FIELD_ID_PAYLOAD);
        MessageData data = message.getData();

        if (data instanceof FlowRequest) {
            FlowRequest request = (FlowRequest) data;
            log.debug("Received request {} with key {}", request, key);
            Values values = new Values(key, request.getFlowId(), request);
            switch (request.getType()) {
                case CREATE:
                    emitWithContext(ROUTER_TO_FLOW_CREATE_HUB.name(), input, values);
                    break;
                case UPDATE:
                    emitWithContext(ROUTER_TO_FLOW_UPDATE_HUB.name(), input, values);
                    break;
                default:
                    throw new UnsupportedOperationException(format("Flow operation %s is not supported",
                            request.getType()));
            }
        } else if (data instanceof FlowRerouteRequest) {
            FlowRerouteRequest rerouteRequest = (FlowRerouteRequest) data;
            log.debug("Received a reroute request {}/{} with key {}. MessageId {}", rerouteRequest.getFlowId(),
                    rerouteRequest.getPathIds(), key, input.getMessageId());
            Values values = new Values(key, rerouteRequest.getFlowId(), data);
            emitWithContext(ROUTER_TO_FLOW_REROUTE_HUB.name(), input, values);
        } else if (data instanceof FlowDeleteRequest) {
            FlowDeleteRequest deleteRequest = (FlowDeleteRequest) data;
            log.debug("Received a delete request {} with key {}. MessageId {}", deleteRequest.getFlowId(),
                    key, input.getMessageId());
            Values values = new Values(key, deleteRequest.getFlowId(), data);
            emitWithContext(ROUTER_TO_FLOW_DELETE_HUB.name(), input, values);
        } else {
            unhandledInput(input);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declareStream(ROUTER_TO_FLOW_CREATE_HUB.name(), STREAM_FIELDS);
        declarer.declareStream(ROUTER_TO_FLOW_UPDATE_HUB.name(), STREAM_FIELDS);
        declarer.declareStream(ROUTER_TO_FLOW_REROUTE_HUB.name(), STREAM_FIELDS);
        declarer.declareStream(ROUTER_TO_FLOW_DELETE_HUB.name(), STREAM_FIELDS);
    }
}
