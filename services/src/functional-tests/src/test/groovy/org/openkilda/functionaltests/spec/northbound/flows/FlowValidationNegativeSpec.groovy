package org.openkilda.functionaltests.spec.northbound.flows

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslInfoData
import org.openkilda.messaging.info.rule.FlowEntry
import org.openkilda.messaging.info.rule.SwitchFlowEntries
import org.openkilda.messaging.model.Flow
import org.openkilda.messaging.model.SwitchId
import org.openkilda.northbound.dto.flows.FlowValidationDto
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.database.Database
import org.springframework.beans.factory.annotation.Autowired

import spock.lang.Unroll


class FlowValidationNegativeSpec extends BaseSpecification {

    @Autowired
    PathHelper pathHelper

    @Autowired
    Database database


    boolean isFlowValid(FlowValidationDto flow, boolean ignoreMeters=true) {
        if (ignoreMeters) {
            return flow.discrepancies.findAll {it.field != "meterId"} .empty
        }
        return flow.discrepancies.empty
    }

    /**
     * Returns list of missing flow cookies on all affected switches
     * @param flow Result of flow validation
     * @return Map<dpId, cookie>
     */
    Map<String, String> findRulesDiscrepancies(FlowValidationDto flow) {
        def discrepancies = flow.discrepancies.findAll {it.field != "meterId"}
        def cookies = [:]
        discrepancies.each { disc ->
            def dpId = (disc.rule =~ /sw:(.*?),/)[0][1]
            def cookie = (disc.rule =~ /ck:(.*?),/)[0][1]
            cookies[dpId] = cookie
        }
        return cookies
    }

    boolean findDirectLinks(Switch src, Switch dst, List<IslInfoData> links) {
        def connectingLinks = links.findAll {link ->
            (link.path[0].switchId == src.dpId) && (link.path[-1].switchId == dst.dpId)
        }
        return connectingLinks.empty
    }

    def getNonNeighbouringSwitches() {
        def islInfoData = northbound.getAllLinks()
        def switches = topologyDefinition.getActiveSwitches()
        def differentSwitches = [switches, switches].combinations().findAll {src, dst -> src.dpId != dst.dpId}

        return differentSwitches.find {src, dst -> findDirectLinks(src, dst, islInfoData)}
    }

    def getNeighbouringSwitches() {
        def switches = topologyDefinition.getActiveSwitches()
        def islInfoData = northbound.getAllLinks()
        return [switches, switches].combinations().unique().find { src, dst -> !findDirectLinks(src, dst, islInfoData) }
    }

    def getSingleSwitch() {
        def switches = topologyDefinition.getActiveSwitches()
        return [switches.first(), switches.first()]
    }

    @Unroll
    def "Flow validation should fail if #rule switch rule is deleted"() {
        given: "Flow with a transit switch exists"

        def (Switch srcSwitch, Switch dstSwitch) = getNonNeighbouringSwitches()
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow = northbound.addFlow(flow)
        assert flow?.id
        assert Wrappers.wait(20) { "up".equalsIgnoreCase(northbound.getFlow(flow.id).status) }
        def forward = database.getFlow(flow.id)?.left
        def involvedSwitches = pathHelper.getInvolvedSwitches(flow.id)*.dpId

        and: "Rules are installed on all switches"
        assert involvedSwitches.every{ sw ->
            northbound.getSwitchRules(sw).flowEntries.find { it.cookie == forward.cookie }
        }
        when: "${rule} switch rule is deleted"
        def dpId = involvedSwitches[item]
        def cookie = forward.cookie.toString()
        northbound.deleteSwitchRules(dpId, cookie)

        def validationResult = northbound.validateFlow(flow.id)
        then: "Forward and reverse flows should be validated"
        validationResult.size() == 2
        and: "Reverse flow validation must be successful"
        validationResult.any { isFlowValid(it) }
        and: "Forward flow validation should fail"
        def fwdFlow = validationResult.find { !isFlowValid(it) }
        and: "Only one rule should be missing"
        def rules = findRulesDiscrepancies(fwdFlow)
        rules.keySet().size() == 1
        rules[dpId.toString()] == cookie
        cleanup: "Delete the flow"
        if (flow){
            northbound.deleteFlow(flow.id)
        }
        where:
        rule << ["ingress", "transit", "egress"]
        item << [0, 1, -1]
    }

    @Unroll
    def "Both flow and switch validation should fail if flow rule is missing with #flowconfig flow configuration"() {
        given: "A ${flowconfig} flow exists"
        def (src, dest) = method()
        def flow = flowHelper.randomFlow(src, dest)
        assert flow?.id

       flow = northbound.addFlow(flow)
        assert Wrappers.wait(20) { "up".equalsIgnoreCase(northbound.getFlow(flow.id).status) }
        def involvedSwitches = pathHelper.getInvolvedSwitches(flow.id)*.dpId
        Flow forward = database.getFlow(flow.id).left

        when: "A flow rule gets deleted"
        northbound.deleteSwitchRules(involvedSwitches[item], forward.cookie.toString())

        then: "Flow and switch validation should fail"
        northbound.validateFlow(flow.id).findAll {isFlowValid(it)} .size() == 1
        northbound.validateFlow(flow.id).findAll {!isFlowValid(it)} .size() == 1

        northbound.validateSwitchRules(involvedSwitches[item]).missingRules.size() > 0
        cleanup: "Delete the flow"
        if (flow) {
            northbound.deleteFlow(flow.id)
        }
        where:
        flowconfig << ["single switch", "neighbouring", "transit"]
        method << [{ getSingleSwitch() }, { getNeighbouringSwitches() }, { getNonNeighbouringSwitches() }]
        //ingress (for single-switch), egress (for neighbouring) and transit (for non-neighbouring) rules.
        item << [0, 1, 1]
    }

    def "Missing #flowtype flow rules on a switch should not prevent intact flows from successful validation"() {
        given: "Two flows with same switches in path exist"
        def src = topologyDefinition.getActiveSwitches()[0]
        def flow1 = flowHelper.singleSwitchFlow(src)
        assert flow1?.id
        flow1 = northbound.addFlow(flow1)
        Flow damagedFlow
        Wrappers.wait(20) { "up".equalsIgnoreCase(northbound.getFlow(flow1.id).status) }
        def flow2 = flowHelper.singleSwitchFlow(src)
        flow2 = northbound.addFlow(flow2)
        assert flow2?.id
        Wrappers.wait(20) { "up".equalsIgnoreCase(northbound.getFlow(flow2.id).status) }

        if (flowType == "forward") {
            damagedFlow = database.getFlow(flow1.id).left
        } else {
            damagedFlow = database.getFlow(flow1.id).right
        }
        def flowRules = northbound.getSwitchRules(src.dpId).flowEntries.findAll {it.cookie == damagedFlow.cookie}
        assert flowRules.size() == 1

        when: "${flowtype} flow rule from flow #1 gets deleted"
        northbound.deleteSwitchRules(src.dpId, damagedFlow.cookie.toString())
        then: "Flow #2 should be validated successfully"
        northbound.validateFlow(flow2.id).every {isFlowValid(it)}
        and: "Flow #1 validation should fail"
        northbound.validateFlow(flow1.id).findAll {isFlowValid(it)} .size() == 1
        northbound.validateFlow(flow1.id).findAll {!isFlowValid(it)} .size() == 1
        and: "Switch validation should fail"
        northbound.validateSwitchRules(src.dpId).missingRules.size() > 0
        cleanup: "Delete the flows"
        if (flow1) {
            northbound.deleteFlow(flow1.id)
        }
        if (flow2) {
            northbound.deleteFlow(flow2.id)
        }
        where:
        flowtype << ["forward", "reverse"]
    }
}