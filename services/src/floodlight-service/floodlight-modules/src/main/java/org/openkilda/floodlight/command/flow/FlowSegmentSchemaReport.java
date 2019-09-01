/*
 * Copyright 2019 Telstra Open Source
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

import org.openkilda.model.of.FlowSegmentSchema;
import org.openkilda.floodlight.api.response.SpeakerFlowSegmentSchemaResponse;
import org.openkilda.floodlight.api.response.SpeakerResponse;

public class FlowSegmentSchemaReport extends FlowSegmentReport {
    private final FlowSegmentSchema schema;

    public FlowSegmentSchemaReport(FlowSegmentCommand command, FlowSegmentSchema schema) {
        super(command);

        this.schema = schema;
    }

    @Override
    protected SpeakerResponse makeSuccessReply() {
        FlowSegmentCommand command = getCommand();
        return new SpeakerFlowSegmentSchemaResponse(command.getMessageContext(), command.getCommandId(), schema);
    }
}
