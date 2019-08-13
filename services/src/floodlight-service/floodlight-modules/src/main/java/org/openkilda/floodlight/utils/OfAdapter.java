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

package org.openkilda.floodlight.utils;

import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActions;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class OfAdapter {
    public static OfAdapter INSTANCE = new OfAdapter();

    public List<OFAction> makeVlanTransformActions(
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
                actions.add(setVlanIdAction(of, desired));
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
            actions.add(setVlanIdAction(of, desiredIter.next()));
        }
        return actions;
    }

    public OFAction setVlanIdAction(OFFactory of, int vlanId) {
        OFActions actions = of.actions();
        OFVlanVidMatch vlanMatch = of.getVersion() == OFVersion.OF_12
                ? OFVlanVidMatch.ofRawVid((short) vlanId) : OFVlanVidMatch.ofVlan(vlanId);

        return actions.setField(of.oxms().vlanVid(vlanMatch));
    }

    private OfAdapter() { }
}
