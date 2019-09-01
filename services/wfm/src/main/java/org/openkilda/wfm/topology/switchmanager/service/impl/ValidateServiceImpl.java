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
import org.openkilda.model.Cookie;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.model.SwitchId;
import org.openkilda.model.of.FlowSegmentSchema;
import org.openkilda.model.of.OfFlowSchema;
import org.openkilda.model.validate.FlowSegmentReference;
import org.openkilda.model.validate.MeterCollision;
import org.openkilda.model.validate.OfFlowMissing;
import org.openkilda.model.validate.OfFlowReference;
import org.openkilda.model.validate.OfMeterReference;
import org.openkilda.model.validate.ValidateDefaultOfFlowsReport;
import org.openkilda.model.validate.ValidateDefaultOfFlowsReport.ValidateDefaultOfFlowsReportBuilder;
import org.openkilda.model.validate.ValidateFlowSegmentReport;
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
import org.openkilda.wfm.topology.switchmanager.model.ValidateFlowSegmentDescriptor;
import org.openkilda.wfm.topology.switchmanager.service.ValidateService;

import com.google.common.collect.ImmutableList;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
        FlowSegmentReference ref = new FlowSegmentReference(flow.getFlowId(), path.getPathId(), blank.getSwitchId());
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

            FlowSegmentReportAdapter reportAdapter = new FlowSegmentReportAdapter(segmentReport);
            FlowSegmentSchema segmentSchema = segmentDescriptor.getSchema();
            for (OfFlowSchema ofFlowSchema : segmentSchema.getEntries()) {
                OfFlowReference ref = new OfFlowReference(segmentSchema, ofFlowSchema);
                verifyOfFlow(context, reportAdapter, ref, ofFlowSchema);
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
            ValidateDefaultOfFlowsReportBuilder reportCurrent = reportBuilders.computeIfAbsent(
                    ref.getDatapath(), ignore -> ValidateDefaultOfFlowsReport.builder());

            DefaultOfFlowsReportAdapter reportAdapter = new DefaultOfFlowsReportAdapter(reportCurrent);
            verifyOfFlow(context, reportAdapter, ref, entry.getValue());
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

    private Collection<OfMeterReference> verifyExcessMeters(ValidateContext context) {
        return context.getActualOfMeters().keySet();
    }

    private void verifyOfFlow(
            ValidateContext context, OfFlowsReportAdapter report, OfFlowReference ref, OfFlowSchema expected) {
        List<OfFlowSchema> matchCandidates = context.getActualOfFlows().getOrDefault(ref, Collections.emptyList());
        Iterator<OfFlowSchema> iter = matchCandidates.iterator();
        boolean isProper = false;
        while (iter.hasNext()) {
            if (! expected.equals(iter.next())) {
                continue;
            }

            // full match
            isProper = true;
            iter.remove();

            report.addProperEntry(ref);
            if (expected.getMeterId() != null) {
                context.removeUsedMeter(ref, expected.getMeterId());
            }
            break;
        }

        if (! isProper) {
            // partial matches inserted as mutable list, they will lost element on future lookups on full match with
            // another OF flow
            OfFlowMissing missing = OfFlowMissing.builder()
                    .reference(ref)
                    .partialMatches(matchCandidates)
                    .build();
            report.addMissingEntry(missing);
        }
    }

    // FIXME
    private static String cookiesIntoLogRepresentation(Collection<Long> rules) {
        return rules.stream().map(Cookie::toString).collect(Collectors.joining(", ", "[", "]"));
    }

    private static abstract class OfFlowsReportAdapter {
        abstract void addProperEntry(OfFlowReference ref);
        abstract void addMissingEntry(OfFlowMissing missing);
    }

    @AllArgsConstructor
    private static class FlowSegmentReportAdapter extends OfFlowsReportAdapter {
        private final ValidateFlowSegmentReport.ValidateFlowSegmentReportBuilder segmentReport;

        @Override
        void addProperEntry(OfFlowReference ref) {
            segmentReport.properOfFlow(ref);
        }

        @Override
        void addMissingEntry(OfFlowMissing missing) {
            segmentReport.missingOfFlow(missing);
        }
    }

    @AllArgsConstructor
    private static class DefaultOfFlowsReportAdapter extends OfFlowsReportAdapter {
        private final ValidateDefaultOfFlowsReport.ValidateDefaultOfFlowsReportBuilder defaultFlowsReport;

        @Override
        void addProperEntry(OfFlowReference ref) {
            defaultFlowsReport.properOfFlow(ref);
        }

        @Override
        void addMissingEntry(OfFlowMissing missing) {
            defaultFlowsReport.missingOfFlow(missing);
        }
    }
}
