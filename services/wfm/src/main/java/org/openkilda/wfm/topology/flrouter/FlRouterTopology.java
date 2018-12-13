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

package org.openkilda.wfm.topology.flrouter;

import org.openkilda.wfm.LaunchEnvironment;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.flrouter.bolts.FlRouterBolt;

import org.apache.storm.generated.StormTopology;
import org.apache.storm.kafka.spout.KafkaSpout;
import org.apache.storm.topology.TopologyBuilder;

/**
 * Floodlight Router topology.
 */
public class FlRouterTopology extends AbstractTopology<FlRouterTopologyConfig> {

    private static final String FLR_SPOUT_ID = "flr-spout";
    private static final String FLR_BOLT_NAME = "flr-bolt";
    private static final String FLR_KAFKA_BOLT_NAME = "flr-kafka-bolt";

    public FlRouterTopology(LaunchEnvironment env) {
        super(env, FlRouterTopologyConfig.class);
    }

    @Override
    public StormTopology createTopology() {
        logger.info("Create FlRouterTopology - {}", topologyName);

        TopologyBuilder builder = new TopologyBuilder();

        KafkaSpout kafkaSpout = createKafkaSpout(topologyConfig.getKafkaFlRouterTopic(), FLR_SPOUT_ID);
        builder.setSpout(FLR_SPOUT_ID, kafkaSpout);

        FlRouterBolt flRouterBolt = new FlRouterBolt();
        builder.setBolt(FLR_BOLT_NAME, flRouterBolt)
                .shuffleGrouping(FLR_SPOUT_ID);

        builder.setBolt(FLR_KAFKA_BOLT_NAME, createKafkaBolt(topologyConfig.getKafkaSpeakerTopic()),
                topologyConfig.getParallelism()).shuffleGrouping(FLR_BOLT_NAME);

        return builder.createTopology();
    }

    /**
     * Topology entry point.
     */
    public static void main(String[] args) {
        try {
            LaunchEnvironment env = new LaunchEnvironment(args);
            (new FlRouterTopology(env)).setup();
        } catch (Exception e) {
            System.exit(handleLaunchException(e));
        }
    }
}
