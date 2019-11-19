package org.openkilda.functionaltests.spec.flows


import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.IterationTag
import org.openkilda.functionaltests.extension.tags.IterationTags
import org.openkilda.functionaltests.extension.tags.Tag
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.model.SwitchPair
import org.openkilda.functionaltests.spec.flows.FlowConnectedDeviceSpec.ConnectedDeviceTestData
import org.openkilda.messaging.info.meter.MeterEntry
import org.openkilda.messaging.info.rule.FlowEntry
import org.openkilda.messaging.payload.flow.DetectConnectedDevicesPayload
import org.openkilda.messaging.payload.flow.FlowPayload
import org.openkilda.model.Cookie
import org.openkilda.model.Flow
import org.openkilda.model.FlowEncapsulationType
import org.openkilda.model.LldpResources
import org.openkilda.model.MeterId
import org.openkilda.model.SwitchFeature
import org.openkilda.model.SwitchId
import org.openkilda.northbound.dto.v1.flows.ConnectedDeviceDto
import org.openkilda.northbound.dto.v1.switches.SwitchPropertiesDto
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.traffexam.TraffExamService
import org.openkilda.testing.service.traffexam.model.LldpData
import org.openkilda.testing.tools.ConnectedDevice

import groovy.transform.Memoized
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Narrative
import spock.lang.See
import spock.lang.Unroll

import javax.inject.Provider

@Slf4j
@Narrative("""
Verify ability to detect connected devices per flow endpoint (src/dst). 
Verify allocated Connected Devices resources and installed rules.""")
@See("https://github.com/telstra/open-kilda/tree/develop/docs/design/connected-devices-lldp")
class SwitchConnectedDeviceSpec extends HealthCheckSpecification {

    @Autowired
    Provider<TraffExamService> traffExamProvider

    @Unroll
    @Tags([Tag.TOPOLOGY_DEPENDENT])
    @IterationTags([
            @IterationTag(tags = [Tag.SMOKE], iterationNameRegex = /srcLldp=true and dstLldp=true/),
            @IterationTag(tags = [Tag.HARDWARE], iterationNameRegex = /VXLAN/)
    ])
    def "Able to create switch lldp"() {
        given: "A flow with enabled or disabled connected devices"
        def tgService = traffExamProvider.get()
        def sw = topology.activeSwitches.find {it.dpId == new SwitchId(3)}

        when: "Create a flow with connected devices"
        SwitchPropertiesDto switchProperties = new SwitchPropertiesDto()
        switchProperties.multiTable = true
        switchProperties.switchLldp = true
        switchProperties.supportedTransitEncapsulation = [FlowEncapsulationType.TRANSIT_VLAN.toString().toLowerCase()]
        northbound.updateSwitchProperties(sw.dpId, switchProperties)

        def lldp1 = LldpData.buildRandom()
        def lldp2 = LldpData.buildRandom()
        def lldp3 = LldpData.buildRandom()

        and: "Two devices send lldp packet on each flow endpoint"
        for (int i = 0; i < 50; i++) {
            new ConnectedDevice(tgService, topology.getTraffGen(sw.dpId), 15).withCloseable {
                it.sendLldp(lldp1)
            }
            new ConnectedDevice(tgService, topology.getTraffGen(sw.dpId), 20).withCloseable {
                it.sendLldp(lldp2)
            }
            new ConnectedDevice(tgService, topology.getTraffGen(sw.dpId), 25).withCloseable {
                it.sendLldp(lldp3)
            }
        }

        then: "Getting connecting devices shows corresponding devices on each endpoint if enabled"
        true
    }

    @Unroll
    @Tags([Tag.TOPOLOGY_DEPENDENT])
    @IterationTags([
            @IterationTag(tags = [Tag.SMOKE], iterationNameRegex = /srcLldp=true and dstLldp=true/),
            @IterationTag(tags = [Tag.HARDWARE], iterationNameRegex = /VXLAN/)
    ])
    def "Able to create switch lldp for flow"() {
        given: "A flow with enabled or disabled connected devices"
        def tgService = traffExamProvider.get()
        def srcSwitch = topology.activeSwitches.find {it.dpId == new SwitchId(8)}
        def dstSwitch = topology.activeSwitches.find {it.dpId == new SwitchId(9)}

        when: "Create a flow with connected devices"
        SwitchPropertiesDto switchProperties = new SwitchPropertiesDto()
        switchProperties.multiTable = true
        switchProperties.switchLldp = true
        switchProperties.supportedTransitEncapsulation = [FlowEncapsulationType.TRANSIT_VLAN.toString().toLowerCase()]
        northbound.updateSwitchProperties(srcSwitch.dpId, switchProperties)

        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch, true)
        flow.encapsulationType = FlowEncapsulationType.TRANSIT_VLAN
        def createdFlow = flowHelper.addFlow(flow)

        def lldp1 = LldpData.buildRandom()
        def lldp2 = LldpData.buildRandom()

        and: "Two devices send lldp packet on each flow endpoint"
        for (int i = 0; i < 50; i++) {
            new ConnectedDevice(tgService, topology.getTraffGen(srcSwitch.dpId), createdFlow.source.vlanId).withCloseable {
                it.sendLldp(lldp1)
            }
            new ConnectedDevice(tgService, topology.getTraffGen(srcSwitch.dpId), 999).withCloseable {
                it.sendLldp(lldp2)
            }
        }

        then: "Getting connecting devices shows corresponding devices on each endpoint if enabled"
        true
    }

    /**
     * Returns a potential flow for creation according to passed params.
     * Note that for 'oneSwitch' it will return a single-port single-switch flow. There is no ability to obtain
     * single-switch different-port flow via this method.
     */
    private FlowPayload getFlowWithConnectedDevices(
            boolean protectedFlow, boolean oneSwitch, boolean srcEnabled, boolean dstEnabled, SwitchPair switchPair) {
        assert !(oneSwitch && protectedFlow), "Cannot create one-switch flow with protected path"
        def flow = null
        if (oneSwitch) {
            flow = flowHelper.singleSwitchSinglePortFlow(switchPair.src)
        } else {
            flow = flowHelper.randomFlow(switchPair)
            flow.allocateProtectedPath = protectedFlow
        }
        flow.source.detectConnectedDevices = new DetectConnectedDevicesPayload(srcEnabled, false)
        flow.destination.detectConnectedDevices = new DetectConnectedDevicesPayload(dstEnabled, false)
        return flow
    }

    private FlowPayload getFlowWithConnectedDevices(
            boolean protectedFlow, boolean oneSwitch, boolean srcEnabled, boolean dstEnabled) {
        def tgSwPair = getUniqueSwitchPairs()[0]
        assert tgSwPair, "Unable to find a switchPair with traffgens for the requested flow arguments"
        getFlowWithConnectedDevices(protectedFlow, oneSwitch, srcEnabled, dstEnabled, tgSwPair)
    }


    private FlowPayload getFlowWithConnectedDevices(ConnectedDeviceTestData testData) {
        getFlowWithConnectedDevices(testData.protectedFlow, testData.oneSwitch, testData.srcEnabled,
                testData.dstEnabled, testData.switchPair)
    }

    /**
     * Pick as little as possible amount of switch pairs to cover all unique switch models we have (only connected
     * to traffgens and lldp-enabled).
     */
    @Memoized
    List<SwitchPair> getUniqueSwitchPairs() {
        def tgSwitches = topology.activeTraffGens*.switchConnected
                                 .findAll { it.features.contains(SwitchFeature.MULTI_TABLE) }
        def unpickedTgSwitches = tgSwitches.unique(false) { [it.description, it.details.hardware].sort() }
        List<SwitchPair> switchPairs = topologyHelper.switchPairs.collectMany { [it, it.reversed] }.findAll {
            it.src in tgSwitches && it.dst in tgSwitches
        }
        def result = []
        while (!unpickedTgSwitches.empty) {
            def pair = switchPairs.sort(false) { switchPair ->
                //prioritize swPairs with unique traffgens on both sides
                [switchPair.src, switchPair.dst].count { Switch sw ->
                    !unpickedTgSwitches.contains(sw)
                }
            }.first()
            //pick first pair and then re-sort considering updated list of unpicked switches
            result << pair
            unpickedTgSwitches = unpickedTgSwitches - pair.src - pair.dst
        }
        return result
    }

    private void validateFlowAndSwitches(Flow flow) {
        northbound.validateFlow(flow.flowId).each { assert it.asExpected }
        [flow.srcSwitch, flow.destSwitch].each {
            def validation = northbound.validateSwitch(it.switchId)
            switchHelper.verifyRuleSectionsAreEmpty(validation, ["missing", "excess"])
            if (it.ofVersion != "OF_12") {
                switchHelper.verifyMeterSectionsAreEmpty(validation, ["missing", "misconfigured", "excess"])
            }
        }
    }

    private void validateLldpMeters(Flow flow, boolean source) {
        def sw = source ? flow.srcSwitch : flow.destSwitch
        if (sw.ofVersion == "OF_12") {
            return //meters are not supported
        }
        def lldpEnabled = source ? flow.detectConnectedDevices.srcLldp : flow.detectConnectedDevices.dstLldp
        def path = source ? flow.forwardPath : flow.reversePath

        def nonDefaultMeters = northbound.getAllMeters(sw.switchId).meterEntries.findAll {
            !MeterId.isMeterIdOfDefaultRule(it.meterId)
        }
        assert nonDefaultMeters.size() == getExpectedNonDefaultMeterCount(flow, source)

        validateLldpMeter(nonDefaultMeters, path.lldpResources, lldpEnabled)

        if (flow.allocateProtectedPath) {
            def protectedPath = source ? flow.protectedForwardPath : flow.protectedReversePath
            validateLldpMeter(nonDefaultMeters, protectedPath.lldpResources, lldpEnabled)
        }
    }

    private static void validateLldpMeter(List<MeterEntry> meters, LldpResources lldpResources, boolean lldpEnabled) {
        if (lldpEnabled) {
            assert meters.count { it.meterId == lldpResources.meterId.value } == 1
        } else {
            assert lldpResources == null
        }
    }

    private void validateLldpRulesOnSwitch(Flow flow, boolean source) {
        def switchId = source ? flow.srcSwitch.switchId : flow.destSwitch.switchId
        def lldpEnabled = source ? flow.detectConnectedDevices.srcLldp : flow.detectConnectedDevices.dstLldp
        def path = source ? flow.forwardPath : flow.reversePath

        def allRules = northbound.getSwitchRules(switchId).flowEntries
        assert allRules.count { it.tableId == 1 } == getExpectedLldpRulesCount(flow, source)


        validateRules(allRules, path.cookie, path.lldpResources, lldpEnabled, false)

        if (flow.allocateProtectedPath) {
            def protectedPath = source ? flow.protectedForwardPath : flow.protectedReversePath
            validateRules(allRules, protectedPath.cookie, protectedPath.lldpResources, lldpEnabled, true)
        }
    }

    private static void validateRules(List<FlowEntry> allRules, Cookie flowCookie, LldpResources lldpResources,
            boolean lldpEnabled, boolean protectedPath) {
        def ingressRules = allRules.findAll { it.cookie == flowCookie.value }
        if (protectedPath) {
            assert ingressRules.size() == 0
        } else {
            assert ingressRules.size() == 1
            assert ingressRules[0].instructions.goToTable == (lldpEnabled ? 1 : null)
            assert ingressRules[0].tableId == 0
        }

        def lldpRules = allRules.findAll { it.tableId == 1 }
        if (lldpEnabled) {
            assert lldpRules.count { it.cookie == lldpResources.cookie.value } == 1
        } else {
            assert lldpResources == null
        }
    }

    private void validateSwitchHasNoFlowRulesAndMeters(SwitchId switchId) {
        assert northbound.getSwitchRules(switchId).flowEntries.count { !Cookie.isDefaultRule(it.cookie) } == 0
        assert northbound.getAllMeters(switchId).meterEntries.count { !MeterId.isMeterIdOfDefaultRule(it.meterId) } == 0
    }

    private static int getExpectedNonDefaultMeterCount(Flow flow, boolean source) {
        int count = 0
        if (mustHaveLldp(flow, source)) {
            count += 1
        }
        if (flow.oneSwitchFlow && mustHaveLldp(flow, !source)) {
            count += 1
        }
        if (flow.allocateProtectedPath) {
            count *= 2
        }
        if (flow.bandwidth > 0) {
            count += flow.oneSwitchFlow ? 2 : 1
        }
        return count
    }

    private static int getExpectedLldpRulesCount(Flow flow, boolean source) {
        int count = 0
        if (mustHaveLldp(flow, source)) {
            count += 1
        }
        if (flow.oneSwitchFlow && mustHaveLldp(flow, !source)) {
            count += 1
        }
        if (flow.allocateProtectedPath) {
            count *= 2
        }
        return count
    }

    private static boolean mustHaveLldp(Flow flow, boolean source) {
        return (source && flow.detectConnectedDevices.srcLldp) || (!source && flow.detectConnectedDevices.dstLldp)
    }

    def verifyEquals(ConnectedDeviceDto device, LldpData lldp) {
        assert device.macAddress == lldp.macAddress
        assert device.chassisId == "Mac Addr: $lldp.chassisId" //for now TG sends it as hardcoded 'mac address' subtype
        assert device.portId == "Locally Assigned: $lldp.portNumber" //subtype also hardcoded for now on traffgen side
        assert device.ttl == lldp.timeToLive
        //other non-mandatory lldp fields are out of scope for now. Most likely they are not properly parsed
        return true
    }
}
