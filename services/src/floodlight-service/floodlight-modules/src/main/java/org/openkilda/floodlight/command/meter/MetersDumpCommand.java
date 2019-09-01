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

package org.openkilda.floodlight.command.meter;

import org.openkilda.model.of.MeterSchema;
import org.openkilda.floodlight.command.SpeakerCommandProcessor;
import org.openkilda.floodlight.converter.MeterSchemaMapper;
import org.openkilda.floodlight.utils.CompletableFutureAdapter;
import org.openkilda.messaging.MessageContext;
import org.openkilda.messaging.model.SpeakerSwitchView.Feature;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.Getter;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFMeterConfig;
import org.projectfloodlight.openflow.protocol.OFMeterConfigStatsReply;
import org.projectfloodlight.openflow.protocol.OFMeterConfigStatsRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Getter
public class MetersDumpCommand extends MeterCommand<MetersDumpReport> {
    @JsonCreator
    public MetersDumpCommand(
            @JsonProperty("message_context") MessageContext messageContext,
            @JsonProperty("switch_id") SwitchId switchId,
            @JsonProperty("command_id") UUID commandId) {
        super(messageContext, switchId, commandId);
    }

    @Override
    protected CompletableFuture<MetersDumpReport> makeExecutePlan(SpeakerCommandProcessor commandProcessor) {
        ListenableFuture<List<OFMeterConfigStatsReply>> request = getSw().writeStatsRequest(
                makeOfMeterConfigStatsRequest());
        return new CompletableFutureAdapter<>(messageContext, request)
                .thenApply(this::handleMetersDump)
                .thenApply(this::makeSuccessReport);
    }

    private List<MeterSchema> handleMetersDump(List<OFMeterConfigStatsReply> replyChain) {
        List<MeterSchema> meters = new ArrayList<>();
        boolean isInaccurate = getSwitchFeatures().contains(Feature.INACCURATE_METER);
        for (OFMeterConfigStatsReply reply : replyChain) {
            for (OFMeterConfig entry : reply.getEntries()) {
                meters.add(MeterSchemaMapper.INSTANCE.map(getSw().getId(), entry, isInaccurate));
            }
        }
        return meters;
    }

    private MetersDumpReport makeSuccessReport(List<MeterSchema> meters) {
        return new MetersDumpReport(this, meters);
    }

    private OFMeterConfigStatsRequest makeOfMeterConfigStatsRequest() {
        OFFactory of = getSw().getOFFactory();
        return of.buildMeterConfigStatsRequest()
                .build();
    }

    @Override
    protected MetersDumpReport makeReport(Exception error) {
        return new MetersDumpReport(this, error);
    }
}
