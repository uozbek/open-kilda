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

package org.openkilda.floodlight.command.flow.ingress;

import org.openkilda.floodlight.command.SpeakerCommandProcessor;
import org.openkilda.floodlight.command.flow.FlowSegmentReport;
import org.openkilda.floodlight.command.flow.ingress.of.IngressFlowSegmentRemoveMultiTableFlowModFactory;
import org.openkilda.floodlight.command.flow.ingress.of.IngressFlowSegmentRemoveSingleTableFlowModFactory;
import org.openkilda.floodlight.model.FlowSegmentMetadata;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.MeterConfig;
import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.projectfloodlight.openflow.protocol.OFFlowMod;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@SuppressWarnings("squid:MaximumInheritanceDepth")
public class IngressFlowSegmentRemoveCommand extends IngressFlowSegmentCommand {
    public IngressFlowSegmentRemoveCommand(
            @JsonProperty("message_context") MessageContext context,
            @JsonProperty("command_id") UUID commandId,
            @JsonProperty("metadata") FlowSegmentMetadata metadata,
            @JsonProperty("endpoint") FlowEndpoint endpoint,
            @JsonProperty("meter_config") MeterConfig meterConfig,
            @JsonProperty("egress_switch") SwitchId egressSwitchId,
            @JsonProperty("isl_port") int islPort,
            @JsonProperty("encapsulation") FlowTransitEncapsulation encapsulation,
            @JsonProperty("remove_customer_port_shared_catch_rule") boolean removeCustomerPortSharedCatchRule) {
        super(context, commandId, metadata, endpoint, meterConfig, egressSwitchId, islPort, encapsulation,
                removeCustomerPortSharedCatchRule);
    }

    @Override
    protected void setupFlowModFactory() {
        if (metadata.isMultiTable()) {
            setFlowModFactory(
                    new IngressFlowSegmentRemoveMultiTableFlowModFactory(this, getSw(), getSwitchFeatures()));
        } else {
            setFlowModFactory(
                    new IngressFlowSegmentRemoveSingleTableFlowModFactory(this, getSw(), getSwitchFeatures()));
        }
    }

    @Override
    protected CompletableFuture<FlowSegmentReport> makeExecutePlan(SpeakerCommandProcessor commandProcessor) {
        return makeRemovePlan(commandProcessor);
    }

    @Override
    protected List<OFFlowMod> makeIngressModMessages(MeterId effectiveMeterId) {
        List<OFFlowMod> ofMessages = super.makeIngressModMessages(effectiveMeterId);
        if (removeCustomerPortSharedCatchRule) {
            ofMessages.add(getFlowModFactory().makeCustomerPortSharedCatchMessage());
        }
        return ofMessages;
    }

    @Override
    protected SegmentAction getSegmentAction() {
        return SegmentAction.REMOVE;
    }
}
