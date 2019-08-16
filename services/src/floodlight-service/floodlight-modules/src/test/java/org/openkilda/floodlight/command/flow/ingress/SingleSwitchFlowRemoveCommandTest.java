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
import org.openkilda.floodlight.model.SwitchDescriptor;
import org.openkilda.floodlight.utils.MetadataAdapter;
import org.openkilda.floodlight.utils.MetadataAdapter.MetadataMatch;
import org.openkilda.floodlight.utils.OfAdapter;
import org.openkilda.model.Cookie;
import org.openkilda.model.SwitchId;

import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFFlowDeleteStrict;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.OFMetadata;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.U64;

import java.util.UUID;

public class SingleSwitchFlowRemoveCommandTest extends AbstractIngressFlowSegmentRemoveCommandTest {
    private final static FlowEndpoint defaultEgressEndpoint = new FlowEndpoint(mapSwitchId(dpId), 2, 0, 0);

    @Test
    public void happyPathDefaultPort() throws Exception {
        SingleSwitchFlowRemoveCommand command = makeCommand(endpointZeroVlan, meterConfig);
        executeCommand(command, 2);

        OFFlowDeleteStrict expect = of.buildFlowDeleteStrict()
                .setPriority(SingleSwitchFlowRemoveCommand.FLOW_PRIORITY - 1)
                .setCookie(U64.of(command.getCookie().getValue()))
                .setMatch(of.buildMatch()
                                  .setExact(MatchField.IN_PORT, OFPort.of(command.getEndpoint().getPortNumber()))
                                  .build())
                .build();
        verifyOfMessageEquals(expect, getWriteRecord(0).getRequest());

        SwitchDescriptor swDesc = new SwitchDescriptor(sw);
        verifyPreQinqRuleRemove(command, swDesc, getWriteRecord(1).getRequest());
    }

    @Test
    public void happyPathOuterVlan() throws Exception {
        SingleSwitchFlowRemoveCommand command = makeCommand(endpointSingleVlan, meterConfig);
        executeCommand(command, 3);

        verifyOuterVlanMatchRemove(command, getWriteRecord(0).getRequest());

        SwitchDescriptor swDesc = new SwitchDescriptor(sw);
        MetadataMatch metadata = MetadataAdapter.INSTANCE.addressOuterVlan(
                OFVlanVidMatch.ofVlan(command.getEndpoint().getOuterVlanId()));
        OFFlowDeleteStrict expected = of.buildFlowDeleteStrict()
                .setTableId(swDesc.getTableIngress())
                .setPriority(SingleSwitchFlowRemoveCommand.FLOW_PRIORITY - 10)
                .setCookie(U64.of(command.getCookie().getValue()))
                .setMatch(of.buildMatch()
                                  .setExact(MatchField.IN_PORT, OFPort.of(command.getEndpoint().getPortNumber()))
                                  .setMasked(MatchField.METADATA,
                                             OFMetadata.of(metadata.getValue()), OFMetadata.of(metadata.getMask()))
                                  .build())
                .build();
        verifyOfMessageEquals(expected, getWriteRecord(1).getRequest());

        verifyPreQinqRuleRemove(command, swDesc, getWriteRecord(2).getRequest());
    }

    @Test
    public void happyPathOuterAndInnerVlan() throws Exception {
        SingleSwitchFlowRemoveCommand command = makeCommand(endpointDoubleVlan, meterConfig);
        executeCommand(command, 3);

        verifyOuterVlanMatchRemove(command, getWriteRecord(0).getRequest());

        SwitchDescriptor swDesc = new SwitchDescriptor(sw);
        MetadataMatch metadata = MetadataAdapter.INSTANCE.addressOuterVlan(
                OFVlanVidMatch.ofVlan(command.getEndpoint().getOuterVlanId()));

        OFFlowDeleteStrict expected = of.buildFlowDeleteStrict()
                .setTableId(swDesc.getTableIngress())
                .setPriority(SingleSwitchFlowRemoveCommand.FLOW_PRIORITY)
                .setCookie(U64.of(command.getCookie().getValue()))
                .setMatch(OfAdapter.INSTANCE.matchVlanId(of, of.buildMatch(), command.getEndpoint().getInnerVlanId())
                                  .setExact(MatchField.IN_PORT, OFPort.of(command.getEndpoint().getPortNumber()))
                                  .setMasked(MatchField.METADATA,
                                             OFMetadata.of(metadata.getValue()),
                                             OFMetadata.of(metadata.getMask()))
                                  .build())
                .build();
        verifyOfMessageEquals(expected, getWriteRecord(1).getRequest());

        verifyPreQinqRuleRemove(command, swDesc, getWriteRecord(2).getRequest());
    }

    @Override
    protected SingleSwitchFlowRemoveCommand makeCommand(FlowEndpoint endpoint, MeterConfig meter) {
        return makeCommand(endpoint, defaultEgressEndpoint, meter);
    }

    protected SingleSwitchFlowRemoveCommand makeCommand(
            FlowEndpoint endpoint, FlowEndpoint endpointEgress, MeterConfig meter) {
        UUID commandId = UUID.randomUUID();
        String flowId = "single-switch-flow-remove-flow-id";
        Cookie cookie = new Cookie(1);

        return new SingleSwitchFlowRemoveCommand(
                messageContext, mapSwitchId(dpId), commandId, flowId, cookie, endpoint, meter,
                endpointEgress);
    }
}
