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

import org.openkilda.api.priv.notifycation.FlowNotification;
import org.openkilda.wfm.share.hubandspoke.HubBolt;
import org.openkilda.wfm.topology.flowhs.service.FlowGenericCarrier;
import org.openkilda.wfm.topology.utils.FlowNotificationKafkaTranslator;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Values;

abstract class FlowCrudHubBase extends HubBolt implements FlowGenericCarrier {
    public static final String STREAM_NOTIFICATION_ID = "notification";
    public static final Fields STREAM_NOTIFICATION_FIELDS = FlowNotificationKafkaTranslator.STREAM_FIELDS;


    public FlowCrudHubBase(Config config) {
        super(config);
    }

    @Override
    public void sendNotification(FlowNotification notification) {
        emit(STREAM_NOTIFICATION_ID, getCurrentTuple(), makeNotificationTuple(notification));
    }

    private Values makeNotificationTuple(FlowNotification notification) {
        return new Values(notification.getFlowId(), notification, getCommandContext());
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputManager) {
        super.declareOutputFields(outputManager);

        outputManager.declareStream(STREAM_NOTIFICATION_ID, STREAM_NOTIFICATION_FIELDS);
    }
}
