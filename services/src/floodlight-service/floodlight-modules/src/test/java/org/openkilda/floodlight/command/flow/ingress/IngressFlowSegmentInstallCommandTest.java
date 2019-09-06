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
import org.openkilda.floodlight.utils.OfAdapter;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.Cookie;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.MeterConfig;
import org.openkilda.model.SwitchFeature;

import net.floodlightcontroller.core.IOFSwitch;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFMetadata;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.U64;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class IngressFlowSegmentInstallCommandTest extends IngressFlowSegmentInstallTest {
    @Test
    public void happyPathZeroVlanTransitVlan() throws Exception {
        IngressFlowSegmentInstallCommand command = getCommandBuilder().endpoint(endpointIngresDefaultPort).build();
        executeCommand(command, 1);

        List<OFInstruction> instructions = new ArrayList<>();
        List<OFAction> applyActions = new ArrayList<>();
        OfAdapter.INSTANCE.makeMeterCall(of, command.getMeterConfig().getId(), applyActions, instructions);
        applyActions.add(of.actions().pushVlan(EthType.VLAN_FRAME));
        applyActions.add(OfAdapter.INSTANCE.setVlanIdAction(of, command.getEncapsulation().getId()));
        applyActions.add(of.actions().buildOutput().setPort(OFPort.of(command.getIslPort())).build());
        instructions.add(of.instructions().applyActions(applyActions));
        OFFlowAdd expect = of.buildFlowAdd()
                .setPriority(IngressFlowSegmentInstallCommand.FLOW_PRIORITY - 1)
                .setCookie(U64.of(command.getCookie().getValue()))
                .setMatch(of.buildMatch()
                        .setExact(MatchField.IN_PORT, OFPort.of(command.getEndpoint().getPortNumber()))
                        .build())
                .setInstructions(instructions)
                .build();
        verifyOfMessageEquals(expect, getWriteRecord(0).getRequest());
    }

    @Test
    public void happyPathSingleVlanTransitVlan() throws Exception {
        IngressFlowSegmentInstallCommand command = getCommandBuilder()
                .endpoint(endpointIngressSingleVlan)
                .updateMultiTableFlag(true)
                .build();
        executeCommand(command, 2);
        
        // table - dispatch
        OFFlowAdd expect = makeOuterVlanMatch(command);
        verifyOfMessageEquals(expect, getWriteRecord(0).getRequest());

        // table - ingress
        final MetadataAdapter.MetadataMatch metadata = MetadataAdapter.INSTANCE.addressOuterVlan(
                OFVlanVidMatch.ofVlan(command.getEndpoint().getOuterVlanId()));

        List<OFAction> applyActions = new ArrayList<>();
        List<OFInstruction> instructions = new ArrayList<>();
        OfAdapter.INSTANCE.makeMeterCall(of, command.getMeterConfig().getId(), applyActions, instructions);
        instructions.add(of.instructions().applyActions(applyActions));
        applyActions.add(of.actions().pushVlan(EthType.VLAN_FRAME));
        applyActions.add(OfAdapter.INSTANCE.setVlanIdAction(of, command.getEncapsulation().getId()));
        applyActions.add(of.actions().buildOutput().setPort(OFPort.of(command.getIslPort())).build());

        expect = of.buildFlowAdd()
                .setTableId(TableId.of(SwitchManager.INGRESS_TABLE_ID))
                .setPriority(IngressFlowSegmentInstallCommand.FLOW_PRIORITY - 10)
                .setCookie(U64.of(command.getCookie().getValue()))
                .setMatch(of.buildMatch()
                                  .setExact(MatchField.IN_PORT, OFPort.of(command.getEndpoint().getPortNumber()))
                                  .setMasked(MatchField.METADATA, OFMetadata.of(metadata.getValue()),
                                             OFMetadata.of(metadata.getMask()))
                                  .build())
                .setInstructions(instructions)
                .build();
        verifyOfMessageEquals(expect, getWriteRecord(1).getRequest());
    }

    @Test
    public void happyPathDoubleVlanTransitVlan() throws Exception {
        IngressFlowSegmentInstallCommand command = getCommandBuilder()
                .endpoint(endpointIngressDoubleVlan)
                .updateMultiTableFlag(true)
                .build();
        executeCommand(command, 2);
        
        // table - dispatch
        OFFlowAdd expect = makeOuterVlanMatch(command);
        verifyOfMessageEquals(expect, getWriteRecord(0).getRequest());

        // table - ingress
        final MetadataAdapter.MetadataMatch metadata = MetadataAdapter.INSTANCE.addressOuterVlan(
                OFVlanVidMatch.ofVlan(command.getEndpoint().getOuterVlanId()));

        List<OFAction> applyActions = new ArrayList<>();
        List<OFInstruction> instructions = new ArrayList<>();
        OfAdapter.INSTANCE.makeMeterCall(of, command.getMeterConfig().getId(), applyActions, instructions);
        instructions.add(of.instructions().applyActions(applyActions));
        applyActions.add(of.actions().pushVlan(EthType.VLAN_FRAME));
        applyActions.add(OfAdapter.INSTANCE.setVlanIdAction(of, command.getEncapsulation().getId()));
        applyActions.add(of.actions().buildOutput().setPort(OFPort.of(command.getIslPort())).build());

        expect = of.buildFlowAdd()
                .setTableId(TableId.of(SwitchManager.INGRESS_TABLE_ID))
                .setPriority(IngressFlowSegmentInstallCommand.FLOW_PRIORITY)
                .setCookie(U64.of(command.getCookie().getValue()))
                .setMatch(OfAdapter.INSTANCE.matchVlanId(of, of.buildMatch(), command.getEndpoint().getInnerVlanId())
                                  .setExact(MatchField.IN_PORT, OFPort.of(command.getEndpoint().getPortNumber()))
                                  .setMasked(MatchField.METADATA, OFMetadata.of(metadata.getValue()),
                                             OFMetadata.of(metadata.getMask()))
                                  .build())
                .setInstructions(instructions)
                .build();
        verifyOfMessageEquals(expect, getWriteRecord(1).getRequest());
    }

    @Test
    public void happyPathZeroVlanTransitVxLan() throws Exception {
        IngressFlowSegmentInstallCommand command = getCommandBuilder()
                .endpoint(endpointIngresDefaultPort)
                .encapsulation(encapsulationVxLan).build();
        executeCommand(command, 1);

        List<OFInstruction> instructions = new ArrayList<>();
        List<OFAction> applyActions = new ArrayList<>();
        OfAdapter.INSTANCE.makeMeterCall(of, command.getMeterConfig().getId(), applyActions, instructions);
        applyActions.addAll(makeVxLanEncodingActions(command));
        applyActions.add(of.actions().buildOutput().setPort(OFPort.of(command.getIslPort())).build());
        instructions.add(of.instructions().applyActions(applyActions));

        OFFlowAdd expected = of.buildFlowAdd()
                .setPriority(IngressFlowSegmentInstallCommand.FLOW_PRIORITY - 1)
                .setCookie(U64.of(command.getCookie().getValue()))
                .setMatch(of.buildMatch()
                        .setExact(MatchField.IN_PORT, OFPort.of(command.getEndpoint().getPortNumber())).build())
                .setInstructions(instructions)
                .build();
        verifyOfMessageEquals(expected, getWriteRecord(0).getRequest());
    }

    @Test
    public void happyPathSingleVlanTransitVxLan() throws Exception {
        IngressFlowSegmentInstallCommand command = getCommandBuilder()
                .endpoint(endpointIngressSingleVlan)
                .updateMultiTableFlag(true)
                .encapsulation(encapsulationVxLan).build();
        executeCommand(command, 2);

        // table - dispatch
        OFFlowAdd expect = makeOuterVlanMatch(command);
        verifyOfMessageEquals(expect, getWriteRecord(0).getRequest());

        // table - ingress
        final MetadataAdapter.MetadataMatch metadata = MetadataAdapter.INSTANCE.addressOuterVlan(
                OFVlanVidMatch.ofVlan(command.getEndpoint().getOuterVlanId()));

        List<OFInstruction> instructions = new ArrayList<>();
        List<OFAction> applyActions = new ArrayList<>();
        OfAdapter.INSTANCE.makeMeterCall(of, command.getMeterConfig().getId(), applyActions, instructions);
        applyActions.add(of.actions().pushVlan(EthType.VLAN_FRAME));
        applyActions.add(OfAdapter.INSTANCE.setVlanIdAction(of, command.getEndpoint().getOuterVlanId()));
        applyActions.addAll(makeVxLanEncodingActions(command));
        applyActions.add(of.actions().buildOutput().setPort(OFPort.of(command.getIslPort())).build());
        instructions.add(of.instructions().applyActions(applyActions));

        expect = of.buildFlowAdd()
                .setTableId(TableId.of(SwitchManager.INGRESS_TABLE_ID))
                .setPriority(IngressFlowSegmentInstallCommand.FLOW_PRIORITY - 10)
                .setCookie(U64.of(command.getCookie().getValue()))
                .setMatch(of.buildMatch()
                        .setExact(MatchField.IN_PORT, OFPort.of(command.getEndpoint().getPortNumber()))
                        .setMasked(MatchField.METADATA,
                                OFMetadata.of(metadata.getValue()), OFMetadata.of(metadata.getMask()))
                        .build())
                .setInstructions(instructions)
                .build();
        verifyOfMessageEquals(expect, getWriteRecord(1).getRequest());
    }

    @Test
    public void happyPathMultiTable() throws Exception {
        IngressFlowSegmentInstallCommand command = getCommandBuilder()
                .endpoint(endpointIngressSingleVlan)
                .updateMultiTableFlag(true)
                .build();
        executeCommand(command, 2);

        OFFlowAdd expect = makeOuterVlanMatch(command);
        verifyOfMessageEquals(expect, getWriteRecord(0).getRequest());

        // table - ingress
        final MetadataAdapter.MetadataMatch metadata = MetadataAdapter.INSTANCE.addressOuterVlan(
                OFVlanVidMatch.ofVlan(command.getEndpoint().getOuterVlanId()));

        List<OFAction> applyActions = new ArrayList<>();
        List<OFInstruction> instructions = new ArrayList<>();
        OfAdapter.INSTANCE.makeMeterCall(of, command.getMeterConfig().getId(), applyActions, instructions);
        instructions.add(of.instructions().applyActions(applyActions));
        applyActions.add(of.actions().pushVlan(EthType.VLAN_FRAME));
        applyActions.add(OfAdapter.INSTANCE.setVlanIdAction(of, command.getEncapsulation().getId()));
        applyActions.add(of.actions().buildOutput().setPort(OFPort.of(command.getIslPort())).build());

        expect = of.buildFlowAdd()
                .setTableId(TableId.of(SwitchManager.INGRESS_TABLE_ID))
                .setPriority(IngressFlowSegmentInstallCommand.FLOW_PRIORITY - 10)
                .setCookie(U64.of(command.getCookie().getValue()))
                .setMatch(of.buildMatch()
                        .setExact(MatchField.IN_PORT, OFPort.of(command.getEndpoint().getPortNumber()))
                        .setMasked(
                                MatchField.METADATA,
                                OFMetadata.of(metadata.getValue()), OFMetadata.of(metadata.getMask()))
                        .build())
                .setInstructions(instructions)
                .build();
        verifyOfMessageEquals(expect, getWriteRecord(1).getRequest());
    }

    @Test
    public void happyPathConnectedDevices() throws Exception {
        IngressFlowSegmentInstallCommand command = getCommandBuilder()
                .endpoint(endpointIngressSingleVlan.toBuilder().trackConnectedDevices(true).build())
                .build();
        executeCommand(command, 1);

        List<OFAction> applyActions = new ArrayList<>();
        List<OFInstruction> instructions = new ArrayList<>();
        OfAdapter.INSTANCE.makeMeterCall(of, command.getMeterConfig().getId(), applyActions, instructions);
        instructions.add(of.instructions().applyActions(applyActions));
        applyActions.add(OfAdapter.INSTANCE.setVlanIdAction(of, command.getEncapsulation().getId()));
        applyActions.add(of.actions().buildOutput().setPort(OFPort.of(command.getIslPort())).build());
        instructions.add(of.instructions().gotoTable(TableId.of(SwitchManager.POST_INGRESS_TABLE_ID)));

        OFFlowAdd expect = of.buildFlowAdd()
                .setPriority(IngressFlowSegmentInstallCommand.FLOW_PRIORITY)
                .setCookie(U64.of(command.getCookie().getValue()))
                .setMatch(OfAdapter.INSTANCE.matchVlanId(of, of.buildMatch(), command.getEndpoint().getOuterVlanId())
                        .setExact(MatchField.IN_PORT, OFPort.of(command.getEndpoint().getPortNumber()))
                        .build())
                .setInstructions(instructions)
                .build();
        verifyOfMessageEquals(expect, getWriteRecord(0).getRequest());
    }

    private List<OFAction> makeVxLanEncodingActions(IngressFlowSegmentInstallCommand command) {
        List<OFAction> actions = new ArrayList<>();

        actions.add(of.actions().buildNoviflowPushVxlanTunnel()
                .setEthSrc(MacAddress.of(DatapathId.of(command.getSwitchId().getId())))
                .setEthDst(MacAddress.of(command.getEgressSwitchId().toLong()))
                .setIpv4Src(IPv4Address.of("127.0.0.1"))
                .setIpv4Dst(IPv4Address.of("127.0.0.2"))
                .setUdpSrc(4500)
                .setVni(command.getEncapsulation().getId())
                .setFlags((short) 0x01)
                .build());
        actions.add(of.actions().buildNoviflowCopyField()
                .setOxmSrcHeader(of.oxms().buildNoviflowPacketOffset().getTypeLen())
                .setSrcOffset(56 * 8)
                .setOxmDstHeader(of.oxms().buildNoviflowPacketOffset().getTypeLen())
                .setDstOffset(6 * 8)
                .setNBits(MacAddress.BROADCAST.getLength() * 8)
                .build());

        return actions;
    }

    @Override
    protected void switchFeaturesSetup(IOFSwitch target, Set<SwitchFeature> features) {
        features.add(SwitchFeature.NOVIFLOW_EXPERIMENTER);
        features.add(SwitchFeature.NOVIFLOW_COPY_FIELD);
        super.switchFeaturesSetup(target, features);
    }

    @Override
    protected void expectMeter() {
        expectMeterInstall();
    }

    @Override
    protected CommandBuilder getCommandBuilder() {
        return new CommandBuilder();
    }

    static class CommandBuilder implements ICommandBuilder {
        private FlowSegmentMetadata metadata = new FlowSegmentMetadata(
                "ingress-segment-install-flow-id", new Cookie(1), false);
        private final int islPort = 6;

        private MeterConfig meterConfig = IngressFlowSegmentInstallCommandTest.meterConfig;
        private FlowEndpoint endpoint = IngressFlowSegmentInstallCommandTest.endpointIngressSingleVlan;
        private FlowEndpoint egressEndpoint = IngressFlowSegmentInstallCommandTest.endpointEgressSingleVlan;
        private FlowTransitEncapsulation encapsulation = encapsulationVlan;

        @Override
        public IngressFlowSegmentInstallCommand build() {
            return new IngressFlowSegmentInstallCommand(
                    new MessageContext(), UUID.randomUUID(), metadata, endpoint, meterConfig,
                    egressEndpoint.getDatapath(), islPort, encapsulation);
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

        @Override
        public CommandBuilder meterConfig(MeterConfig meterConfig) {
            this.meterConfig = meterConfig;
            return this;
        }

        public CommandBuilder encapsulation(FlowTransitEncapsulation encapsulation) {
            this.encapsulation = encapsulation;
            return this;
        }
    }
}
