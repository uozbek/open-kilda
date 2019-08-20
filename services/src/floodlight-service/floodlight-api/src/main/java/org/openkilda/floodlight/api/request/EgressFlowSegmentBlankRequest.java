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

package org.openkilda.floodlight.api.request;

import static java.util.Objects.requireNonNull;

import org.openkilda.floodlight.api.FlowEndpoint;
import org.openkilda.floodlight.api.FlowTransitEncapsulation;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.Cookie;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.UUID;

@JsonIgnoreProperties({"switch_id"})
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class EgressFlowSegmentBlankRequest extends FlowSegmentRequest
        implements IFlowSegmentBlank<EgressFlowSegmentInstallRequest, EgressFlowSegmentRemoveRequest> {
    @JsonProperty("endpoint")
    protected final FlowEndpoint endpoint;

    @JsonProperty("ingress_endpoint")
    protected final FlowEndpoint ingressEndpoint;

    @JsonProperty("islPort")
    protected final Integer islPort;

    @JsonProperty("encapsulation")
    protected final FlowTransitEncapsulation encapsulation;

    EgressFlowSegmentBlankRequest(
            MessageContext messageContext, UUID commandId, String flowId, Cookie cookie,
            FlowEndpoint endpoint, FlowEndpoint ingressEndpoint, Integer islPort,
            FlowTransitEncapsulation encapsulation) {
        super(messageContext, endpoint.getDatapath(), commandId, flowId, cookie);

        requireNonNull(endpoint, "Argument endpoint must not be null");
        requireNonNull(ingressEndpoint, "Argument ingressEndpoint must not be null");
        requireNonNull(islPort, "Argument islPort must not be null");
        requireNonNull(encapsulation, "Argument encapsulation must not be null");

        this.endpoint = endpoint;
        this.ingressEndpoint = ingressEndpoint;
        this.islPort = islPort;
        this.encapsulation = encapsulation;
    }

    EgressFlowSegmentBlankRequest(EgressFlowSegmentBlankRequest other) {
        this(
                other.messageContext, other.commandId, other.flowId, other.cookie, other.endpoint,
                other.ingressEndpoint, other.islPort, other.encapsulation);
    }

    @Override
    public EgressFlowSegmentInstallRequest makeInstallRequest() {
        return new EgressFlowSegmentInstallRequest(this);
    }

    @Override
    public EgressFlowSegmentRemoveRequest makeRemoveRequest() {
        return new EgressFlowSegmentRemoveRequest(this);
    }

    @Builder(builderMethodName = "buildResolver")
    public static BlankResolver makeResolver(
            MessageContext messageContext, UUID commandId, String flowId, Cookie cookie,
            FlowEndpoint endpoint, FlowEndpoint ingressEndpoint, Integer islPort,
            FlowTransitEncapsulation encapsulation) {
        EgressFlowSegmentBlankRequest blank = new EgressFlowSegmentBlankRequest(
                messageContext, commandId, flowId, cookie, endpoint, ingressEndpoint, islPort, encapsulation);
        return new BlankResolver(blank);
    }

    public static class BlankResolver
            extends FlowSegmentBlankResolver<EgressFlowSegmentInstallRequest, EgressFlowSegmentRemoveRequest> {
        BlankResolver(EgressFlowSegmentBlankRequest blank) {
            super(blank);
        }
    }
}
