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

import org.openkilda.floodlight.model.FlowSegmentMetadata;
import org.openkilda.floodlight.switchmanager.SwitchManager;
import org.openkilda.floodlight.utils.MetadataAdapter;
import org.openkilda.floodlight.utils.MetadataAdapter.MetadataMatch;
import org.openkilda.floodlight.utils.OfAdapter;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.Cookie;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.MeterConfig;

import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFFlowDeleteStrict;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.OFMetadata;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.U64;

import java.util.UUID;

public class OneSwitchFlowRemoveCommandTest extends IngressFlowSegmentRemoveTest {
    private static final FlowEndpoint endpointEgressDefaultPort = new FlowEndpoint(
            endpointIngresDefaultPort.getDatapath(),
            IngressFlowSegmentRemoveTest.endpointEgressDefaultPort.getPortNumber());
    private static final FlowEndpoint endpointEgressSingleVlan = new FlowEndpoint(
            endpointIngressSingleVlan.getDatapath(),
            IngressFlowSegmentRemoveTest.endpointEgressSingleVlan.getPortNumber(),
            IngressFlowSegmentRemoveTest.endpointEgressSingleVlan.getOuterVlanId());
    private static final FlowEndpoint endpointEgressDoubleVlan = new FlowEndpoint(
            endpointIngressDoubleVlan.getDatapath(),
            IngressFlowSegmentRemoveTest.endpointEgressDoubleVlan.getPortNumber(),
            IngressFlowSegmentRemoveTest.endpointEgressDoubleVlan.getOuterVlanId(),
            IngressFlowSegmentRemoveTest.endpointEgressDoubleVlan.getInnerVlanId());

    @Test
    public void happyPathDefaultPort() throws Exception {
        OneSwitchFlowRemoveCommand command = getCommandBuilder()
                .endpoint(endpointIngresDefaultPort)
                .build();
        executeCommand(command, 2);

        OFFlowDeleteStrict expect = of.buildFlowDeleteStrict()
                .setPriority(OneSwitchFlowRemoveCommand.FLOW_PRIORITY - 1)
                .setCookie(U64.of(command.getCookie().getValue()))
                .setMatch(of.buildMatch()
                                  .setExact(MatchField.IN_PORT, OFPort.of(command.getEndpoint().getPortNumber()))
                                  .build())
                .build();
        verifyOfMessageEquals(expect, getWriteRecord(0).getRequest());

        verifyPreQinqRuleRemove(command, getWriteRecord(1).getRequest());
    }

    @Test
    public void happyPathOuterVlan() throws Exception {
        OneSwitchFlowRemoveCommand command = getCommandBuilder()
                .updateMultiTableFlag(true)
                .endpoint(endpointIngressSingleVlan)
                .build();
        executeCommand(command, 3);

        verifyOuterVlanMatchRemove(command, getWriteRecord(0).getRequest());

        MetadataMatch metadata = MetadataAdapter.INSTANCE.addressOuterVlan(
                OFVlanVidMatch.ofVlan(command.getEndpoint().getOuterVlanId()));
        OFFlowDeleteStrict expected = of.buildFlowDeleteStrict()
                .setTableId(TableId.of(SwitchManager.INGRESS_TABLE_ID))
                .setPriority(OneSwitchFlowRemoveCommand.FLOW_PRIORITY - 10)
                .setCookie(U64.of(command.getCookie().getValue()))
                .setMatch(of.buildMatch()
                                  .setExact(MatchField.IN_PORT, OFPort.of(command.getEndpoint().getPortNumber()))
                                  .setMasked(MatchField.METADATA,
                                             OFMetadata.of(metadata.getValue()), OFMetadata.of(metadata.getMask()))
                                  .build())
                .build();
        verifyOfMessageEquals(expected, getWriteRecord(1).getRequest());

        verifyPreQinqRuleRemove(command, getWriteRecord(2).getRequest());
    }

    @Test
    public void happyPathOuterAndInnerVlan() throws Exception {
        OneSwitchFlowRemoveCommand command = getCommandBuilder()
                .updateMultiTableFlag(true)
                .endpoint(endpointEgressDoubleVlan)
                .build();
        executeCommand(command, 3);

        verifyOuterVlanMatchRemove(command, getWriteRecord(0).getRequest());

        MetadataMatch metadata = MetadataAdapter.INSTANCE.addressOuterVlan(
                OFVlanVidMatch.ofVlan(command.getEndpoint().getOuterVlanId()));
        OFFlowDeleteStrict expected = of.buildFlowDeleteStrict()
                .setTableId(TableId.of(SwitchManager.INGRESS_TABLE_ID))
                .setPriority(OneSwitchFlowRemoveCommand.FLOW_PRIORITY)
                .setCookie(U64.of(command.getCookie().getValue()))
                .setMatch(OfAdapter.INSTANCE.matchVlanId(of, of.buildMatch(), command.getEndpoint().getInnerVlanId())
                                  .setExact(MatchField.IN_PORT, OFPort.of(command.getEndpoint().getPortNumber()))
                                  .setMasked(MatchField.METADATA,
                                             OFMetadata.of(metadata.getValue()),
                                             OFMetadata.of(metadata.getMask()))
                                  .build())
                .build();
        verifyOfMessageEquals(expected, getWriteRecord(1).getRequest());

        verifyPreQinqRuleRemove(command, getWriteRecord(2).getRequest());
    }

    @Override
    protected CommandBuilder getCommandBuilder() {
        return new CommandBuilder();
    }

    static class CommandBuilder implements ICommandBuilder {
        private FlowSegmentMetadata metadata = new FlowSegmentMetadata(
                "single-switch-flow-remove-flow-id", new Cookie(1), false);

        private FlowEndpoint endpoint = OneSwitchFlowRemoveCommandTest.endpointIngressSingleVlan;
        private FlowEndpoint egressEndpoint = OneSwitchFlowRemoveCommandTest.endpointEgressSingleVlan;
        private MeterConfig meterConfig = OneSwitchFlowRemoveCommandTest.meterConfig;

        @Override
        public OneSwitchFlowRemoveCommand build() {
            return new OneSwitchFlowRemoveCommand(
                    new MessageContext(), UUID.randomUUID(), metadata, endpoint, meterConfig, egressEndpoint);
        }

        public CommandBuilder updateMultiTableFlag(boolean isMultiTable) {
            this.metadata = metadata.toBuilder().multiTable(isMultiTable).build();
            return this;
        }

        @Override
        public CommandBuilder endpoint(FlowEndpoint endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        public CommandBuilder egressEndpoint(FlowEndpoint endpoint) {
            this.egressEndpoint = endpoint;
            return this;
        }

        @Override
        public CommandBuilder meterConfig(MeterConfig meterConfig) {
            this.meterConfig = meterConfig;
            return this;
        }
    }
}
