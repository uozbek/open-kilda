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
import org.openkilda.floodlight.api.request.IngressFlowSegmentInstallRequest;
import org.openkilda.floodlight.command.SpeakerCommand;
import org.openkilda.floodlight.command.SpeakerCommandReport;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.Cookie;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchId;

import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;

import java.util.UUID;

@Slf4j
public class IngressFlowSegmentInstallCommandJsonTest extends IngressFlowSegmentBlankCommandJsonTest {
    @Override
    protected void verify(IngressFlowSegmentBlankRequest request,
                          SpeakerCommand<? extends SpeakerCommandReport> rawCommand) {
        Assert.assertTrue(rawCommand instanceof IngressFlowSegmentInstallCommand);
        verifyPayload(request, (IngressFlowSegmentInstallCommand) rawCommand);
    }

    @Override
    protected IngressFlowSegmentInstallRequest makeRequest() {
        return new IngressFlowSegmentInstallRequest(
                new MessageContext(),
                UUID.randomUUID(),
                "ingress-flow-segment-json-remove-request",
                new Cookie(1),
                new FlowEndpoint(new SwitchId(2), 3, 4, 5),
                new MeterConfig(new MeterId(6), 7000),
                8,
                new FlowTransitEncapsulation(9, FlowEncapsulationType.TRANSIT_VLAN));
    }
}
