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

import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.Cookie;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.UUID;

@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class TransitFlowSegmentBlankRequest extends FlowSegmentRequest
        implements IFlowSegmentBlank<TransitFlowSegmentBlankRequest> {
    @JsonProperty("ingressIslPort")
    protected final Integer ingressIslPort;

    @JsonProperty("egressIslPort")
    protected final Integer egressIslPort;

    @JsonProperty("encapsulation")
    protected final FlowTransitEncapsulation encapsulation;

    TransitFlowSegmentBlankRequest(
            MessageContext context, SwitchId switchId, UUID commandId, String flowId, Cookie cookie,
            Integer ingressIslPort, Integer egressIslPort, FlowTransitEncapsulation encapsulation) {
        super(context, switchId, commandId, flowId, cookie);

        requireNonNull(ingressIslPort, "Argument ingressIslPort must not be null");
        requireNonNull(egressIslPort, "Argument egressIslPort must not be null");
        requireNonNull(encapsulation, "Argument encapsulation must not be null");

        this.ingressIslPort = ingressIslPort;
        this.egressIslPort = egressIslPort;
        this.encapsulation = encapsulation;
    }

    TransitFlowSegmentBlankRequest(TransitFlowSegmentBlankRequest other) {
        this(
                other.messageContext, other.switchId, other.commandId, other.flowId, other.cookie,
                other.ingressIslPort, other.egressIslPort, other.encapsulation);
    }

    @Override
    public TransitFlowSegmentInstallRequest makeInstallRequest() {
        return new TransitFlowSegmentInstallRequest(this);
    }

    @Override
    public TransitFlowSegmentRemoveRequest makeRemoveRequest() {
        return new TransitFlowSegmentRemoveRequest(this);
    }

    @Override
    public TransitFlowSegmentVerifyRequest makeVerifyRequest() {
        return new TransitFlowSegmentVerifyRequest(this);
    }

    @Override
    public TransitFlowSegmentBlankRequest makeSchemaRequest() {
        return new TransitFlowSegmentSchemaRequest(this);
    }

    /**
     * Create "blank" resolver - object capable to create any "real" request type.
     */
    @Builder(builderMethodName = "buildResolver")
    public static BlankResolver makeResolver(
            MessageContext messageContext, SwitchId switchId, UUID commandId, String flowId, Cookie cookie,
            Integer ingressIslPort, Integer egressIslPort, FlowTransitEncapsulation encapsulation) {
        TransitFlowSegmentBlankRequest blank = new TransitFlowSegmentBlankRequest(
                messageContext, switchId, commandId, flowId, cookie, ingressIslPort, egressIslPort, encapsulation);
        return new BlankResolver(blank);
    }

    public static class BlankResolver
            extends FlowSegmentBlankResolver<TransitFlowSegmentBlankRequest> {
        BlankResolver(IFlowSegmentBlank<TransitFlowSegmentBlankRequest> blank) {
            super(blank);
        }
    }
}