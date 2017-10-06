package org.bitbucket.openkilda.northbound.controller;

import static org.bitbucket.openkilda.messaging.Utils.SYSTEM_CORRELATION_ID;
import static org.bitbucket.openkilda.messaging.error.ErrorType.OPERATION_TIMED_OUT;

import org.bitbucket.openkilda.messaging.Destination;
import org.bitbucket.openkilda.messaging.Message;
import org.bitbucket.openkilda.messaging.command.CommandData;
import org.bitbucket.openkilda.messaging.command.CommandMessage;
import org.bitbucket.openkilda.messaging.command.flow.FlowCreateRequest;
import org.bitbucket.openkilda.messaging.command.flow.FlowDeleteRequest;
import org.bitbucket.openkilda.messaging.command.flow.FlowGetRequest;
import org.bitbucket.openkilda.messaging.command.flow.FlowPathRequest;
import org.bitbucket.openkilda.messaging.command.flow.FlowStatusRequest;
import org.bitbucket.openkilda.messaging.command.flow.FlowUpdateRequest;
import org.bitbucket.openkilda.messaging.command.flow.FlowsGetRequest;
import org.bitbucket.openkilda.messaging.error.ErrorData;
import org.bitbucket.openkilda.messaging.error.ErrorMessage;
import org.bitbucket.openkilda.messaging.error.ErrorType;
import org.bitbucket.openkilda.messaging.error.MessageException;
import org.bitbucket.openkilda.messaging.info.InfoMessage;
import org.bitbucket.openkilda.messaging.info.event.PathInfoData;
import org.bitbucket.openkilda.messaging.info.flow.FlowPathResponse;
import org.bitbucket.openkilda.messaging.info.flow.FlowResponse;
import org.bitbucket.openkilda.messaging.info.flow.FlowStatusResponse;
import org.bitbucket.openkilda.messaging.info.flow.FlowsResponse;
import org.bitbucket.openkilda.messaging.model.Flow;
import org.bitbucket.openkilda.messaging.payload.flow.FlowEndpointPayload;
import org.bitbucket.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.bitbucket.openkilda.messaging.payload.flow.FlowPathPayload;
import org.bitbucket.openkilda.messaging.payload.flow.FlowPayload;
import org.bitbucket.openkilda.messaging.payload.flow.FlowState;
import org.bitbucket.openkilda.northbound.messaging.MessageConsumer;
import org.bitbucket.openkilda.northbound.messaging.MessageProducer;
import org.bitbucket.openkilda.northbound.messaging.kafka.KafkaMessageConsumer;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spring component which mocks WorkFlow Manager. This instance listens kafka ingoing requests and sends back
 * appropriate kafka responses. Response type choice is based on request type.
 */
@Component
public class TestMessageMock implements MessageProducer, MessageConsumer<Object> {
    static final String FLOW_ID = "test-flow";
    static final String ERROR_FLOW_ID = "error-flow";
    static final FlowEndpointPayload flowEndpoint = new FlowEndpointPayload(FLOW_ID, 1, 1);
    static final FlowPayload flow = new FlowPayload(FLOW_ID, flowEndpoint, flowEndpoint, 10000, FLOW_ID, null);
    static final FlowIdStatusPayload flowStatus = new FlowIdStatusPayload(FLOW_ID, FlowState.IN_PROGRESS);
    static final PathInfoData path = new PathInfoData(0L, Collections.emptyList());
    static final FlowPathPayload flowPath = new FlowPathPayload(FLOW_ID, path);
    static final Flow flowModel = new Flow(FLOW_ID, 10000, FLOW_ID, FLOW_ID, 1, 1, FLOW_ID, 1, 1);
    private static final FlowResponse flowResponse = new FlowResponse(flowModel);
    private static final FlowsResponse flowsResponse = new FlowsResponse(Collections.singletonList(flowModel));
    private static final FlowPathResponse flowPathResponse = new FlowPathResponse(path);
    private static final FlowStatusResponse flowStatusResponse = new FlowStatusResponse(flowStatus);
    private static final Map<String, CommandData> messages = new ConcurrentHashMap<>();

    /**
     * Chooses response by request.
     *
     * @param data received from kafka CommandData message payload
     * @return InfoMassage to be send as response payload
     */
    private Message formatResponse(final String correlationId, final CommandData data) {
        if (data instanceof FlowCreateRequest) {
            return new InfoMessage(flowResponse, 0, correlationId, Destination.NORTHBOUND);
        } else if (data instanceof FlowDeleteRequest) {
            return new InfoMessage(flowResponse, 0, correlationId, Destination.NORTHBOUND);
        } else if (data instanceof FlowUpdateRequest) {
            return new InfoMessage(flowResponse, 0, correlationId, Destination.NORTHBOUND);
        } else if (data instanceof FlowGetRequest) {
            if (ERROR_FLOW_ID.equals(((FlowGetRequest) data).getPayload().getId())) {
                return new ErrorMessage(new ErrorData(ErrorType.NOT_FOUND, "Flow was not found", ERROR_FLOW_ID),
                        0, correlationId, Destination.NORTHBOUND);
            } else {
                return new InfoMessage(flowResponse, 0, correlationId, Destination.NORTHBOUND);
            }
        } else if (data instanceof FlowsGetRequest) {
            return new InfoMessage(flowsResponse, 0, correlationId, Destination.NORTHBOUND);
        } else if (data instanceof FlowStatusRequest) {
            return new InfoMessage(flowStatusResponse, 0, correlationId, Destination.NORTHBOUND);
        } else if (data instanceof FlowPathRequest) {
            return new InfoMessage(flowPathResponse, 0, correlationId, Destination.NORTHBOUND);
        } else {
            return null;
        }
    }

    @Override
    public Object poll(String correlationId) {
        CommandData data;

        if (messages.containsKey(correlationId)) {
            data = messages.remove(correlationId);
        } else if (messages.containsKey(SYSTEM_CORRELATION_ID)) {
            data = messages.remove(SYSTEM_CORRELATION_ID);
        } else {
            throw new MessageException(correlationId, System.currentTimeMillis(),
                    OPERATION_TIMED_OUT, KafkaMessageConsumer.TIMEOUT_ERROR_MESSAGE, "kilda-test");
        }
        return formatResponse(correlationId, data);
    }

    @Override
    public void clear() {
        messages.clear();
    }

    @Override
    public void send(String topic, Object object) {
        Message message = (Message) object;
        if (message instanceof CommandMessage) {
            messages.put(message.getCorrelationId(), ((CommandMessage) message).getData());
        }
    }
}