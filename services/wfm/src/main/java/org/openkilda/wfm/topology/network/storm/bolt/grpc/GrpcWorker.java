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

import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.grpc.DumpLogicalPortsRequest;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.error.SwitchNotFoundException;
import org.openkilda.wfm.share.hubandspoke.WorkerBolt;
import org.openkilda.wfm.topology.network.storm.ComponentId;
import org.openkilda.wfm.topology.network.storm.bolt.GrpcEncoder;
import org.openkilda.wfm.topology.network.storm.bolt.bfdport.BfdPortHandler;
import org.openkilda.wfm.topology.network.storm.bolt.grpc.command.GrpcWorkerCommand;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

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
        handleCommand(input, BfdPortHandler.FIELD_ID_COMMAND);
    }

    @Override
    protected void onAsyncResponse(Tuple input) throws Exception {
        // TODO
    }

    @Override
    public void onTimeout(String key, Tuple tuple) throws PipelineException {
        // TODO
    }

    // -- commands processing --

    public void processBfdPortCreate() {}

    public void processBfdPortRemove() {}

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

    private void handleCommand(Tuple input, String field) throws Exception {
        GrpcWorkerCommand command = pullValue(input, field, GrpcWorkerCommand.class);
        try {
            command.apply(this);
        } catch (SwitchNotFoundException e) {
            command.error(this, e);
        }
    }

    private String lookupSwitchAddress(SwitchId switchId) throws SwitchNotFoundException {
        Switch sw = switchRepository.findById(switchId)
                .orElseThrow(() -> new SwitchNotFoundException(switchId));
        return sw.getAddress();
    }

    private Values makeGrpcTuple(String key, CommandData payload) {
        return new Values(key, payload, getCommandContext());
    }
}
