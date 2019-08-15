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
import org.openkilda.floodlight.model.SwitchDescriptor;
import org.openkilda.floodlight.utils.MetadataAdapter;
import org.openkilda.floodlight.utils.OfAdapter;
import org.openkilda.model.Cookie;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.SwitchId;

import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFFlowDelete;
import org.projectfloodlight.openflow.protocol.OFFlowDeleteStrict;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.OFMetadata;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.U64;

import java.util.UUID;

public class IngressFlowSegmentRemoveCommandTest extends AbstractIngressFlowSegmentRemoveCommandTest {
    @Test
    public void happyPathDefaultPort() throws Exception {
        IngressFlowSegmentRemoveCommand command = makeCommand(endpointDefaultPort, meterConfig);
        executeCommand(command, 2);

        OFFlowMod expect = of.buildFlowDeleteStrict()
                .setCookie(U64.of(command.getCookie().getValue()))
                .setPriority(IngressFlowSegmentRemoveCommand.FLOW_PRIORITY - 1)
                .setMatch(of.buildMatch()
                                  .setExact(MatchField.IN_PORT, OFPort.of(command.getEndpoint().getPortNumber()))
                                  .build())
                .build();
        verifyOfMessageEquals(expect, getWriteRecord(0).getRequest());

        final SwitchDescriptor swDesc = new SwitchDescriptor(sw);
        verifyPreQinqRuleRemove(command, swDesc, (OFFlowDelete) getWriteRecord(1).getRequest());
    }

    @Test
    public void happyPathOuterVlan() throws Exception {
        IngressFlowSegmentRemoveCommand command = makeCommand(endpointOuterVlan, meterConfig);
        executeCommand(command, 3);

        // table - dispatch
        verifyOuterVlanMatchRemove(command, (OFFlowDeleteStrict) getWriteRecord(0).getRequest());

        // table - ingress (match inner vlan)
        final SwitchDescriptor swDesc = new SwitchDescriptor(sw);
        MetadataAdapter.MetadataMatch metadata = MetadataAdapter.INSTANCE.addressOuterVlan(
                OFVlanVidMatch.ofVlan(command.getEndpoint().getOuterVlanId()));
        OFFlowDeleteStrict expect = of.buildFlowDeleteStrict()
                .setTableId(swDesc.getTableIngress())
                .setCookie(U64.of(command.getCookie().getValue()))
                .setPriority(IngressFlowSegmentRemoveCommand.FLOW_PRIORITY - 10)
                .setMatch(of.buildMatch()
                                  .setExact(MatchField.IN_PORT, OFPort.of(command.getEndpoint().getPortNumber()))
                                  .setMasked(MatchField.METADATA,
                                             OFMetadata.of(metadata.getValue()), OFMetadata.of(metadata.getMask()))
                                  .build())
                .build();
        verifyOfMessageEquals(expect, getWriteRecord(1).getRequest());

        verifyPreQinqRuleRemove(command, swDesc, (OFFlowDelete) getWriteRecord(2).getRequest());
    }

    @Test
    public void happyPathOuterAndInnerVlan() throws Exception {
        IngressFlowSegmentRemoveCommand command = makeCommand(endpointOuterAndInnerVlan, meterConfig);
        executeCommand(command, 3);

        verifyOuterVlanMatchRemove(command, (OFFlowDeleteStrict) getWriteRecord(0).getRequest());

        SwitchDescriptor swDesc = new SwitchDescriptor(sw);
        MetadataAdapter.MetadataMatch metadata = MetadataAdapter.INSTANCE.addressOuterVlan(
                OFVlanVidMatch.ofVlan(command.getEndpoint().getOuterVlanId()));
        OFFlowDeleteStrict expect = of.buildFlowDeleteStrict()
                .setTableId(swDesc.getTableIngress())
                .setCookie(U64.of(command.getCookie().getValue()))
                .setPriority(IngressFlowSegmentRemoveCommand.FLOW_PRIORITY)
                .setMatch(OfAdapter.INSTANCE.matchVlanId(of, of.buildMatch(), command.getEndpoint().getInnerVlanId())
                                  .setExact(MatchField.IN_PORT, OFPort.of(command.getEndpoint().getPortNumber()))
                                  .setMasked(MatchField.METADATA,
                                             OFMetadata.of(metadata.getValue()), OFMetadata.of(metadata.getMask()))
                                  .build())
                .build();
        verifyOfMessageEquals(expect, getWriteRecord(1).getRequest());

        verifyPreQinqRuleRemove(command, swDesc, (OFFlowDelete) getWriteRecord(2).getRequest());
    }

    private void verifyOuterVlanMatchRemove(IngressFlowSegmentRemoveCommand command, OFFlowDeleteStrict actual) {
        OFFlowMod expect = of.buildFlowDeleteStrict()
                .setCookie(U64.of(command.getCookie().getValue()))
                .setPriority(IngressFlowSegmentRemoveCommand.FLOW_PRIORITY)
                .setMatch(OfAdapter.INSTANCE.matchVlanId(of, of.buildMatch(), command.getEndpoint().getOuterVlanId())
                                  .setExact(MatchField.IN_PORT, OFPort.of(command.getEndpoint().getPortNumber()))
                                  .build())
                .build();
        verifyOfMessageEquals(expect, actual);
    }

    private void verifyPreQinqRuleRemove(
            IngressFlowSegmentRemoveCommand command, SwitchDescriptor swDesc, OFFlowDelete actual) {
        OFFlowDelete expect = of.buildFlowDelete()
                .setTableId(swDesc.getTableDispatch())
                .setCookie(U64.of(command.getCookie().getValue()))
                .build();
        verifyOfMessageEquals(expect, actual);
    }

    @Override
    protected IngressFlowSegmentRemoveCommand makeCommand(FlowEndpoint endpoint, MeterConfig meter) {
        UUID commandId = UUID.randomUUID();
        String flowId = "ingress-segment-remove-flow-id";
        Cookie cookie = new Cookie(2);
        FlowTransitEncapsulation encapsulation = new FlowTransitEncapsulation(97, FlowEncapsulationType.TRANSIT_VLAN);
        int islPort = 7;
        return new IngressFlowSegmentRemoveCommand(
                messageContext, new SwitchId(dpId.getLong()), commandId, flowId, cookie, endpoint, meter, islPort,
                encapsulation);
    }
}
