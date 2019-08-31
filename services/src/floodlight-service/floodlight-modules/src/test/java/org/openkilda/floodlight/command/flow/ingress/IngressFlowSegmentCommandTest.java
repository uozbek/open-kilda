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

import static org.easymock.EasyMock.expect;

import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.MeterConfig;
import org.openkilda.floodlight.command.AbstractSpeakerCommandTest;
import org.openkilda.floodlight.command.flow.FlowSegmentReport;
import org.openkilda.messaging.MessageContext;

import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.projectfloodlight.openflow.types.TableId;

import java.util.concurrent.CompletableFuture;

abstract class IngressFlowSegmentCommandTest extends AbstractSpeakerCommandTest {
    protected final MessageContext messageContext = new MessageContext();
    protected final FlowEndpoint endpointZeroVlan = new FlowEndpoint(mapSwitchId(dpId), 4, 0, 0);
    protected final FlowEndpoint endpointSingleVlan = new FlowEndpoint(mapSwitchId(dpId), 4, 64, 0);
    protected final FlowEndpoint endpointDoubleVlan = new FlowEndpoint(mapSwitchId(dpId), 4, 64, 65);

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        expect(sw.getTables())
                .andReturn(ImmutableList.of(
                        TableId.of(0),
                        TableId.of(1),
                        TableId.of(2),
                        TableId.of(3),
                        TableId.of(4),
                        TableId.of(5),
                        TableId.of(6),
                        TableId.of(7),
                        TableId.of(8),
                        TableId.of(9)))
                .anyTimes();
    }

    protected void executeCommand(IngressFlowSegmentCommand command, int writeCount) throws Exception {
        switchFeaturesSetup(true);
        expectMeter();
        replayAll();

        final CompletableFuture<FlowSegmentReport> result = command.execute(commandProcessor);

        verifyWriteCount(writeCount);
        verifySuccessCompletion(result);
    }

    protected abstract IngressFlowSegmentCommand makeCommand(FlowEndpoint endpoint, MeterConfig meter);
}
