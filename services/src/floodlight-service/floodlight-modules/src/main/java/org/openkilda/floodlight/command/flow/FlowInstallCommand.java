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

package org.openkilda.floodlight.command.flow;


import org.openkilda.messaging.MessageContext;
import org.openkilda.model.Cookie;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.SwitchId;

import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActions;
import org.projectfloodlight.openflow.protocol.oxm.OFOxms;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.U64;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public abstract class FlowInstallCommand extends FlowCommand<FlowReport> {

    final Integer inputPort;
    final Integer outputPort;
    final Integer transitEncapsulationId;
    final FlowEncapsulationType transitEncapsulationType;

    FlowInstallCommand(UUID commandId, String flowId, MessageContext messageContext, Cookie cookie, SwitchId switchId,
                       Integer inputPort, Integer outputPort,
                       Integer transitEncapsulationId, FlowEncapsulationType transitEncapsulationType) {
        super(commandId, flowId, messageContext, cookie, switchId);
        this.inputPort = inputPort;
        this.outputPort = outputPort;
        this.transitEncapsulationId = transitEncapsulationId;
        this.transitEncapsulationType = transitEncapsulationType;
    }

    protected FlowReport makeReport(Exception error) {
        return new FlowReport(this, error);
    }

    protected FlowReport makeSuccessReport() {
        return new FlowReport(this);
    }

    final OFFlowAdd.Builder makeOfFlowAddMessageBuilder(OFFactory ofFactory) {
        return ofFactory.buildFlowAdd()
                .setCookie(U64.of(cookie.getValue()))
                .setPriority(FLOW_PRIORITY);
    }

    static final List<OFAction> makePacketVlanTransformActions(
            OFFactory of, List<Integer> currentVlanStack, List<Integer> desiredVlanStack) {
        Iterator<Integer> currentIter = currentVlanStack.iterator();
        Iterator<Integer> desiredIter = desiredVlanStack.iterator();

        final List<OFAction> actions = new ArrayList<>();
        while (currentIter.hasNext() && desiredIter.hasNext()) {
            Integer current = currentIter.next();
            Integer desired = desiredIter.next();
            if (!current.equals(desired)) {
                // remove all extra VLANs
                while (currentIter.hasNext()) {
                    currentIter.next();
                    actions.add(of.actions().popVlan());
                }
                // rewrite existing VLAN stack "head"
                actions.add(makeSetVlanIdAction(of, desired));
                break;
            }
        }

        // remove all extra VLANs (if previous loops ends with lack of desired VLANs
        while (currentIter.hasNext()) {
            currentIter.next();
            actions.add(of.actions().popVlan());
        }

        while (desiredIter.hasNext()) {
            actions.add(of.actions().pushVlan(EthType.VLAN_FRAME));
            actions.add(makeSetVlanIdAction(of, desiredIter.next()));
        }
        return actions;
    }

    static final OFAction makeSetVlanIdAction(final OFFactory factory, final int newVlan) {
        OFOxms oxms = factory.oxms();
        OFActions actions = factory.actions();
        OFVlanVidMatch vlanMatch = factory.getVersion() == OFVersion.OF_12
                ? OFVlanVidMatch.ofRawVid((short) newVlan) : OFVlanVidMatch.ofVlan(newVlan);

        return actions.buildSetField().setField(oxms.buildVlanVid()
                                                        .setValue(vlanMatch)
                                                        .build()).build();
    }
}
