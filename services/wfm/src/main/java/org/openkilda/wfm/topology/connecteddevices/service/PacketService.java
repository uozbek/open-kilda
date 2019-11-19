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

package org.openkilda.wfm.topology.connecteddevices.service;

import static org.openkilda.model.ConnectedDeviceType.LLDP;

import org.openkilda.messaging.info.event.LldpInfoData;
import org.openkilda.messaging.info.event.SwitchLldpInfoData;
import org.openkilda.model.ConnectedDevice;
import org.openkilda.model.Cookie;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowCookie;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchConnectedDevice;
import org.openkilda.model.SwitchId;
import org.openkilda.model.TransitVlan;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.ConnectedDeviceRepository;
import org.openkilda.persistence.repositories.FlowCookieRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.SwitchConnectedDeviceRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.persistence.repositories.TransitVlanRepository;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Optional;

@Slf4j
public class PacketService {
    public static final int FULL_PORT_VLAN = 0;
    private TransactionManager transactionManager;
    private FlowCookieRepository flowCookieRepository;
    private SwitchRepository switchRepository;
    private ConnectedDeviceRepository connectedDeviceRepository;
    private SwitchConnectedDeviceRepository switchConnectedDeviceRepository;
    private TransitVlanRepository transitVlanRepository;
    private FlowRepository flowRepository;

    public PacketService(PersistenceManager persistenceManager) {
        transactionManager = persistenceManager.getTransactionManager();
        flowCookieRepository = persistenceManager.getRepositoryFactory().createFlowCookieRepository();
        switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();
        connectedDeviceRepository = persistenceManager.getRepositoryFactory().createConnectedDeviceRepository();
        switchConnectedDeviceRepository = persistenceManager.getRepositoryFactory()
                .createSwitchConnectedDeviceRepository();
        transitVlanRepository = persistenceManager.getRepositoryFactory().createTransitVlanRepository();
        flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
    }

    /**
     * Handle LLDP info data.
     */
    public void handleFlowLldpData(LldpInfoData data) {
        transactionManager.doInTransaction(() -> {
            Cookie cookie = new Cookie(data.getCookie());
            Optional<FlowCookie> flowCookie = flowCookieRepository.findByCookie(cookie.getUnmaskedValue());
            if (!flowCookie.isPresent()) {
                log.warn("Couldn't find flow by cookie {}", cookie);
                return;
            }

            String flowId = flowCookie.get().getFlowId();
            boolean isForward = cookie.isMaskedAsForward();

            Instant now = Instant.now();
            ConnectedDevice device = connectedDeviceRepository
                    .findByUniqueFieldCombination(
                            flowId, isForward, data.getMacAddress(), LLDP, data.getChassisId(), data.getPortId())
                    .orElse(ConnectedDevice.builder()
                            .flowId(flowId)
                            .source(isForward)
                            .macAddress(data.getMacAddress())
                            .timeFirstSeen(now)
                            .type(LLDP)
                            .chassisId(data.getChassisId())
                            .portId(data.getPortId())
                            .build());

            device.setTtl(data.getTtl());
            device.setPortDescription(data.getPortDescription());
            device.setSystemName(data.getSystemName());
            device.setSystemDescription(data.getSystemDescription());
            device.setSystemCapabilities(data.getSystemCapabilities());
            device.setManagementAddress(data.getManagementAddress());
            device.setTimeLastSeen(now);
            device.setType(LLDP);

            connectedDeviceRepository.createOrUpdate(device);
        });
    }

    /**
     * Handle Switch LLDP info data.
     */
    public void handleSwitchLldpData(SwitchLldpInfoData data) {
        transactionManager.doInTransaction(() -> {

            int vlan = data.getVlan();
            String flowId = null;

            if (data.getCookie() == Cookie.LLDP_POST_INGRESS_COOKIE) {
                Flow flow = findFlowByTransitVlan(vlan);

                if (flow != null) {
                    flowId = flow.getFlowId();
                    if (data.getSwitchId().equals(flow.getSrcSwitch().getSwitchId())) {
                        vlan = flow.getSrcVlan();
                    } else if (data.getSwitchId().equals(flow.getDestSwitch().getSwitchId())) {
                        vlan = flow.getDestVlan();
                    } else {
                        log.warn("Got LLDP packet from Flow {} on non-src/non-dst switch {}. Transit vlan: {}",
                                flowId, data.getSwitchId(), vlan);
                        return;
                    }
                }
            } else if (data.getCookie() == Cookie.LLDP_POST_INGRESS_VXLAN_COOKIE) {
                flowId = getFlowIdForLldpVxlan(data.getSwitchId(), data.getPortNumber(), data.getVlan());
            }

            Instant now = Instant.now();
            SwitchConnectedDevice device = getOrBuildSwitchDevice(data, vlan, now);

            if (device == null) {
                return;
            }

            device.setTtl(data.getTtl());
            device.setPortDescription(data.getPortDescription());
            device.setSystemName(data.getSystemName());
            device.setSystemDescription(data.getSystemDescription());
            device.setSystemCapabilities(data.getSystemCapabilities());
            device.setManagementAddress(data.getManagementAddress());
            device.setTimeLastSeen(now);
            device.setFlowId(flowId);

            switchConnectedDeviceRepository.createOrUpdate(device);
        });
    }

    private Flow findFlowByTransitVlan(int vlan) {
        Optional<TransitVlan> transitVlan = transitVlanRepository.findByVlan(vlan);

        if (!transitVlan.isPresent()) {
            log.info("Couldn't find flow encapsulation resources by Transit vlan '{}", vlan);
            return null;
        }
        Optional<Flow> flow = flowRepository.findById(transitVlan.get().getFlowId());
        if (!flow.isPresent()) {
            log.warn("Couldn't find flow by flow ID '{}", transitVlan.get().getFlowId());
            return null;
        }
        return flow.get();
    }

    private String getFlowIdForLldpVxlan(SwitchId switchId, int portNumber, int vlan) {
        Optional<Flow> flow = flowRepository.findByEndpointAndVlan(switchId, portNumber, vlan);

        if (flow.isPresent()) {
            return flow.get().getFlowId();
        } else {
            // may be it's a full port flow
            Optional<Flow> fullPortFlow = flowRepository.findByEndpointAndVlan(switchId, portNumber, FULL_PORT_VLAN);
            if (fullPortFlow.isPresent()) {
                return fullPortFlow.get().getFlowId();
            } else {
                log.warn("Couldn't find Flow for VXLAN encapsulated LLDP packet on Switch {}, port {}, vlan {}",
                        switchId, portNumber, vlan);
                return null;
            }
        }
    }

    private SwitchConnectedDevice getOrBuildSwitchDevice(SwitchLldpInfoData data, int vlan, Instant now) {
        Optional<SwitchConnectedDevice> device = switchConnectedDeviceRepository
                .findByUniqueFieldCombination(
                        data.getSwitchId(), data.getPortNumber(), vlan, data.getMacAddress(), LLDP,
                        data.getChassisId(), data.getPortId());

        if (device.isPresent()) {
            return device.get();
        }

        Optional<Switch> sw = switchRepository.findById(data.getSwitchId());

        if (!sw.isPresent()) {
            log.warn("Got LLDP packet from non existent switch {}. Port number '{}', vlan '{}', mac address '{}', "
                            + "chassis id '{}', port id '{}'", data.getSwitchId(), data.getPortNumber(), data.getVlan(),
                    data.getMacAddress(), data.getChassisId(), data.getPortId());
            return null;
        }

        return SwitchConnectedDevice.builder()
                .switchObj(sw.get())
                .portNumber(data.getPortNumber())
                .vlan(vlan)
                .macAddress(data.getMacAddress())
                .type(LLDP)
                .chassisId(data.getChassisId())
                .portId(data.getPortId())
                .timeFirstSeen(now)
                .build();
    }
}
