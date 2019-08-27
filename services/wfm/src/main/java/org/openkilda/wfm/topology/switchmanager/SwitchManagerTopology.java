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

package org.openkilda.wfm.topology.switchmanager;

import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.spi.PersistenceProvider;
import org.openkilda.wfm.LaunchEnvironment;
import org.openkilda.wfm.share.flow.resources.FlowResourcesConfig;
import org.openkilda.wfm.share.hubandspoke.CoordinatorBolt;
import org.openkilda.wfm.share.hubandspoke.CoordinatorSpout;
import org.openkilda.wfm.share.hubandspoke.WorkerBolt;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.switchmanager.bolt.speaker.SpeakerWorkerBolt;
import org.openkilda.wfm.topology.switchmanager.bolt.hub.HubBolt;
import org.openkilda.wfm.topology.utils.MessageTranslator;

import org.apache.storm.generated.StormTopology;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.tuple.Fields;

import java.util.concurrent.TimeUnit;

public class SwitchManagerTopology extends AbstractTopology<SwitchManagerTopologyConfig> {

    private static final String HUB_SPOUT = "hub.spout";
    private static final String WORKER_SPOUT = "worker.spout";
    private static final String NB_KAFKA_BOLT = "nb.bolt";
    private static final String SPEAKER_KAFKA_BOLT = "speaker.bolt";

    private static final Fields FIELDS_KEY = new Fields(MessageTranslator.FIELD_ID_KEY);

    public SwitchManagerTopology(LaunchEnvironment env) {
        super(env, SwitchManagerTopologyConfig.class);
    }

    @Override
    public StormTopology createTopology() {
        logger.info("Creating SwitchManagerTopology - {}", topologyName);

        TopologyBuilder builder = new TopologyBuilder();

        builder.setSpout(CoordinatorSpout.ID, new CoordinatorSpout());
        builder.setBolt(CoordinatorBolt.ID, new CoordinatorBolt())
                .allGrouping(CoordinatorSpout.ID)
                .fieldsGrouping(HubBolt.ID, CoordinatorBolt.INCOME_STREAM, FIELDS_KEY);

        PersistenceManager persistenceManager =
                PersistenceProvider.getInstance().createPersistenceManager(configurationProvider);

        org.openkilda.wfm.share.hubandspoke.HubBolt.Config hubConfig = org.openkilda.wfm.share.hubandspoke.HubBolt.Config.builder()
                .requestSenderComponent(HUB_SPOUT)
                .workerComponent(SpeakerWorkerBolt.ID)
                .timeoutMs((int) TimeUnit.SECONDS.toMillis(topologyConfig.getProcessTimeout()))
                .build();
        builder.setSpout(HUB_SPOUT, buildKafkaSpout(topologyConfig.getKafkaSwitchManagerNbTopic(), HUB_SPOUT));
        builder.setBolt(HubBolt.ID, new HubBolt(hubConfig, persistenceManager,
                                                topologyConfig.getFlowMeterMinBurstSizeInKbits(), topologyConfig.getFlowMeterBurstCoefficient(),
                                                configurationProvider.getConfiguration(FlowResourcesConfig.class)),
                        topologyConfig.getNewParallelism())
                .fieldsGrouping(HUB_SPOUT, FIELDS_KEY)
                .directGrouping(SpeakerWorkerBolt.ID, HubBolt.INCOME_STREAM)
                .directGrouping(CoordinatorBolt.ID);

        WorkerBolt.Config speakerWorkerConfig = WorkerBolt.Config.builder()
                .hubComponent(HubBolt.ID)
                .streamToHub(HubBolt.INCOME_STREAM)
                .workerSpoutComponent(WORKER_SPOUT)
                .defaultTimeout((int) TimeUnit.SECONDS.toMillis(topologyConfig.getOperationTimeout()))
                .build();
        builder.setSpout(WORKER_SPOUT, buildKafkaSpout(topologyConfig.getKafkaSwitchManagerTopic(), WORKER_SPOUT));
        builder.setBolt(SpeakerWorkerBolt.ID, new SpeakerWorkerBolt(speakerWorkerConfig),
                topologyConfig.getNewParallelism())
                .fieldsGrouping(WORKER_SPOUT, FIELDS_KEY)
                .fieldsGrouping(HubBolt.ID, SpeakerWorkerBolt.INCOME_STREAM, FIELDS_KEY)
                .directGrouping(CoordinatorBolt.ID);

        builder.setBolt(NB_KAFKA_BOLT, buildKafkaBolt(topologyConfig.getKafkaNorthboundTopic()))
                .shuffleGrouping(HubBolt.ID, StreamType.TO_NORTHBOUND.toString());

        builder.setBolt(SPEAKER_KAFKA_BOLT, buildKafkaJsonBolt(topologyConfig.getKafkaSpeakerTopic()))
                .shuffleGrouping(SpeakerWorkerBolt.ID, StreamType.TO_FLOODLIGHT.toString());

        return builder.createTopology();
    }

    /**
     * Launches and sets up the workflow manager environment.
     *
     * @param args the command-line arguments.
     */
    public static void main(String[] args) {
        try {
            LaunchEnvironment env = new LaunchEnvironment(args);
            new SwitchManagerTopology(env).setup();
        } catch (Exception e) {
            System.exit(handleLaunchException(e));
        }
    }
}
