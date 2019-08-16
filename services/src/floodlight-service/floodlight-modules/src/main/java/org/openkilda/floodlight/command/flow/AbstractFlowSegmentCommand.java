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

import org.openkilda.floodlight.command.SpeakerCommand;
import org.openkilda.floodlight.error.SwitchNotFoundException;
import org.openkilda.floodlight.model.SwitchDescriptor;
import org.openkilda.floodlight.service.FeatureDetectorService;
import org.openkilda.messaging.MessageContext;
import org.openkilda.messaging.model.SpeakerSwitchView;
import org.openkilda.model.Cookie;
import org.openkilda.model.SwitchId;

import lombok.AccessLevel;
import lombok.Getter;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.util.FlowModUtils;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFFlowDeleteStrict;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.types.U64;

import java.util.Set;
import java.util.UUID;

@Getter
public abstract class AbstractFlowSegmentCommand extends SpeakerCommand<FlowSegmentReport> {
    protected static final long FLOW_COOKIE_MASK = 0x7FFFFFFFFFFFFFFFL;
    public static final int FLOW_PRIORITY = FlowModUtils.PRIORITY_HIGH;

    // payload
    protected final UUID commandId;
    protected final String flowId;
    protected final Cookie cookie;

    public AbstractFlowSegmentCommand(
            MessageContext messageContext, SwitchId switchId, UUID commandId, String flowId, Cookie cookie) {
        super(messageContext, switchId);
        this.commandId = commandId;
        this.flowId = flowId;
        this.cookie = cookie;
    }

    protected FlowSegmentReport makeReport(Exception error) {
        return new FlowSegmentReport(this, error);
    }

    protected FlowSegmentReport makeSuccessReport() {
        return new FlowSegmentReport(this);
    }

    protected abstract OFFlowMod.Builder makeFlowModBuilder(OFFactory of);

    protected OFFlowAdd.Builder makeFlowAddBuilder(OFFactory of) {
        return of.buildFlowAdd()
                .setPriority(FLOW_PRIORITY)
                .setCookie(U64.of(cookie.getValue()));
    }

    protected OFFlowDeleteStrict.Builder makeFlowDelBuilder(OFFactory of) {
        return of.buildFlowDeleteStrict()
                .setPriority(FLOW_PRIORITY)
                .setCookie(U64.of(cookie.getValue()));
    }
}
