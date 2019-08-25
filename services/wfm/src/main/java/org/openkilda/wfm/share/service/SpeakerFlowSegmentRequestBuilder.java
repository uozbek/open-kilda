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

package org.openkilda.wfm.share.service;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.MeterConfig;
import org.openkilda.floodlight.api.request.EgressFlowSegmentBlankRequest;
import org.openkilda.floodlight.api.request.FlowSegmentBlankGenericResolver;
import org.openkilda.floodlight.api.request.IngressFlowSegmentBlankRequest;
import org.openkilda.floodlight.api.request.OneSwitchFlowBlankRequest;
import org.openkilda.floodlight.api.request.TransitFlowSegmentBlankRequest;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPath;
import org.openkilda.model.PathSegment;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.EncapsulationResources;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.topology.flowhs.service.FlowCommandBuilder;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
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
    public List<FlowSegmentBlankGenericResolver> buildAll(
            CommandContext context, Flow flow, FlowPath forwardPath, FlowPath reversePath) {
        return null;
    }

    @Override
    public List<FlowSegmentBlankGenericResolver> buildAllExceptIngress(CommandContext context, Flow flow) {
        return buildAllExceptIngress(context, flow, flow.getForwardPath(), flow.getReversePath());
    }

    @Override
    public List<FlowSegmentBlankGenericResolver> buildAllExceptIngress(
            CommandContext context, Flow flow, FlowPath forwardPath, FlowPath reversePath) {
        return makeAllExceptionIngress(context, flow, forwardPath, reversePath);
    }

    @Override
    public List<FlowSegmentBlankGenericResolver> buildIngressOnly(CommandContext context, Flow flow) {
        return buildIngressOnly(context, flow, flow.getForwardPath(), flow.getReversePath());
    }

    @Override
    public List<FlowSegmentBlankGenericResolver> buildIngressOnly(
            CommandContext context, Flow flow, FlowPath forwardPath, FlowPath reversePath) {
        return makeIngressOnly(context, flow, forwardPath, reversePath);
    }

    private List<FlowSegmentBlankGenericResolver> makeAll(
            CommandContext context, Flow flow, FlowPath path, FlowPath oppositePath) {
        ensureValidArguments(flow, path, oppositePath);

        List<FlowSegmentBlankGenericResolver> requests = new ArrayList<>();
        requests.addAll(makeRequests(
                flow, path, context, getEncapsulation(path, oppositePath), true, true, true));
        requests.addAll(makeRequests(
                flow, oppositePath, context, getEncapsulation(oppositePath, path), true, true, true));
        return requests;
    }

    private List<FlowSegmentBlankGenericResolver> makeAllExceptionIngress(
            CommandContext context, Flow flow, FlowPath path, FlowPath oppositePath) {
        ensureValidArguments(flow, path, oppositePath);

        List<FlowSegmentBlankGenericResolver> requests = new ArrayList<>();
        requests.addAll(makeRequests(
                flow, path, context, getEncapsulation(path, oppositePath), false, true, true));
        requests.addAll(makeRequests(
                flow, oppositePath, context, getEncapsulation(oppositePath, path), false, true, true));
        return requests;
    }

    private List<FlowSegmentBlankGenericResolver> makeIngressOnly(
            CommandContext context, Flow flow, FlowPath path, FlowPath oppositePath) {
        ensureValidArguments(flow, path, oppositePath);

        List<FlowSegmentBlankGenericResolver> requests = new ArrayList<>();
        requests.addAll(makeRequests(
                flow, path, context, getEncapsulation(path, oppositePath), true, false, false));
        requests.addAll(makeRequests(
                flow, oppositePath, context, getEncapsulation(oppositePath, path), true, false, false));
        return requests;
    }

    private List<FlowSegmentBlankGenericResolver> makeRequests(
            Flow flow, FlowPath path, CommandContext context, FlowTransitEncapsulation encapsulation,
            boolean doEnter, boolean doTransit, boolean doExit) {
        ensureFlowPathValid(flow, path);

        List<FlowSegmentBlankGenericResolver> requests = new ArrayList<>();

        FlowEndpoint ingressEndpoint = getIngressEndpoint(flow, path);
        FlowEndpoint egressEndpoint = getEgressEndpoint(flow, path);

        if (doEnter) {
            if (flow.isOneSwitchFlow()) {
                requests.add(makeOneSwitchFlowRequest(path, context, ingressEndpoint, egressEndpoint));
            } else {
                requests.add(makeIngressSegmentRequest(path, context, ingressEndpoint, encapsulation));
            }
        }

        if (doTransit) {
            requests.addAll(makeTransitRequests(path, context, encapsulation));
        }

        if (doExit) {
            requests.add(makeEgressSegmentRequest(
                    path, context, egressEndpoint, ingressEndpoint, encapsulation));
        }

        return requests;
    }

    private List<FlowSegmentBlankGenericResolver> makeTransitRequests(
            FlowPath path, CommandContext context, FlowTransitEncapsulation encapsulation) {
        List<FlowSegmentBlankGenericResolver> requests = new ArrayList<>();
        List<PathSegment> segments = path.getSegments();
        for (int i = 1; i < segments.size(); i++) {
            PathSegment income = segments.get(i - 1);
            PathSegment outcome = segments.get(i);

            requests.add(makeTransitSegmentRequest(
                    path, context, income.getDestSwitch().getSwitchId(), income.getDestPort(),
                    outcome.getSrcPort(), encapsulation));
        }

        return requests;
    }

    private FlowSegmentBlankGenericResolver makeOneSwitchFlowRequest(
            FlowPath path, CommandContext context, FlowEndpoint ingressEndpoint, FlowEndpoint egressEndpoint) {
        UUID commandId = commandIdGenerator.generate();
        MessageContext messageContext = new MessageContext(commandId.toString(), context.getCorrelationId());
        return OneSwitchFlowBlankRequest.buildResolver()
                .messageContext(messageContext)
                .commandId(commandId)
                .flowId(path.getFlow().getFlowId())
                .cookie(path.getCookie())
                .endpoint(ingressEndpoint)
                .meterConfig(getMeterConfig(path))
                .egressEndpoint(egressEndpoint)
                .build().makeGenericResolver();
    }

    private FlowSegmentBlankGenericResolver makeIngressSegmentRequest(
            FlowPath path, CommandContext context, FlowEndpoint endpoint, FlowTransitEncapsulation encapsulation) {
        UUID commandId = commandIdGenerator.generate();
        MessageContext messageContext = new MessageContext(commandId.toString(), context.getCorrelationId());

        PathSegment ingressSegment = path.getSegments().get(0);
        int islPort = ingressSegment.getSrcPort();

        return IngressFlowSegmentBlankRequest.buildResolver()
                .messageContext(messageContext)
                .commandId(commandId)
                .flowId(path.getFlow().getFlowId())
                .cookie(path.getCookie())
                .meterConfig(getMeterConfig(path))
                .endpoint(endpoint)
                .islPort(islPort)
                .encapsulation(encapsulation)
                .build().makeGenericResolver();
    }

    private FlowSegmentBlankGenericResolver makeTransitSegmentRequest(
            FlowPath flowPath, CommandContext context, SwitchId switchId, int ingressIslPort, int egressIslPort,
            FlowTransitEncapsulation encapsulation) {
        UUID commandId = commandIdGenerator.generate();
        MessageContext messageContext = new MessageContext(commandId.toString(), context.getCorrelationId());
        return TransitFlowSegmentBlankRequest.buildResolver()
                .messageContext(messageContext)
                .commandId(commandId)
                .switchId(switchId)
                .flowId(flowPath.getFlow().getFlowId())
                .cookie(flowPath.getCookie())
                .ingressIslPort(ingressIslPort)
                .egressIslPort(egressIslPort)
                .encapsulation(encapsulation)
                .build().makeGenericResolver();
    }

    private FlowSegmentBlankGenericResolver makeEgressSegmentRequest(
            FlowPath flowPath, CommandContext context,
            FlowEndpoint egressEndpoint, FlowEndpoint ingressEndpoint, FlowTransitEncapsulation encapsulation) {

        List<PathSegment> segments = flowPath.getSegments();
        PathSegment egressSegment = segments.get(segments.size() - 1);
        int islPort = egressSegment.getDestPort();

        UUID commandId = commandIdGenerator.generate();
        MessageContext messageContext = new MessageContext(commandId.toString(), context.getCorrelationId());

        return EgressFlowSegmentBlankRequest.buildResolver()
                .messageContext(messageContext)
                .commandId(commandId)
                .flowId(flowPath.getFlow().getFlowId())
                .cookie(flowPath.getCookie())
                .endpoint(egressEndpoint)
                .ingressEndpoint(ingressEndpoint)
                .islPort(islPort)
                .encapsulation(encapsulation)
                .build().makeGenericResolver();
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

    private FlowTransitEncapsulation getEncapsulation(FlowPath path, FlowPath oppositePath) {
        EncapsulationResources resources = resourcesManager
                .getEncapsulationResources(path.getPathId(), oppositePath.getPathId(), encapsulationType)
                .orElseThrow(() -> new IllegalStateException(format(
                        "No encapsulation resources found for flow path %s (opposite: %s)",
                        path.getPathId(), oppositePath.getPathId())));
        return new FlowTransitEncapsulation(resources.getTransitEncapsulationId(), resources.getEncapsulationType());
    }

    private FlowEndpoint getIngressEndpoint(Flow flow, FlowPath path) {
        if (flow.getSrcSwitch().getSwitchId().equals(path.getSrcSwitch().getSwitchId())) {
            return getIngressEndpoint(flow);
        } else {
            return getEgressEndpoint(flow);
        }
    }

    private FlowEndpoint getIngressEndpoint(Flow flow) {
        return new FlowEndpoint(flow.getSrcSwitch().getSwitchId(), flow.getSrcPort(), flow.getSrcVlan());
    }

    private FlowEndpoint getEgressEndpoint(Flow flow, FlowPath path) {
        if (flow.getDestSwitch().getSwitchId().equals(path.getDestSwitch().getSwitchId())) {
            return getEgressEndpoint(flow);
        } else {
            return getIngressEndpoint(flow);
        }
    }

    private FlowEndpoint getEgressEndpoint(Flow flow) {
        return new FlowEndpoint(flow.getDestSwitch().getSwitchId(), flow.getDestPort(), flow.getDestVlan());
    }
}
