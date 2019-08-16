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
import org.openkilda.floodlight.api.MeterConfig;
import org.openkilda.floodlight.api.request.SingleSwitchFlowBlankRequest;
import org.openkilda.floodlight.api.request.SingleSwitchFlowInstallRequest;
import org.openkilda.floodlight.api.request.SingleSwitchFlowRemoveRequest;
import org.openkilda.floodlight.command.SpeakerCommand;
import org.openkilda.floodlight.command.SpeakerCommandReport;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.Cookie;
import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchId;

import org.junit.Assert;

import java.util.UUID;

public class SingleSwitchFlowRemoveCommandJsonTest extends SingleSwitchFlowBlankCommandJsonTest {

    @Override
    protected void verify(SingleSwitchFlowBlankRequest request,
                          SpeakerCommand<? extends SpeakerCommandReport> rawCommand) {
        Assert.assertTrue(rawCommand instanceof SingleSwitchFlowRemoveCommand);
        verifyPayload(request, (SingleSwitchFlowRemoveCommand) rawCommand);
    }

    @Override
    protected SingleSwitchFlowRemoveRequest makeRequest() {
        SwitchId swId = new SwitchId(1);
        return new SingleSwitchFlowRemoveRequest(
                new MessageContext(),
                swId,
                UUID.randomUUID(),
                "single-switch-flow-remove-request",
                new Cookie(2),
                new FlowEndpoint(swId, 3, 4, 5),
                new MeterConfig(new MeterId(6), 7000),
                new FlowEndpoint(swId, 8, 9, 10));
    }
}
