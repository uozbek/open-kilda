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

package org.openkilda.wfm.topology.flowhs.validation.rules;

import org.openkilda.floodlight.api.FlowEndpoint;
import org.openkilda.floodlight.api.request.EgressFlowSegmentInstallRequest;
import org.openkilda.floodlight.api.request.FlowSegmentRequest;
import org.openkilda.floodlight.flow.response.FlowRuleResponse;

import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

@Slf4j
public class NonIngressRulesValidator extends RulesValidator {

    public NonIngressRulesValidator(FlowSegmentRequest expected, FlowRuleResponse actual) {
        super(expected, actual);
    }

    @Override
    public boolean validate() {
        boolean valid = super.validate();
        if (expected instanceof EgressFlowSegmentInstallRequest) {
            valid = valid & validateEgressRule();
        }

        return valid;
    }

    private boolean validateEgressRule() {
        boolean valid = true;
        EgressFlowSegmentInstallRequest expectedEgressRule = (EgressFlowSegmentInstallRequest) expected;
        FlowEndpoint endpoint = expectedEgressRule.getEndpoint();
        if (!Objects.equals(endpoint.getOuterVlanId(), actual.getOutVlan())) {
            log.warn("Output vlan mismatch for the flow {} on the switch {}. Expected {}, actual {}",
                    expected.getFlowId(), expected.getSwitchId(), endpoint.getOuterVlanId(),
                    actual.getOutVlan());
            valid = false;
        }

        return valid;
    }
}
