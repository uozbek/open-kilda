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

package org.openkilda.floodlight.flow;

import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.io.Serializable;

@Value
public class FlowEndpoint implements Serializable {
    @JsonProperty("datapath")
    private final SwitchId datapath;

    @JsonProperty("port_number")
    private final int portNumber;

    @JsonProperty("outer_vlan_id")
    private final int outerVlanId;

    @JsonProperty("inner_vlan_id")
    private final int innerVlanId;

    @JsonCreator
    public FlowEndpoint(
            @JsonProperty("datapath") SwitchId datapath,
            @JsonProperty("port_number") int portNumber,
            @JsonProperty("outer_vlan_id") int outerVlanId,
            @JsonProperty("inner_vlan_id") int innerVlanId) {
        this.datapath = datapath;
        this.portNumber = portNumber;
        this.outerVlanId = outerVlanId;
        this.innerVlanId = innerVlanId;
    }
}
