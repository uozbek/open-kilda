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

package org.openkilda.wfm.topology.flowhs.service;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

import org.openkilda.floodlight.api.FlowEndpoint;
import org.openkilda.floodlight.api.FlowTransitEncapsulation;
import org.openkilda.floodlight.api.MeterConfig;
import org.openkilda.floodlight.api.request.EgressFlowSegmentInstallRequest;
import org.openkilda.floodlight.api.request.FlowSegmentRequest;
import org.openkilda.floodlight.api.request.SpeakerIngressActModRequest;
import org.openkilda.floodlight.api.request.IngressFlowSegmentInstallRequest;
import org.openkilda.floodlight.api.request.SingleSwitchFlowInstallRequest;
import org.openkilda.floodlight.api.request.TransitFlowSegmentInstallRequest;
import org.openkilda.floodlight.flow.request.RemoveRule;
import org.openkilda.messaging.MessageContext;
import org.openkilda.messaging.command.switches.DeleteRulesCriteria;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPath;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.EncapsulationResources;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import com.google.common.collect.ImmutableList;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class SpeakerFlowSegmentRequestBuilder implements FlowCommandBuilder {
    private final NoArgGenerator commandIdGenerator = Generators.timeBasedGenerator();
    private final FlowResourcesManager resourcesManager;
    private final FlowEncapsulationType encapsulationType;

    public SpeakerFlowSegmentRequestBuilder(FlowResourcesManager resourcesManager,
                                            FlowEncapsulationType encapsulationType) {
        this.resourcesManager = resourcesManager;
        this.encapsulationType = encapsulationType;
    }

    @Override
    public List<FlowSegmentRequest> createInstallNotIngressRequests(CommandContext context, Flow flow) {
        return createInstallNotIngressRequests(context, flow, flow.getForwardPath(), flow.getReversePath());
    }

    @Override
    public List<FlowSegmentRequest> createInstallNotIngressRequests(
            CommandContext context, Flow flow, FlowPath forwardPath, FlowPath reversePath) {
        ensureValidArguments(flow, forwardPath, reversePath);

        if (flow.isOneSwitchFlow()) {
            return Collections.emptyList();
        }

        FlowEndpoint source = getSourceEndpoint(flow);
        FlowEndpoint dest = getDestEndpoint(flow);

        List<FlowSegmentRequest> requests = new ArrayList<>();
        requests.addAll(collectInstallNotIngressRequests(
                context, flow, forwardPath, source, dest,
                getEncapsulation(forwardPath.getPathId(), reversePath.getPathId())));
        requests.addAll(collectInstallNotIngressRequests(
                context, flow, reversePath, dest, source,
                getEncapsulation(reversePath.getPathId(), forwardPath.getPathId())));
        return requests;
    }

    @Override
    public List<FlowSegmentRequest> createInstallIngressRequests(CommandContext context, Flow flow) {
        return createInstallIngressRequests(context, flow, flow.getForwardPath(), flow.getReversePath());
    }

    @Override
    public List<FlowSegmentRequest> createInstallIngressRequests(
            CommandContext context, Flow flow, FlowPath forwardPath, FlowPath reversePath) {
        requireNonNull(flow, "Argument \"flow\" must not be null");
        requireNonNull(forwardPath, "Argument \"forwardPath\" must not be null");
        requireNonNull(forwardPath, "Argument \"reversePath\" must not be null");

        if (flow.isOneSwitchFlow()) {
            return createOneSwitchFlowInstallRequests(context, flow, forwardPath, reversePath);
        } else {
            return createMultiSwitchFlowInstallIngressRequests(context, flow, forwardPath, reversePath);
        }
    }

    @Override
    public List<FlowSegmentRequest> createRemoveNotIngressRules(CommandContext context, Flow flow) {
        return createRemoveNotIngressRules(context, flow, flow.getForwardPath(), flow.getReversePath());
    }

    @Override
    public List<FlowSegmentRequest> createRemoveNotIngressRules(
            CommandContext context, Flow flow, FlowPath forwardPath, FlowPath reversePath) {
        ensureValidArguments(flow, forwardPath, reversePath);
        if (flow.isOneSwitchFlow()) {
            return Collections.emptyList();
        }

        FlowEndpoint source = getSourceEndpoint(flow);
        FlowEndpoint dest = getDestEndpoint(flow);

        List<FlowSegmentRequest> commands = new ArrayList<>();
        commands.addAll(collectRemoveNotIngressRules(
                context, flow, forwardPath, source, dest,
                getEncapsulation(forwardPath.getPathId(), reversePath.getPathId())));
        commands.addAll(collectRemoveNotIngressRules(
                context, flow, reversePath, dest, source,
                getEncapsulation(reversePath.getPathId(), forwardPath.getPathId())));
        return commands;
    }

    @Override
    public List<RemoveRule> createRemoveIngressRules(CommandContext context, Flow flow) {
        return createRemoveIngressRules(context, flow, flow.getForwardPath(), flow.getReversePath());
    }

    @Override
    public List<RemoveRule> createRemoveIngressRules(CommandContext context, Flow flow,
                                                     FlowPath forwardPath, FlowPath reversePath) {
        RemoveRule removeForwardIngress =
                buildRemoveIngressRule(context, forwardPath, flow.getSrcPort(), flow.getSrcVlan(),
                                       getEncapsulation(forwardPath.getPathId(), reversePath.getPathId()));
        RemoveRule removeReverseIngress =
                buildRemoveIngressRule(context, reversePath, flow.getDestPort(), flow.getDestVlan(),
                                       getEncapsulation(reversePath.getPathId(), forwardPath.getPathId()));
        return ImmutableList.of(removeForwardIngress, removeReverseIngress);
    }

    private List<FlowSegmentRequest> createOneSwitchFlowInstallRequests(
            CommandContext context, Flow flow, FlowPath forwardPath, FlowPath reversePath) {
        FlowEndpoint source = getSourceEndpoint(flow);
        FlowEndpoint dest = getDestEndpoint(flow);
        return ImmutableList.of(
                buildInstallOneSwitchFlowRequest(context, forwardPath, source, dest),
                buildInstallOneSwitchFlowRequest(context, reversePath, dest, source));
    }

    private List<FlowSegmentRequest> createMultiSwitchFlowInstallIngressRequests(
            CommandContext context, Flow flow, FlowPath forwardPath, FlowPath reversePath) {

        FlowEndpoint source = getSourceEndpoint(flow);
        FlowEndpoint dest = getDestEndpoint(flow);

        return ImmutableList.of(
                buildInstallIngressRule(
                        context, flow, forwardPath, source,
                        getEncapsulation(forwardPath.getPathId(), reversePath.getPathId())),
                buildInstallIngressRule(
                        context, flow, reversePath, dest,
                        getEncapsulation(reversePath.getPathId(), forwardPath.getPathId())));
    }

    private SingleSwitchFlowInstallRequest buildInstallOneSwitchFlowRequest(
            CommandContext context, FlowPath path, FlowEndpoint endpoint, FlowEndpoint egressEndpoint) {
        UUID commandId = commandIdGenerator.generate();
        MessageContext messageContext = new MessageContext(commandId.toString(), context.getCorrelationId());

        MeterConfig meterConfig = getMeterConfig(path);

        return new SingleSwitchFlowInstallRequest(
                messageContext, commandId, path.getFlow().getFlowId(), path.getCookie(),
                endpoint, meterConfig, egressEndpoint);
    }

    private IngressFlowSegmentInstallRequest buildInstallIngressRule(
            CommandContext context, Flow flow, FlowPath path, FlowEndpoint endpoint,
            FlowTransitEncapsulation encapsulation) {
        ensureFlowPathValid(flow, path);

        UUID commandId = commandIdGenerator.generate();
        MessageContext messageContext = new MessageContext(commandId.toString(), context.getCorrelationId());
        MeterConfig meterConfig = getMeterConfig(path);

        PathSegment ingressSegment = path.getSegments().get(0);
        int islPort = ingressSegment.getSrcPort();

        return new IngressFlowSegmentInstallRequest(
                messageContext, commandId, flow.getFlowId(), path.getCookie(), endpoint, meterConfig, islPort,
                encapsulation);
    }

    private List<FlowSegmentRequest> collectInstallNotIngressRequests(
            CommandContext context, Flow flow, FlowPath path,
            FlowEndpoint start, FlowEndpoint end, FlowTransitEncapsulation encapsulation) {

        ensureFlowPathValid(flow, path);

        List<FlowSegmentRequest> requests = new ArrayList<>(path.getSegments().size() + 1);
        requests.addAll(collectInstallTransitRequests(context, path, encapsulation));
        requests.add(buildInstallEgressRule(context, path, end, start, encapsulation));

        return requests;
    }

    private List<FlowSegmentRequest> collectInstallTransitRequests(
            CommandContext context, FlowPath flowPath, FlowTransitEncapsulation encapsulation) {

        List<PathSegment> segments = flowPath.getSegments();
        List<FlowSegmentRequest> requests = new ArrayList<>(segments.size());
        for (int i = 1; i < segments.size(); i++) {
            PathSegment income = segments.get(i - 1);
            PathSegment outcome = segments.get(i);

            requests.add(buildInstallTransitRequest(
                    context, flowPath, income.getDestSwitch().getSwitchId(), income.getDestPort(),
                    outcome.getSrcPort(), encapsulation));
        }

        return requests;
    }

    private TransitFlowSegmentInstallRequest buildInstallTransitRequest(
            CommandContext context, FlowPath flowPath, SwitchId switchId, int ingressIslPort, int egressIslPort,
            FlowTransitEncapsulation encapsulation) {
        UUID commandId = commandIdGenerator.generate();
        MessageContext messageContext = new MessageContext(commandId.toString(), context.getCorrelationId());
        return new TransitFlowSegmentInstallRequest(
                messageContext, switchId, commandId, flowPath.getFlow().getFlowId(), flowPath.getCookie(),
                ingressIslPort, egressIslPort, encapsulation);
    }

    private EgressFlowSegmentInstallRequest buildInstallEgressRule(
            CommandContext context, FlowPath flowPath,
            FlowEndpoint endpoint, FlowEndpoint ingressEndpoint, FlowTransitEncapsulation encapsulation) {

        List<PathSegment> segments = flowPath.getSegments();
        PathSegment egressSegment = segments.get(segments.size() - 1);
        int islPort = egressSegment.getDestPort();

        UUID commandId = commandIdGenerator.generate();
        MessageContext messageContext = new MessageContext(commandId.toString(), context.getCorrelationId());

        return new EgressFlowSegmentInstallRequest(
                messageContext, commandId, flowPath.getFlow().getFlowId(), flowPath.getCookie(),
                endpoint, ingressEndpoint, islPort, encapsulation);
    }

    private RemoveRule buildRemoveIngressRule(CommandContext context, FlowPath flowPath, int inputPort, int inputVlanId,
                                              EncapsulationResources encapsulationResources) {
        Integer outputPort = flowPath.getSegments().isEmpty() ? null : flowPath.getSegments().get(0).getSrcPort();

        DeleteRulesCriteria ingressCriteria = new DeleteRulesCriteria(flowPath.getCookie().getValue(), inputPort,
                                                                      inputVlanId, 0, outputPort,
                                                                      encapsulationResources.getEncapsulationType(),
                                                                      flowPath.getSrcSwitch().getSwitchId());
        UUID commandId = commandIdGenerator.generate();
        return RemoveRule.builder()
                .messageContext(new MessageContext(commandId.toString(), context.getCorrelationId()))
                .commandId(commandId)
                .flowId(flowPath.getFlow().getFlowId())
                .switchId(flowPath.getSrcSwitch().getSwitchId())
                .cookie(flowPath.getCookie())
                .meterId(flowPath.getMeterId())
                .criteria(ingressCriteria)
                .build();
    }

    private List<RemoveRule> collectRemoveNotIngressRules(
            CommandContext context, Flow flow, FlowPath flowPath, FlowEndpoint start, FlowEndpoint end,
            EncapsulationResources encapsulationResources) {
        if (flowPath == null || CollectionUtils.isEmpty(flowPath.getSegments())) {
            throw new IllegalArgumentException("Flow path with segments is required");
        }

        List<PathSegment> segments = flowPath.getSegments();
        List<RemoveRule> requests = new ArrayList<>(segments.size());

        for (int i = 1; i < segments.size(); i++) {
            PathSegment income = segments.get(i - 1);
            PathSegment outcome = segments.get(i);

            RemoveRule transitRule = buildRemoveTransitRequest(
                    context, flowPath, income.getDestSwitch().getSwitchId(), income.getDestPort(), outcome.getSrcPort(), encapsulationResources);
            requests.add(transitRule);
        }

        PathSegment egressSegment = segments.get(segments.size() - 1);
        if (!egressSegment.getDestSwitch().getSwitchId().equals(flowPath.getDestSwitch().getSwitchId())) {
            throw new IllegalStateException(
                    format("PathSegment was not found for egress flow rule, flowId: %s",
                           flowPath.getFlow().getFlowId()));
        }

        RemoveRule egressRule = buildRemoveEgressRule(context, flowPath,
                                                      egressSegment.getDestPort(), outputPort, encapsulationResources);
        requests.add(egressRule);

        return requests;
    }

    private RemoveRule buildRemoveTransitRequest(
            CommandContext context, FlowPath flowPath, SwitchId switchId, int ingressIslPort, int egressIslPort,
            EncapsulationResources encapsulationResources) {
        DeleteRulesCriteria criteria = new DeleteRulesCriteria(flowPath.getCookie().getValue(), ingressIslPort,
                                                               encapsulationResources.getTransitEncapsulationId(),
                                                               0, outputPort,
                                                               encapsulationResources.getEncapsulationType(),
                                                               flowPath.getSrcSwitch().getSwitchId());
        UUID commandId = commandIdGenerator.generate();
        return RemoveRule.builder()
                .messageContext(new MessageContext(commandId.toString(), context.getCorrelationId()))
                .commandId(commandId)
                .flowId(flowPath.getFlow().getFlowId())
                .cookie(flowPath.getCookie())
                .switchId(switchId)
                .criteria(criteria)
                .build();
    }

    private RemoveRule buildRemoveEgressRule(CommandContext context, FlowPath flowPath, int inputPort, int outputPort,
                                             EncapsulationResources encapsulationResources) {
        DeleteRulesCriteria criteria = new DeleteRulesCriteria(flowPath.getCookie().getValue(), inputPort,
                                                               encapsulationResources.getTransitEncapsulationId(),
                                                               0, outputPort,
                                                               encapsulationResources.getEncapsulationType(),
                                                               flowPath.getSrcSwitch().getSwitchId());
        UUID commandId = commandIdGenerator.generate();
        return RemoveRule.builder()
                .messageContext(new MessageContext(commandId.toString(), context.getCorrelationId()))
                .commandId(commandId)
                .flowId(flowPath.getFlow().getFlowId())
                .cookie(flowPath.getCookie())
                .criteria(criteria)
                .switchId(flowPath.getDestSwitch().getSwitchId())
                .build();
    }

    private void ensureValidArguments(Flow flow, FlowPath forwardPath, FlowPath reversePath) {
        requireNonNull(flow, "Argument \"flow\" must not be null");
        requireNonNull(forwardPath, "Argument \"forwardPath\" must not be null");
        requireNonNull(reversePath, "Argument \"reversePath\" must not be null");
    }

    private void ensureFlowPathValid(Flow flow, FlowPath path) {
        if (path == null) {
            throw new IllegalArgumentException();
        }
        final List<PathSegment> segments = path.getSegments();
        if (CollectionUtils.isEmpty(segments)) {
            throw new IllegalArgumentException(String.format(
                    "Flow path with segments is required (flowId=%s, pathId=%s)", flow.getFlowId(), path.getPathId()));
        }

        if (!isIngressPathSegment(path, segments.get(0))
                || !isEgressPathSegment(path, segments.get(segments.size() - 1))) {
            throw new IllegalArgumentException(String.format(
                    "Flow's path segments do not start on flow endpoints (flowId=%s, pathId=%s)",
                    flow.getFlowId(), path.getPathId()));
        }
    }

    private boolean isIngressPathSegment(FlowPath path, PathSegment segment) {
        return path.getSrcSwitch().getSwitchId().equals(segment.getSrcSwitch().getSwitchId());
    }

    private boolean isEgressPathSegment(FlowPath path, PathSegment segment) {
        return path.getDestSwitch().getSwitchId().equals(segment.getDestSwitch().getSwitchId());
    }

    private MeterConfig getMeterConfig(FlowPath path) {
        if (path.getMeterId() == null) {
            return null;
        }
        return new MeterConfig(path.getMeterId(), path.getBandwidth());
    }

    private FlowTransitEncapsulation getEncapsulation(PathId pathId, PathId oppositePathId) {
        EncapsulationResources resources = resourcesManager
                .getEncapsulationResources(pathId, oppositePathId, encapsulationType)
                .orElseThrow(() -> new IllegalStateException(format(
                        "No encapsulation resources found for flow path %s (opposite: %s)",
                        pathId, oppositePathId)));
        return new FlowTransitEncapsulation(resources.getTransitEncapsulationId(), resources.getEncapsulationType());
    }

    private FlowEndpoint getSourceEndpoint(Flow flow) {
        return new FlowEndpoint(flow.getSrcSwitch().getSwitchId(), flow.getSrcPort(), flow.getSrcVlan());
    }

    private FlowEndpoint getDestEndpoint(Flow flow) {
        return new FlowEndpoint(flow.getDestSwitch().getSwitchId(), flow.getDestPort(), flow.getDestVlan());
    }
}
