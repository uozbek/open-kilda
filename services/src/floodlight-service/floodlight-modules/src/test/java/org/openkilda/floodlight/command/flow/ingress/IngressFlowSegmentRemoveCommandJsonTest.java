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

import org.openkilda.floodlight.api.FlowEndpoint;
import org.openkilda.floodlight.api.FlowTransitEncapsulation;
import org.openkilda.floodlight.api.MeterConfig;
import org.openkilda.floodlight.api.request.IngressFlowSegmentBlankRequest;
import org.openkilda.floodlight.api.request.IngressFlowSegmentRemoveRequest;
import org.openkilda.floodlight.command.SpeakerCommand;
import org.openkilda.floodlight.command.SpeakerCommandReport;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.Cookie;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchId;

import org.junit.Assert;

import java.util.UUID;

public class IngressFlowSegmentRemoveCommandJsonTest extends IngressFlowSegmentBlankCommandJsonTest {
    @Override
    protected void verify(IngressFlowSegmentBlankRequest request,
                          SpeakerCommand<? extends SpeakerCommandReport> rawCommand) {
        Assert.assertTrue(rawCommand instanceof IngressFlowSegmentRemoveCommand);
        verifyPayload(request, (IngressFlowSegmentBlankCommand) rawCommand);
    }

    @Override
    protected IngressFlowSegmentRemoveRequest makeRequest() {
        SwitchId swId = new SwitchId(1);
        return new IngressFlowSegmentRemoveRequest(
                new MessageContext(),
                swId,
                UUID.randomUUID(),
                "ingress-flow-segment-json-remove-request",
                new Cookie(2),
                new FlowEndpoint(swId, 1, 2, 3),
                new MeterConfig(new MeterId(4), 5000),
                6,
                new FlowTransitEncapsulation(7, FlowEncapsulationType.TRANSIT_VLAN));
    }
}
