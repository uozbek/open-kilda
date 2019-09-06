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

package org.openkilda.wfm.topology.switchmanager.service.impl;

import org.openkilda.floodlight.api.request.FlowSegmentBlankGenericResolver;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.model.SwitchId;
import org.openkilda.model.of.FlowSegmentSchema;
import org.openkilda.model.of.MeterSchema;
import org.openkilda.model.of.OfFlowSchema;
import org.openkilda.model.validate.FlowSegmentReference;
import org.openkilda.model.validate.MeterCollision;
import org.openkilda.model.validate.OfFlowReference;
import org.openkilda.model.validate.OfMeterReference;
import org.openkilda.model.validate.ValidateDefaultOfFlowsReport;
import org.openkilda.model.validate.ValidateDefaultOfFlowsReport.ValidateDefaultOfFlowsReportBuilder;
import org.openkilda.model.validate.ValidateDefect;
import org.openkilda.model.validate.ValidateFlowSegmentReport;
import org.openkilda.model.validate.ValidateOfFlowDefect;
import org.openkilda.model.validate.ValidateOfMeterDefect;
import org.openkilda.model.validate.ValidateSwitchReport;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResourcesConfig;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.service.SpeakerFlowSegmentRequestBuilder;
import org.openkilda.wfm.topology.switchmanager.model.SpeakerSwitchSchema;
import org.openkilda.wfm.topology.switchmanager.model.SwitchSyncData;
import org.openkilda.wfm.topology.switchmanager.model.ValidateContext;
import org.openkilda.wfm.topology.switchmanager.model.ValidateFlowSegmentDescriptor;
import org.openkilda.wfm.topology.switchmanager.service.ValidateService;

import com.google.common.collect.ImmutableList;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class ValidateServiceImpl implements ValidateService {
    private final FlowResourcesManager resourceManager;
    private final FlowRepository flowRepository;
    private final FlowPathRepository flowPathRepository;

    public ValidateServiceImpl(FlowResourcesConfig resourcesConfig, PersistenceManager persistenceManager) {
        resourceManager =  new FlowResourcesManager(persistenceManager, resourcesConfig);

        RepositoryFactory repositories = persistenceManager.getRepositoryFactory();
        flowRepository = repositories.createFlowRepository();
        flowPathRepository = repositories.createFlowPathRepository();
    }

    @Override
    public List<ValidateFlowSegmentDescriptor> makeSwitchValidateFlowSegments(
            CommandContext context, SwitchId switchId) {
        final Map<PathId, FlowPath> affectedPath = new HashMap<>();

        Set<PathId> avoidIngressRequest = new HashSet<>();
        for (Flow flow : flowRepository.findByEndpointSwitch(switchId)) {
            affectedPath.put(flow.getForwardPathId(), flow.getForwardPath());
            affectedPath.put(flow.getReversePathId(), flow.getReversePath());

            FlowPath path = flow.getProtectedForwardPath();
            if (path != null) {
                avoidIngressRequest.add(path.getPathId());
                affectedPath.put(path.getPathId(), path);
            }

            path = flow.getProtectedReversePath();
            if (path != null) {
                avoidIngressRequest.add(path.getPathId());
                affectedPath.put(path.getPathId(), path);
            }
        }

        for (FlowPath path : flowPathRepository.findBySegmentSwitch(switchId)) {
            affectedPath.put(path.getPathId(), path);
        }

        SpeakerFlowSegmentRequestBuilder requestBuilder = new SpeakerFlowSegmentRequestBuilder(
                resourceManager, switchId);

        List<ValidateFlowSegmentDescriptor> descriptors = new ArrayList<>();
        Set<PathId> processedPath = new HashSet<>();
        for (Map.Entry<PathId, FlowPath> pathEntry : affectedPath.entrySet()) {
            PathId pathId = pathEntry.getKey();
            if (processedPath.contains(pathId)) {
                continue;
            }

            FlowPath path = pathEntry.getValue();
            Flow flow = path.getFlow();
            PathId oppositePathId = flow.getOppositePathId(pathId);
            FlowPath oppositePath = affectedPath.get(oppositePathId);
            if (oppositePath == null) {
                // asymmetric path
                oppositePath = flowPathRepository.findById(oppositePathId)
                        .orElseThrow(() -> new IllegalStateException(String.format(
                                "unable to find opposite path to %s (flow: \"%s\") into persistence storage",
                                pathId, flow.getFlowId())));
            }

            processedPath.add(pathId);
            processedPath.add(oppositePath.getPathId());

            List<FlowSegmentBlankGenericResolver> blanks;
            if (avoidIngressRequest.contains(pathId)) {
                blanks = requestBuilder.buildAllExceptIngress(context, flow, path, oppositePath);
            } else {
                blanks = requestBuilder.buildAll(context, flow, path, oppositePath);
            }
            descriptors.addAll(makeFlowSegmentDescriptor(flow, path, blanks));
        }

        return descriptors;
    }

    @Override
    public SwitchSyncData validateSwitch(SpeakerSwitchSchema switchSchema) {
        log.debug("Validating rules on switch {}", switchSchema.getDatapath());

        ValidateSwitchReport.ValidateSwitchReportBuilder switchReport = ValidateSwitchReport.builder()
                .datapath(switchSchema.getDatapath());
        ValidateContext context = new ValidateContext(switchSchema);

        switchReport.cookieCollisions(verifyCookieCollisions(context));
        switchReport.meterCollisions(verifyMeterCollisions(context));

        switchReport.segmentReports(verifyFlowSegments(context));
        switchReport.defaultFlowsReport(verifyDefaultFlows(context).get(switchSchema.getDatapath()));

        switchReport.excessOfFlows(verifyExcessOfFlows(context));
        switchReport.excessMeters(verifyExcessMeters(context));

        return new SwitchSyncData(switchReport.build(), ImmutableList.copyOf(context.getCorruptedSegments()));
    }

    private List<ValidateFlowSegmentDescriptor> makeFlowSegmentDescriptor(
            Flow flow, FlowPath path, List<FlowSegmentBlankGenericResolver> blanks) {
        return blanks.stream()
                .map(entry -> makeFlowSegmentDescriptor(flow, path, entry))
                .collect(Collectors.toList());
    }

    private ValidateFlowSegmentDescriptor makeFlowSegmentDescriptor(
            Flow flow, FlowPath path, FlowSegmentBlankGenericResolver blank) {
        FlowSegmentReference ref = new FlowSegmentReference(
                flow.getFlowId(), path.getPathId(), blank.getSwitchId(), path.getCookie());
        return ValidateFlowSegmentDescriptor.builder()
                .ref(ref)
                .requestBlank(blank)
                .build();
    }

    private List<OfFlowReference> verifyCookieCollisions(ValidateContext context) {
        List<OfFlowReference> collisions = new ArrayList<>();
        for (Map.Entry<OfFlowReference, List<OfFlowSchema>> entry : context.getActualOfFlows().entrySet()) {
            if (1 < entry.getValue().size()) {
                collisions.add(entry.getKey());
            }
        }
        return collisions;
    }

    private List<MeterCollision> verifyMeterCollisions(ValidateContext context) {
        Map<OfMeterReference, List<OfFlowReference>> meterUsageMap = new HashMap<>();
        for (Map.Entry<OfFlowReference, List<OfFlowSchema>> actualFlowEntry : context.getActualOfFlows().entrySet()) {
            for (OfFlowSchema entry : actualFlowEntry.getValue()) {
                MeterId meterId = entry.getMeterId();
                if (meterId == null) {
                    continue;
                }
                OfMeterReference meterRef = new OfMeterReference(meterId, actualFlowEntry.getKey().getDatapath());
                OfFlowReference flowRef = actualFlowEntry.getKey();
                meterUsageMap.computeIfAbsent(meterRef, ignore -> new ArrayList<>())
                        .add(flowRef);
            }
        }

        List<MeterCollision> collisions = new ArrayList<>();
        for (Map.Entry<OfMeterReference, List<OfFlowReference>> entry : meterUsageMap.entrySet()) {
            List<OfFlowReference> usages = entry.getValue();
            if (1 < usages.size()) {
                collisions.add(
                        MeterCollision.builder()
                                .ref(entry.getKey())
                                .usages(usages)
                                .build());
            }
        }
        return collisions;
    }

    private List<ValidateFlowSegmentReport> verifyFlowSegments(ValidateContext context) {
        List<ValidateFlowSegmentReport> reports = new ArrayList<>();
        for (ValidateFlowSegmentDescriptor segmentDescriptor : context.getExpectedFlowSegments()) {
            ValidateFlowSegmentReport.ValidateFlowSegmentReportBuilder segmentReport = ValidateFlowSegmentReport
                    .builder()
                    .segmentRef(segmentDescriptor.getRef());

            FlowSegmentSchema segmentSchema = segmentDescriptor.getSchema();
            for (OfFlowSchema ofFlowSchema : segmentSchema.getEntries()) {
                OfFlowReference ref = new OfFlowReference(segmentSchema, ofFlowSchema);
                // TODO(surabujin): resolve copy & paste
                Optional<ValidateDefect> defect = verifyOfFlow(context, ref, ofFlowSchema);
                if (defect.isPresent()) {
                    segmentReport.defect(defect.get());
                } else {
                    OfMeterReference meterRef = new OfMeterReference(ofFlowSchema.getMeterId(), ref.getDatapath());
                    segmentReport.properMeter(
                            context.lookupExpectedMeterSchema(meterRef)
                                    .orElseThrow(() -> makeMissingExpectedMeterException(meterRef)));
                    segmentReport.properOfFlow(ref);
                }
            }

            ValidateFlowSegmentReport report = segmentReport.build();
            if (! report.isValid()) {
                context.recordCorruptedSegment(segmentDescriptor);
            }
            reports.add(report);
        }

        return reports;
    }

    private Map<SwitchId, ValidateDefaultOfFlowsReport> verifyDefaultFlows(ValidateContext context) {
        Map<SwitchId, ValidateDefaultOfFlowsReportBuilder> reportBuilders = new HashMap<>();

        for (Map.Entry<OfFlowReference, OfFlowSchema> entry : context.getExpectedDefaultOfFlows().entrySet()) {
            OfFlowReference ref = entry.getKey();
            ValidateDefaultOfFlowsReportBuilder report = reportBuilders.computeIfAbsent(
                    ref.getDatapath(), ignore -> ValidateDefaultOfFlowsReport.builder());

            OfFlowSchema flowSchema = entry.getValue();
            // TODO(surabujin): resolve copy & paste
            Optional<ValidateDefect> defect = verifyOfFlow(context, ref, flowSchema);
            if (defect.isPresent()) {
                report.defect(defect.get());
            } else {
                OfMeterReference meterRef = new OfMeterReference(flowSchema.getMeterId(), ref.getDatapath());
                report.properMeter(
                        context.lookupExpectedMeterSchema(meterRef)
                                .orElseThrow(() -> makeMissingExpectedMeterException(meterRef)));
                report.properOfFlow(ref);
            }
        }

        Map<SwitchId, ValidateDefaultOfFlowsReport> reportsAll = new HashMap<>();
        for (Map.Entry<SwitchId, ValidateDefaultOfFlowsReportBuilder> entry : reportBuilders.entrySet()) {
            reportsAll.put(entry.getKey(), entry.getValue().build());
        }
        return reportsAll;
    }

    private List<OfFlowReference> verifyExcessOfFlows(ValidateContext context) {
        List<OfFlowReference> excess = new ArrayList<>();

        for (Map.Entry<OfFlowReference, List<OfFlowSchema>> sharedRef : context.getActualOfFlows().entrySet()) {
            for (int i = 0; i < sharedRef.getValue().size(); i++) {
                excess.add(sharedRef.getKey());
            }
        }

        return excess;
    }

    private List<MeterSchema> verifyExcessMeters(ValidateContext context) {
        List<MeterSchema> excess = new ArrayList<>();
        Set<OfMeterReference> seenMeters = context.getSeenMeters();
        for (Map.Entry<OfMeterReference, MeterSchema> entry : context.getActualOfMeters().entrySet()) {
            if (! seenMeters.contains(entry.getKey())) {
                excess.add(entry.getValue());
            }
        }
        return excess;
    }

    private Optional<ValidateDefect> verifyOfFlow(ValidateContext context, OfFlowReference ref, OfFlowSchema expected) {
        ValidateOfFlowDefect flowDefect = null;
        Optional<OfFlowSchema> actual = context.lookupAndExtractActualOfFlowSchema(ref, expected);
        if (!actual.isPresent()) {
            flowDefect = new ValidateOfFlowDefect(ref, expected, null);
        }

        ValidateOfMeterDefect meterDefect = null;
        if (expected.getMeterId() != null) {
            OfMeterReference meterRef = new OfMeterReference(expected.getMeterId(), ref.getDatapath());
            meterDefect = verifyOfMeter(context, meterRef);
        }

        if (flowDefect != null || meterDefect != null) {
            return Optional.of(ValidateDefect.builder()
                                       .flow(flowDefect)
                                       .meter(meterDefect).build());
        }
        return Optional.empty();
    }

    private ValidateOfMeterDefect verifyOfMeter(ValidateContext context, OfMeterReference ref) {
        MeterSchema expected = context.lookupExpectedMeterSchema(ref)
                .orElseThrow(() -> makeMissingExpectedMeterException(ref));
        Optional<MeterSchema> actual = context.lookupActualMeterSchema(ref);

        context.recordSeenMeter(ref);

        ValidateOfMeterDefect defect = null;
        if (! actual.isPresent()) {
            defect = new ValidateOfMeterDefect(ref, expected, null);
        } else if (! expected.equals(actual.get())) {
            defect = new ValidateOfMeterDefect(ref, expected, actual.get());
        }
        return defect;
    }

    private RuntimeException makeMissingExpectedMeterException(OfMeterReference ref) {
        return new IllegalStateException(String.format(
                "Inconsistent validate expected data - missing meter schema for %s", ref));
    }
}
