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
import org.apache.storm.kafka.bolt.KafkaBolt;
import org.apache.storm.kafka.spout.KafkaSpout;
import org.apache.storm.topology.TopologyBuilder;

/**
 * Floodlight Router topology.
 */
public class FlRouterTopology extends AbstractTopology<FlRouterTopologyConfig> {

    public FlRouterTopology(LaunchEnvironment env) {
        super(env, FlRouterTopologyConfig.class);
    }

    @Override
    public StormTopology createTopology() {
        logger.info("Create FlRouterTopology - {}", topologyName);

        TopologyBuilder builder = new TopologyBuilder();

        // Spout reads FLRouter speaker topic
        KafkaSpout kafkaSpeakerSpout = createKafkaSpout(topologyConfig.getKafkaFlRouterSpeakerTopic(),
                ComponentType.FLR_SPEAKER_SPOUT_ID.toString());
        builder.setSpout(ComponentType.FLR_SPEAKER_SPOUT_ID.toString(), kafkaSpeakerSpout);

        // Spout reads FLRouter speaker flow topic
        KafkaSpout kafkaSpeakerFlowSpout = createKafkaSpout(topologyConfig.getKafkaFlRouterSpeakerFlowTopic(),
                ComponentType.FLR_SPEAKER_FLOW_SPOUT_ID.toString());
        builder.setSpout(ComponentType.FLR_SPEAKER_FLOW_SPOUT_ID.toString(), kafkaSpeakerFlowSpout);

        // Floodlight Router bolt
        FlRouterBolt flRouterBolt = new FlRouterBolt();
        builder.setBolt(ComponentType.FLR_BOLT_NAME.toString(), flRouterBolt)
                .shuffleGrouping(ComponentType.FLR_SPEAKER_SPOUT_ID.toString())
                .shuffleGrouping(ComponentType.FLR_SPEAKER_FLOW_SPOUT_ID.toString());

        // Sends message to FL Speaker topic
        KafkaBolt speakerKafkaBolt = createKafkaBolt(topologyConfig.getKafkaSpeakerTopic());
        builder.setBolt(ComponentType.FL_KAFKA_BOLT.toString(), speakerKafkaBolt, topologyConfig.getParallelism())
                .shuffleGrouping(ComponentType.FLR_BOLT_NAME.toString(), StreamType.REQUEST_SPEAKER.toString());

        // Sends message to FL Speaker flow topic
        KafkaBolt speakerFlowBolt = createKafkaBolt(topologyConfig.getKafkaSpeakerFlowTopic());
        builder.setBolt(ComponentType.FL_FLOW_KAFKA_BOLT.toString(), speakerFlowBolt, topologyConfig.getParallelism())
                .shuffleGrouping(ComponentType.FLR_BOLT_NAME.toString(), StreamType.REQUEST_SPEAKER_FLOW.toString());

        // Sends responses to Northbound
        KafkaBolt nbKafkaBolt = createKafkaBolt(topologyConfig.getKafkaNorthboundTopic());
        builder.setBolt(ComponentType.NB_KAFKA_BOLT.toString(), nbKafkaBolt, topologyConfig.getParallelism())
                .shuffleGrouping(ComponentType.FLR_BOLT_NAME.toString(), StreamType.NB_RESPONSE.toString());

        // Sends responses to TPE
        KafkaBolt tpeKafkaBolt = createKafkaBolt(topologyConfig.getKafkaTopoEngTopic());
        builder.setBolt(ComponentType.TPE_KAFKA_BOLT.toString(), tpeKafkaBolt, topologyConfig.getParallelism())
                .shuffleGrouping(ComponentType.FLR_BOLT_NAME.toString(), StreamType.TPE_RESPONSE.toString());
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
