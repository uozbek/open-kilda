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

package org.openkilda.floodlight.command.flow.egress;

import org.openkilda.floodlight.api.FlowEndpoint;
import org.openkilda.floodlight.api.FlowTransitEncapsulation;
import org.openkilda.floodlight.api.request.EgressFlowSegmentBlankRequest;
import org.openkilda.floodlight.api.request.EgressFlowSegmentInstallRequest;
import org.openkilda.floodlight.command.SpeakerCommand;
import org.openkilda.floodlight.command.SpeakerCommandReport;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.Cookie;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.SwitchId;

import org.junit.Assert;

import java.util.UUID;

public class EgressFlowSegmentInstallCommandJsonTest extends EgressFlowSegmentBlankCommandJsonTest {
    @Override
    protected void verify(EgressFlowSegmentBlankRequest request,
                          SpeakerCommand<? extends SpeakerCommandReport> rawCommand) {
        Assert.assertTrue(rawCommand instanceof EgressFlowSegmentInstallCommand);
        verifyPayload(request, (EgressFlowSegmentInstallCommand) rawCommand);
    }

    @Override
    protected EgressFlowSegmentBlankRequest makeRequest() {
        SwitchId swId = new SwitchId(1);
        return new EgressFlowSegmentInstallRequest(
                new MessageContext(),
                UUID.randomUUID(),
                "egress-flow-segment-install-request",
                new Cookie(2),
                new FlowEndpoint(swId, 3, 4, 5),
                new FlowEndpoint(new SwitchId(swId.toLong() + 1), 6, 7, 8),
                9,
                new FlowTransitEncapsulation(10, FlowEncapsulationType.TRANSIT_VLAN));
    }
}
