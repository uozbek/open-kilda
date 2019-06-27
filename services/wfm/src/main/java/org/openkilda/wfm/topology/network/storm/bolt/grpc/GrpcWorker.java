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

package org.openkilda.wfm.topology.network.storm.bolt.grpc;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.grpc.CreateLogicalPortRequest;
import org.openkilda.messaging.command.grpc.DumpLogicalPortsRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.grpc.CreateLogicalPortResponse;
import org.openkilda.messaging.info.grpc.DumpLogicalPortsResponse;
import org.openkilda.messaging.model.grpc.LogicalPortType;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.error.SwitchNotFoundException;
import org.openkilda.wfm.share.hubandspoke.WorkerBolt;
import org.openkilda.wfm.topology.network.model.LogicalPortDescriptor;
import org.openkilda.wfm.topology.network.storm.ComponentId;
import org.openkilda.wfm.topology.network.storm.bolt.GrpcEncoder;
import org.openkilda.wfm.topology.network.storm.bolt.bfdport.BfdPortHandler;
import org.openkilda.wfm.topology.network.storm.bolt.bfdport.command.BfdPortCommand;
import org.openkilda.wfm.topology.network.storm.bolt.grpc.command.GrpcWorkerCommand;
import org.openkilda.wfm.topology.utils.MessageTranslator;

import javassist.tools.Dump;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import scala.annotation.meta.field;

import java.util.Collections;
import java.util.List;

public class GrpcWorker extends WorkerBolt {
    public static final String BOLT_ID = ComponentId.GRPC_WORKER.toString();

    public static final String FIELD_ID_PAYLOAD = GrpcEncoder.FIELD_ID_PAYLOAD;
    public static final String FIELD_ID_KEY = GrpcEncoder.FIELD_ID_KEY;

    public static final String STREAM_HUB_ID = "hub";

    public static final Fields STREAM_FIELDS = new Fields(FIELD_ID_KEY, FIELD_ID_PAYLOAD, FIELD_ID_CONTEXT);

    private final PersistenceManager persistenceManager;

    private transient SwitchRepository switchRepository;

    public GrpcWorker(Config config, PersistenceManager persistenceManager) {
        super(config);

        this.persistenceManager = persistenceManager;
    }

    @Override
    protected void onHubRequest(Tuple input) throws Exception {
        GrpcWorkerCommand command = pullValue(input, BfdPortHandler.FIELD_ID_COMMAND, GrpcWorkerCommand.class);
        try {
            command.apply(this);
        } catch (SwitchNotFoundException e) {
            command.error(this, e);  // FIXME
        }
    }

    @Override
    protected void onAsyncResponse(Tuple request, Tuple response) throws Exception {
        Message message = pullValue(response, MessageTranslator.FIELD_ID_PAYLOAD, Message.class);
        if (message instanceof InfoMessage) {
            dispatchInfoMessage(((InfoMessage) message).getData());
        } else if (message instanceof ErrorMessage) {
            dispatchErrorMessage(((ErrorMessage) message).getData());
        } else {
            unhandledInput(response);
        }
    }

    private void dispatchInfoMessage(InfoData payload) {
        if (payload instanceof DumpLogicalPortsResponse) {
            handleResponse((DumpLogicalPortsResponse) payload);
        } else if (payload instanceof CreateLogicalPortResponse) {
            handleResponse((CreateLogicalPortResponse) payload);
        } else if (payload instanceof RemoveLogicalPortResponse) {
            handleResponse((RemoveLogicalPortResponse) payload);
        } else {
            unhandledInput(getCurrentTuple());
        }
    }

    private void dispatchErrorMessage(ErrorData payload) {
        handleResponse(payload);
    }

    private void handleResponse(DumpLogicalPortsResponse response) {
        // TODO
    }

    private void handleResponse(CreateLogicalPortResponse response) {
        // TODO
    }

    private void handleResponse(RemoveLogicalPortResponse response) {
        // TODO
    }

    private void handleResponse(ErrorData response) {
        // TODO
    }

    @Override
    protected void onRequestTimeout(Tuple request) throws PipelineException {
        GrpcWorkerCommand command = pullValue(request, BfdPortHandler.FIELD_ID_COMMAND, GrpcWorkerCommand.class);
        command.timeout(this);
    }

    // -- commands processing --

    public void processBfdPortCreate(String key, SwitchId switchId,
                                     LogicalPortDescriptor portDescriptor) throws SwitchNotFoundException {
        List<Integer> physicalPort = Collections.singletonList(portDescriptor.getPhysicalPortNumber());
        CreateLogicalPortRequest request = new CreateLogicalPortRequest(
                lookupSwitchAddress(switchId), physicalPort, portDescriptor.getLogicalPortNumber(),
                LogicalPortType.BFD);
        getOutput().emit(getCurrentTuple(), makeGrpcTuple(key, request));
    }

    public void processBfdPortRemove(String key, SwitchId switchId,
                                     LogicalPortDescriptor portDescriptor) throws SwitchNotFoundException {
        RemoveLogicalPortRequest request = new RemoveLogicalPortRequest(
                lookupSwitchAddress(switchId), portDescriptor.getLogicalPortNumber());
        getOutput().emit(getCurrentTuple(), makeGrpcTuple(key, request));
    }

    public void processLogicalPortList(String key, SwitchId switchId) throws SwitchNotFoundException {
        DumpLogicalPortsRequest request = new DumpLogicalPortsRequest(lookupSwitchAddress(switchId));
        getOutput().emit(getCurrentTuple(), makeGrpcTuple(key, request));
    }

    // -- setup --

    @Override
    protected void init() {
        super.init();

        switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer streamManager) {
        super.declareOutputFields(streamManager);  // it will define HUB stream
        streamManager.declare(STREAM_FIELDS);
    }

    // -- private/service methods --

    private String lookupSwitchAddress(SwitchId switchId) throws SwitchNotFoundException {
        Switch sw = switchRepository.findById(switchId)
                .orElseThrow(() -> new SwitchNotFoundException(switchId));
        return sw.getAddress();
    }

    private Values makeGrpcTuple(String key, CommandData payload) {
        return new Values(key, payload, getCommandContext());
    }

    private Values makeHubTuple(BfdPortCommand command) throws PipelineException {
        return new Values(pullKey(), command, getCommandContext());
    }
}
