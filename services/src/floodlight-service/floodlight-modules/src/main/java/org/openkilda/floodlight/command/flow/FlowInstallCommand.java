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
import org.openkilda.model.SwitchId;

import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.types.U64;

import java.util.UUID;

public abstract class FlowInstallCommand extends FlowCommand<FlowInstallReport> {

    final Integer inputPort;
    final Integer outputPort;

    FlowInstallCommand(UUID commandId, String flowId, MessageContext messageContext, Cookie cookie, SwitchId switchId,
                       Integer inputPort, Integer outputPort) {
        super(commandId, flowId, messageContext, cookie, switchId);
        this.inputPort = inputPort;
        this.outputPort = outputPort;
    }

    protected FlowInstallReport makeReport(Exception error) {
        return new FlowInstallReport(this, error);
    }

    protected FlowInstallReport makeSuccessReport() {
        return new FlowInstallReport(this);
    }

    final OFFlowAdd.Builder makeFlowAddMessageBuilder(OFFactory ofFactory) {
        return ofFactory.buildFlowAdd()
                .setCookie(U64.of(cookie.getValue()))
                .setPriority(FLOW_PRIORITY);
    }
}
