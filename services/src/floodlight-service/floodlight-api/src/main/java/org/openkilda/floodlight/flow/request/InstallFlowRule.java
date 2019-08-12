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

package org.openkilda.floodlight.flow.request;

import org.openkilda.floodlight.api.FlowSegmentOperation;
import org.openkilda.floodlight.api.request.AbstractFlowSegmentRequest;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.Cookie;
import org.openkilda.model.SwitchId;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.UUID;

@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class InstallFlowRule extends AbstractFlowSegmentRequest {
    public InstallFlowRule(MessageContext context,
                           FlowSegmentOperation operation, UUID commandId,
                           SwitchId switchId, String flowId, Cookie cookie) {
        super(context, switchId, operation, commandId, flowId, cookie);

        throw new IllegalStateException(String.format("**FIXME** class %s must be drop i.e. its usage is prohibited",
                                                      getClass().getName()));
    }
}
