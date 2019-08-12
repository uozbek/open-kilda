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
import org.projectfloodlight.openflow.types.OFVlanVidMatch;

public class OfAdapter {
    public static OfAdapter INSTANCE = new OfAdapter();

    public OFAction setVlanIdAction(OFFactory of, int vlanId) {
        OFActions actions = of.actions();
        OFVlanVidMatch vlanMatch = of.getVersion() == OFVersion.OF_12
                ? OFVlanVidMatch.ofRawVid((short) vlanId) : OFVlanVidMatch.ofVlan(vlanId);

        return actions.setField(of.oxms().vlanVid(vlanMatch));
    }

    private OfAdapter() { }
}
