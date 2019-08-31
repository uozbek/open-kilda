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

import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.MeterConfig;
import org.openkilda.floodlight.api.request.IngressFlowSegmentBlankRequest;
import org.openkilda.floodlight.api.request.IngressFlowSegmentBlankRequest.BlankResolver;
import org.openkilda.floodlight.command.AbstractSpeakerCommandJsonTest;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.Cookie;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchId;

import org.junit.Assert;

import java.util.UUID;

abstract class IngressFlowSegmentBlankCommandJsonTest
        extends AbstractSpeakerCommandJsonTest<IngressFlowSegmentBlankRequest> {
    protected void verifyPayload(IngressFlowSegmentBlankRequest request, IngressFlowSegmentBlankCommand command) {
        Assert.assertEquals(request.getMessageContext(), command.getMessageContext());
        Assert.assertEquals(request.getSwitchId(), command.getSwitchId());
        Assert.assertEquals(request.getCommandId(), command.getCommandId());
        Assert.assertEquals(request.getFlowId(), command.getFlowId());
        Assert.assertEquals(request.getCookie(), command.getCookie());
        Assert.assertEquals(request.getEndpoint(), command.getEndpoint());
        Assert.assertEquals(request.getMeterConfig(), command.getMeterConfig());
        Assert.assertEquals(request.getIslPort(), command.getIslPort());
        Assert.assertEquals(request.getEncapsulation(), command.getEncapsulation());
    }

    @Override
    protected IngressFlowSegmentBlankRequest makeRequest() {
        BlankResolver blank = IngressFlowSegmentBlankRequest.makeResolver(
                new MessageContext(),
                UUID.randomUUID(),
                "ingress-flow-segment-json-remove-request",
                new Cookie(1),
                new FlowEndpoint(new SwitchId(2), 3, 4, 5),
                new MeterConfig(new MeterId(6), 7000),
                8,
                new FlowTransitEncapsulation(9, FlowEncapsulationType.TRANSIT_VLAN));
        return makeRequest(blank);
    }

    protected abstract IngressFlowSegmentBlankRequest makeRequest(BlankResolver blank);
}