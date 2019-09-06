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

package org.openkilda.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Value
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class FlowEndpoint extends NetworkEndpoint {
    @JsonProperty("outer_vlan_id")
    private final int outerVlanId;

    @JsonProperty("inner_vlan_id")
    private final int innerVlanId;

    @JsonProperty("track_connected_devices")
    private final boolean trackConnectedDevices;

    public FlowEndpoint(SwitchId datapath, Integer portNumber) {
        this(datapath, portNumber, 0);
    }

    public FlowEndpoint(SwitchId datapath, Integer portNumber, int outerVlanId) {
        this(datapath, portNumber, outerVlanId, 0, false);
    }

    public FlowEndpoint(SwitchId datapath, Integer portNumber, int outerVlanId, int innerVlanId) {
        this(datapath, portNumber, outerVlanId, innerVlanId, false);
    }

    @JsonCreator
    @Builder(toBuilder = true)
    public FlowEndpoint(
            @JsonProperty("datapath") SwitchId datapath,
            @JsonProperty("port_number") Integer portNumber,
            @JsonProperty("outer_vlan_id") int outerVlanId,
            @JsonProperty("inner_vlan_id") int innerVlanId,
            @JsonProperty("track_connected_devices") boolean trackConnectedDevices) {
        super(datapath, portNumber);

        this.trackConnectedDevices = trackConnectedDevices;

        // normalize VLANs representation
        List<Integer> vlanStack = makeVlanStack(innerVlanId, outerVlanId);
        if (1 < vlanStack.size()) {
            this.outerVlanId = vlanStack.get(1);
            this.innerVlanId = vlanStack.get(0);
        } else if (!vlanStack.isEmpty()) {
            this.outerVlanId = vlanStack.get(0);
            this.innerVlanId = 0;
        } else {
            this.outerVlanId = 0;
            this.innerVlanId = 0;
        }
    }

    @JsonIgnore
    public List<Integer> getVlanStack() {
        return makeVlanStack(innerVlanId, outerVlanId);
    }

    /**
     * Scan provided sequence for valid VLAN IDs and return them as a list.
     */
    public static List<Integer> makeVlanStack(Integer... sequence) {
        return Stream.of(sequence)
                .filter(FlowEndpoint::isVlanIdSet)
                .collect(Collectors.toList());
    }

    public static boolean isVlanIdSet(Integer vlanId) {
        return vlanId != null && 0 < vlanId;
    }
}
