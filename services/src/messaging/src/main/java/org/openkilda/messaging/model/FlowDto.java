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

package org.openkilda.messaging.model;

import org.openkilda.messaging.Utils;
import org.openkilda.messaging.payload.flow.FlowEncapsulationType;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.messaging.payload.flow.FlowState;
import org.openkilda.messaging.payload.flow.FlowStatusDetails;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

@Data
@EqualsAndHashCode(exclude = {"ignoreBandwidth", "periodicPings", "cookie", "createdTime", "lastUpdated", "meterId",
        "transitEncapsulationId"})
public class FlowDto implements Serializable {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    private static final long MASK_COOKIE_FLAGS = 0x0000_0000_FFFF_FFFFL;

    /**
     * Flow id.
     */
    @JsonProperty(Utils.FLOW_ID)
    private String flowId;

    /**
     * FLow bandwidth.
     */
    @JsonProperty("bandwidth")
    private long bandwidth;

    /**
     * Should flow ignore bandwidth in path computation.
     */
    @JsonProperty("ignore_bandwidth")
    private boolean ignoreBandwidth;

    @JsonProperty("periodic-pings")
    private boolean periodicPings;

    @JsonProperty("allocate_protected_path")
    private boolean allocateProtectedPath;

    /**
     * Flow cookie.
     */
    @JsonProperty("cookie")
    private long cookie;

    /**
     * Flow description.
     */
    @JsonProperty("description")
    private String description;

    @JsonProperty("created_time")
    private String createdTime;

    /**
     * Flow last updated timestamp.
     */
    @JsonProperty("last_updated")
    private String lastUpdated;

    /**
     * Flow source switch.
     */
    @JsonProperty("src_switch")
    private SwitchId sourceSwitch;

    /**
     * Flow destination switch.
     */
    @JsonProperty("dst_switch")
    private SwitchId destinationSwitch;

    /**
     * Flow source port.
     */
    @JsonProperty("src_port")
    private int sourcePort;

    /**
     * Flow destination port.
     */
    @JsonProperty("dst_port")
    private int destinationPort;

    /**
     * Flow source vlan id.
     */
    @JsonProperty("src_vlan")
    private int sourceVlan;

    /**
     * Flow destination vlan id.
     */
    @JsonProperty("dst_vlan")
    private int destinationVlan;

    @JsonProperty("detect_connected_devices")
    private DetectConnectedDevicesDto detectConnectedDevices
            = new DetectConnectedDevicesDto(false, false, false, false, false, false);

    /**
     * Flow source meter id.
     */
    @JsonProperty("meter_id")
    private Integer meterId;

    /**
     * Flow transit encapsulation id.
     */
    @JsonProperty("transit_encapsulation_id")
    private int transitEncapsulationId;

    /**
     * Flow state.
     */
    @JsonProperty("state")
    private FlowState state;

    @JsonProperty("status_details")
    private FlowStatusDetails flowStatusDetails;

    @JsonProperty("max_latency")
    private Integer maxLatency;

    @JsonProperty("priority")
    private Integer priority;

    @JsonProperty("pinned")
    private boolean pinned;

    @JsonProperty("encapsulation_type")
    private FlowEncapsulationType encapsulationType;

    @JsonProperty("path_computation_strategy")
    private PathComputationStrategy pathComputationStrategy;

    public FlowDto() {
    }

    /**
     * Instance constructor.
     *
     * @param flowId                    flow id
     * @param bandwidth                 bandwidth
     * @param ignoreBandwidth           ignore bandwidth flag
     * @param periodicPings             enable periodic pings
     * @param allocateProtectedPath     allocate protected flow path.
     * @param cookie                    cookie
     * @param description               description
     * @param createdTime               flow created timestamp
     * @param lastUpdated               last updated timestamp
     * @param sourceSwitch              source switch
     * @param destinationSwitch         destination switch
     * @param sourcePort                source port
     * @param destinationPort           destination port
     * @param sourceVlan                source vlan id
     * @param destinationVlan           destination vlan id
     * @param meterId                   meter id
     * @param transitEncapsulationId    transit vlan id
     * @param state                     flow state
     * @param maxLatency                max latency
     * @param priority                  flow priority
     * @param pinned                    pinned flag
     * @param encapsulationType         flow encapsulation type
     * @param detectConnectedDevices    detectConnectedDevices flags
     * @param pathComputationStrategy   path computation strategy
     */
    @JsonCreator
    @Builder(toBuilder = true)
    public FlowDto(@JsonProperty(Utils.FLOW_ID) final String flowId,
                   @JsonProperty("bandwidth") final long bandwidth,
                   @JsonProperty("ignore_bandwidth") boolean ignoreBandwidth,
                   @JsonProperty("periodic-pings") boolean periodicPings,
                   @JsonProperty("allocate_protected_path") boolean allocateProtectedPath,
                   @JsonProperty("cookie") final long cookie,
                   @JsonProperty("description") final String description,
                   @JsonProperty("created_time") String createdTime,
                   @JsonProperty("last_updated") final String lastUpdated,
                   @JsonProperty("src_switch") final SwitchId sourceSwitch,
                   @JsonProperty("dst_switch") final SwitchId destinationSwitch,
                   @JsonProperty("src_port") final int sourcePort,
                   @JsonProperty("dst_port") final int destinationPort,
                   @JsonProperty("src_vlan") final int sourceVlan,
                   @JsonProperty("dst_vlan") final int destinationVlan,
                   @JsonProperty("meter_id") final Integer meterId,
                   @JsonProperty("transit_encapsulation_id") final int transitEncapsulationId,
                   @JsonProperty("state") FlowState state,
                   @JsonProperty("status_details") FlowStatusDetails flowStatusDetails,
                   @JsonProperty("max_latency") Integer maxLatency,
                   @JsonProperty("priority") Integer priority,
                   @JsonProperty("pinned") boolean pinned,
                   @JsonProperty("encapsulation_type") FlowEncapsulationType encapsulationType,
                   @JsonProperty("detect_connected_devices") DetectConnectedDevicesDto detectConnectedDevices,
                   @JsonProperty("path_computation_strategy") PathComputationStrategy pathComputationStrategy) {
        this.flowId = flowId;
        this.bandwidth = bandwidth;
        this.ignoreBandwidth = ignoreBandwidth;
        this.periodicPings = periodicPings;
        this.allocateProtectedPath = allocateProtectedPath;
        this.cookie = cookie;
        this.description = description;
        this.createdTime = createdTime;
        this.lastUpdated = lastUpdated;
        this.sourceSwitch = sourceSwitch;
        this.destinationSwitch = destinationSwitch;
        this.sourcePort = sourcePort;
        this.destinationPort = destinationPort;
        this.sourceVlan = sourceVlan;
        this.destinationVlan = destinationVlan;
        this.transitEncapsulationId = transitEncapsulationId;
        this.meterId = meterId;
        this.state = state;
        this.flowStatusDetails = flowStatusDetails;
        this.maxLatency = maxLatency;
        this.priority = priority;
        this.pinned = pinned;
        this.encapsulationType = encapsulationType;
        setDetectConnectedDevices(detectConnectedDevices);
        this.pathComputationStrategy = pathComputationStrategy;
    }

    /**
     * Instance constructor.
     *
     * @param flowId            flow id
     * @param bandwidth         bandwidth
     * @param ignoreBandwidth   ignore bandwidth flag
     * @param description       description
     * @param sourceSwitch      source switch
     * @param sourcePort        source port
     * @param sourceVlan        source vlan id
     * @param destinationSwitch destination switch
     * @param destinationPort   destination port
     * @param destinationVlan   destination vlan id
     * @param pinned            pinned flag
     * @param detectConnectedDevices detect connected devices flags
     */
    public FlowDto(String flowId,
                   long bandwidth,
                   boolean ignoreBandwidth,
                   String description,
                   SwitchId sourceSwitch, int sourcePort, int sourceVlan,
                   SwitchId destinationSwitch, int destinationPort, int destinationVlan, boolean pinned,
                   DetectConnectedDevicesDto detectConnectedDevices) {
        this(flowId,
                bandwidth,
                ignoreBandwidth,
                false,
                false,
                0,
                description,
                null, null,
                sourceSwitch,
                destinationSwitch,
                sourcePort,
                destinationPort,
                sourceVlan,
                destinationVlan,
                null, 0, null, null, null, null, pinned, null, detectConnectedDevices, null);
    }

    public FlowDto(FlowPayload input) {
        this(input.getId(),
                input.getMaximumBandwidth(),
                input.isIgnoreBandwidth(),
                input.isPeriodicPings(),
                input.isAllocateProtectedPath(),
                0,
                input.getDescription(),
                null, null,
                input.getSource().getDatapath(),
                input.getDestination().getDatapath(),
                input.getSource().getPortNumber(),
                input.getDestination().getPortNumber(),
                input.getSource().getVlanId(),
                input.getDestination().getVlanId(),
                null, 0, null, null,
                input.getMaxLatency(),
                input.getPriority(),
                input.isPinned(),
                input.getEncapsulationType() != null ? FlowEncapsulationType.valueOf(
                        input.getEncapsulationType().toUpperCase()) : null,
                new DetectConnectedDevicesDto(
                        input.getSource().getDetectConnectedDevices().isLldp(),
                        input.getSource().getDetectConnectedDevices().isArp(),
                        input.getDestination().getDetectConnectedDevices().isLldp(),
                        input.getDestination().getDetectConnectedDevices().isArp(), false, false),
                input.getPathComputationStrategy() != null ? PathComputationStrategy.valueOf(
                        input.getPathComputationStrategy().toUpperCase()) : null);
    }

    @JsonIgnore
    public long getFlagglessCookie() {
        return cookie & MASK_COOKIE_FLAGS;
    }

    /**
     * Returns whether this represents a forward flow.
     * The result is based on the cookie value,
     * see {@link FlowDto#cookieMarkedAsFroward} and {@link FlowDto#cookieMarkedAsReversed()}.
     */
    @JsonIgnore
    public boolean isForward() {
        boolean isForward = cookieMarkedAsFroward();
        boolean isReversed = cookieMarkedAsReversed();

        if (isForward && isReversed) {
            throw new IllegalArgumentException(
                    "Invalid cookie flags combinations - it mark as forward and reverse flow at same time.");
        }

        return isForward;
    }

    @JsonIgnore
    public boolean isReverse() {
        return !isForward();
    }

    private boolean cookieMarkedAsFroward() {
        boolean isMatch;

        if ((cookie & 0xE000000000000000L) != 0) {
            isMatch = (cookie & 0x4000000000000000L) != 0;
        } else {
            isMatch = (cookie & 0x0080000000000000L) == 0;
        }
        return isMatch;

    }

    private boolean cookieMarkedAsReversed() {
        boolean isMatch;
        if ((cookie & 0xE000000000000000L) != 0) {
            isMatch = (cookie & 0x2000000000000000L) != 0;
        } else {
            isMatch = (cookie & 0x0080000000000000L) != 0;
        }
        return isMatch;
    }

    /**
     * Checks creation params to figure out whether they are aligned or not.
      * @return validation result
     */
    @JsonIgnore
    public boolean isValid() {
        if (isAllocateProtectedPath() && isPinned()) {
            return false;
        }
        return true;
    }

    /**
     * Set connected devices flags.
     */
    public void setDetectConnectedDevices(DetectConnectedDevicesDto detectConnectedDevices) {
        if (detectConnectedDevices == null) {
            this.detectConnectedDevices = new DetectConnectedDevicesDto(false, false, false, false, false, false);
        } else {
            this.detectConnectedDevices = detectConnectedDevices;
        }
    }
}
