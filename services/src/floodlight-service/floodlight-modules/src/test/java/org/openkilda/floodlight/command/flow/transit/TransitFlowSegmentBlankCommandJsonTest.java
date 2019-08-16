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

package org.openkilda.floodlight.command.flow.transit;

import org.openkilda.floodlight.api.request.TransitFlowSegmentBlankRequest;
import org.openkilda.floodlight.command.AbstractSpeakerCommandJsonTest;

import org.junit.Assert;

abstract class TransitFlowSegmentBlankCommandJsonTest
        extends AbstractSpeakerCommandJsonTest<TransitFlowSegmentBlankRequest> {
    protected void verifyPayload(TransitFlowSegmentBlankRequest request, TransitFlowSegmentBlankCommand command) {
        Assert.assertEquals(request.getMessageContext(), command.getMessageContext());
        Assert.assertEquals(request.getSwitchId(), command.getSwitchId());
        Assert.assertEquals(request.getCommandId(), command.getCommandId());
        Assert.assertEquals(request.getFlowId(), command.getFlowId());
        Assert.assertEquals(request.getCookie(), command.getCookie());
        Assert.assertEquals(request.getIngressIslPort(), command.getIngressIslPort());
        Assert.assertEquals(request.getEgressIslPort(), command.getEgressIslPort());
        Assert.assertEquals(request.getEncapsulation(), command.getEncapsulation());
    }
}
