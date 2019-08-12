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

package org.openkilda.floodlight.command.flow;

import org.openkilda.floodlight.api.FlowEndpoint;
import org.openkilda.floodlight.api.FlowSegmentOperation;
import org.openkilda.floodlight.api.MeterConfig;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.Cookie;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public abstract class AbstractIngressFlowSegmentCommand extends AbstractFlowSegmentCommand {
    // payload
    protected final FlowEndpoint endpoint;

    @JsonProperty("meter_config")
    protected final MeterConfig meterConfig;

    public AbstractIngressFlowSegmentCommand(
            MessageContext messageContext, SwitchId switchId, FlowSegmentOperation operation, UUID commandId,
            String flowId, Cookie cookie, FlowEndpoint endpoint, MeterConfig meterConfig) {
        super(messageContext, switchId, operation, commandId, flowId, cookie);
        this.endpoint = endpoint;
        this.meterConfig = meterConfig;
    }
}
