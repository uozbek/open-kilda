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

import org.openkilda.floodlight.api.FlowSegmentSchema;
import org.openkilda.floodlight.api.FlowSegmentSchemaEntry;
import org.openkilda.floodlight.api.request.FlowSegmentBlankGenericResolver;
import org.openkilda.messaging.info.meter.MeterEntry;
import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.messaging.info.rule.FlowInstructions;
import org.openkilda.messaging.info.switches.MeterInfoEntry;
import org.openkilda.messaging.info.switches.MeterMisconfiguredInfoEntry;
import org.openkilda.model.Cookie;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.Meter;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResourcesConfig;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.service.SpeakerFlowSegmentRequestBuilder;
import org.openkilda.wfm.topology.switchmanager.model.OfFlowReference;
import org.openkilda.wfm.topology.switchmanager.model.SpeakerSwitchSchema;
import org.openkilda.wfm.topology.switchmanager.model.SwitchDefaultFlowsSchema;
import org.openkilda.wfm.topology.switchmanager.model.SwitchOfTableDump;
import org.openkilda.wfm.topology.switchmanager.model.ValidateDefaultFlowsReport;
import org.openkilda.wfm.topology.switchmanager.model.ValidateDefaultFlowsReport.ValidateDefaultFlowsReportBuilder;
import org.openkilda.wfm.topology.switchmanager.model.ValidateFlowSegmentEntry;
import org.openkilda.wfm.topology.switchmanager.model.ValidateFlowSegmentReport;
import org.openkilda.wfm.topology.switchmanager.model.ValidateMetersResult;
import org.openkilda.wfm.topology.switchmanager.model.ValidateRulesResult;
import org.openkilda.wfm.topology.switchmanager.model.ValidateSwitchReport;
import org.openkilda.wfm.topology.switchmanager.model.ValidateSwitchReport.ValidateSwitchReportBuilder;
import org.openkilda.wfm.topology.switchmanager.service.ValidationService;

import com.google.common.collect.ImmutableList;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class ValidationServiceImpl implements ValidationService {
    private static final int METER_BURST_SIZE_EQUALS_DELTA = 1;
    private static final double E_SWITCH_METER_RATE_EQUALS_DELTA_COEFFICIENT = 0.01;
    private static final double E_SWITCH_METER_BURST_SIZE_EQUALS_DELTA_COEFFICIENT = 0.01;

    private final FlowResourcesManager resourceManager;
    private final FlowRepository flowRepository;
    private final FlowPathRepository flowPathRepository;

    public ValidationServiceImpl(FlowResourcesConfig resourcesConfig, PersistenceManager persistenceManager) {
        resourceManager =  new FlowResourcesManager(persistenceManager, resourcesConfig);

        RepositoryFactory repositories = persistenceManager.getRepositoryFactory();
        flowRepository = repositories.createFlowRepository();
        flowPathRepository = repositories.createFlowPathRepository();
    }

    @Override
    public List<FlowSegmentBlankGenericResolver> prepareFlowSegmentRequests(CommandContext context, SwitchId switchId) {
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

        List<FlowSegmentBlankGenericResolver> requests = new ArrayList<>();
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

            if (avoidIngressRequest.contains(pathId)) {
                requests.addAll(requestBuilder.buildAllExceptIngress(context, flow, path, oppositePath));
            } else {
                requests.addAll(requestBuilder.buildAll(context, flow, path, oppositePath));
            }
        }

        return requests;
    }

    @Override
    public ValidateSwitchReport validateSwitch(SpeakerSwitchSchema switchSchema) {
        log.debug("Validating rules on switch {}", switchSchema.getDatapath());

        Map<OfFlowReference, List<FlowEntry>> existingOfFlows = unpackOfFlows(switchSchema);

        ValidateSwitchReport.ValidateSwitchReportBuilder switchReport = ValidateSwitchReport.builder();

        // verify methods alter existingOfFlows list (remove matching entries)
        verifyCookieCollisions(switchReport, existingOfFlows);
        verifyFlowSegments(switchReport, switchSchema.getFlowSegments(), existingOfFlows);
        verifyDefaultFlows(switchReport, switchSchema.getDefaultFlowsSchema(), existingOfFlows);

        // TODO

        return null;
    }

    private ValidateRulesResult makeRulesResponse(Set<Long> expectedCookies, List<FlowEntry> presentRules,
                                                  List<FlowEntry> expectedDefaultRules, SwitchId switchId) {
        Set<Long> presentCookies = presentRules.stream()
                .map(FlowEntry::getCookie)
                .filter(cookie -> !Cookie.isDefaultRule(cookie))
                .collect(Collectors.toSet());

        Set<Long> missingRules = new HashSet<>(expectedCookies);
        missingRules.removeAll(presentCookies);
        if (! missingRules.isEmpty() && log.isErrorEnabled()) {
            log.error("On switch {} the following rules are missed: {}", switchId,
                    cookiesIntoLogRepresentation(missingRules));
        }

        Set<Long> properRules = new HashSet<>(expectedCookies);
        properRules.retainAll(presentCookies);

        Set<Long> excessRules = new HashSet<>(presentCookies);
        excessRules.removeAll(expectedCookies);
        if (!excessRules.isEmpty() && log.isWarnEnabled()) {
            log.warn("On switch {} the following rules are excessive: {}", switchId,
                    cookiesIntoLogRepresentation(excessRules));
        }

        Set<Long> misconfiguredRules = new HashSet<>();

        validateDefaultRules(presentRules, expectedDefaultRules, missingRules, properRules, excessRules,
                misconfiguredRules);

        return new ValidateRulesResult(
                ImmutableList.copyOf(missingRules),
                ImmutableList.copyOf(properRules),
                ImmutableList.copyOf(excessRules),
                ImmutableList.copyOf(misconfiguredRules));
    }

    private static String cookiesIntoLogRepresentation(Collection<Long> rules) {
        return rules.stream().map(Cookie::toString).collect(Collectors.joining(", ", "[", "]"));
    }

    @Override
    public ValidateMetersResult validateMeters(SwitchId switchId, List<MeterEntry> presentMeters,
                                               long flowMeterMinBurstSizeInKbits, double flowMeterBurstCoefficient) {
        log.debug("Validating meters on switch {}", switchId);

        presentMeters.removeIf(meterEntry -> MeterId.isMeterIdOfDefaultRule(meterEntry.getMeterId()));

        List<Long> presentMeterIds = presentMeters.stream()
                .map(MeterEntry::getMeterId)
                .collect(Collectors.toList());

        List<MeterInfoEntry> missingMeters = new ArrayList<>();
        List<MeterInfoEntry> misconfiguredMeters = new ArrayList<>();
        List<MeterInfoEntry> properMeters = new ArrayList<>();
        List<MeterInfoEntry> excessMeters = new ArrayList<>();

        Collection<FlowPath> paths = flowPathRepository.findBySrcSwitch(switchId).stream()
                .filter(path -> path.getMeterId() != null)
                .collect(Collectors.toList());

        for (FlowPath path : paths) {
            Switch sw = path.getSrcSwitch();
            boolean isESwitch =
                    Switch.isNoviflowESwitch(sw.getOfDescriptionManufacturer(), sw.getOfDescriptionHardware());

            long calculatedBurstSize = Meter.calculateBurstSize(path.getBandwidth(), flowMeterMinBurstSizeInKbits,
                    flowMeterBurstCoefficient, path.getSrcSwitch().getDescription());

            if (!presentMeterIds.contains(path.getMeterId().getValue())) {
                missingMeters.add(makeMissingMeterEntry(path, calculatedBurstSize));
            }

            for (MeterEntry meter : presentMeters) {
                if (meter.getMeterId() == path.getMeterId().getValue()) {
                    if (equalsRate(meter.getRate(), path.getBandwidth(), isESwitch)
                            && equalsBurstSize(meter.getBurstSize(), calculatedBurstSize, isESwitch)
                            && Arrays.equals(meter.getFlags(), Meter.getMeterFlags())) {

                        properMeters.add(makeProperMeterEntry(path, meter));
                    } else {
                        misconfiguredMeters.add(
                                makeMisconfiguredMeterEntry(path, meter, calculatedBurstSize, isESwitch));
                    }
                }
            }
        }

        List<Long> expectedMeterIds = paths.stream()
                .map(FlowPath::getMeterId)
                .map(MeterId::getValue)
                .collect(Collectors.toList());

        for (MeterEntry meterEntry : presentMeters) {
            if (!expectedMeterIds.contains(meterEntry.getMeterId())) {
                excessMeters.add(makeExcessMeterEntry(meterEntry));
            }
        }

        return new ValidateMetersResult(missingMeters, misconfiguredMeters, properMeters, excessMeters);
    }

    private MeterInfoEntry makeMissingMeterEntry(FlowPath path, Long burstSize) {
        return MeterInfoEntry.builder()
                .meterId(path.getMeterId().getValue())
                .cookie(path.getCookie().getValue())
                .flowId(path.getFlow().getFlowId())
                .rate(path.getBandwidth())
                .burstSize(burstSize)
                .flags(Meter.getMeterFlags())
                .build();
    }

    private MeterInfoEntry makeProperMeterEntry(FlowPath path, MeterEntry meter) {
        return MeterInfoEntry.builder()
                .meterId(meter.getMeterId())
                .cookie(path.getCookie().getValue())
                .flowId(path.getFlow().getFlowId())
                .rate(meter.getRate())
                .burstSize(meter.getBurstSize())
                .flags(meter.getFlags())
                .build();
    }

    private MeterInfoEntry makeExcessMeterEntry(MeterEntry meter) {
        return MeterInfoEntry.builder()
                .meterId(meter.getMeterId())
                .rate(meter.getRate())
                .burstSize(meter.getBurstSize())
                .flags(meter.getFlags())
                .build();
    }

    private MeterInfoEntry makeMisconfiguredMeterEntry(FlowPath path, MeterEntry meter, long burstSize,
                                                       boolean isESwitch) {
        MeterMisconfiguredInfoEntry actual = new MeterMisconfiguredInfoEntry();
        MeterMisconfiguredInfoEntry expected = new MeterMisconfiguredInfoEntry();

        if (!equalsRate(meter.getRate(), path.getBandwidth(), isESwitch)) {
            actual.setRate(meter.getRate());
            expected.setRate(path.getBandwidth());
        }
        if (!equalsBurstSize(meter.getBurstSize(), burstSize, isESwitch)) {
            actual.setBurstSize(meter.getBurstSize());
            expected.setBurstSize(burstSize);
        }
        if (!Arrays.equals(meter.getFlags(), Meter.getMeterFlags())) {
            actual.setFlags(meter.getFlags());
            expected.setFlags(Meter.getMeterFlags());
        }

        return MeterInfoEntry.builder()
                .meterId(meter.getMeterId())
                .cookie(path.getCookie().getValue())
                .flowId(path.getFlow().getFlowId())
                .rate(meter.getRate())
                .burstSize(meter.getBurstSize())
                .flags(meter.getFlags())
                .actual(actual)
                .expected(expected)
                .build();
    }

    private boolean equalsRate(long actual, long expected, boolean isESwitch) {
        // E-switches have a bug when installing the rate and burst size.
        // Such switch sets the rate different from the rate that was sent to it.
        // Therefore, we compare actual and expected values ​​using the delta coefficient.
        if (isESwitch) {
            return Math.abs(actual - expected) <= expected * E_SWITCH_METER_RATE_EQUALS_DELTA_COEFFICIENT;
        }
        return actual == expected;
    }

    private boolean equalsBurstSize(long actual, long expected, boolean isESwitch) {
        // E-switches have a bug when installing the rate and burst size.
        // Such switch sets the burst size different from the burst size that was sent to it.
        // Therefore, we compare actual and expected values ​​using the delta coefficient.
        if (isESwitch) {
            return Math.abs(actual - expected) <= expected * E_SWITCH_METER_BURST_SIZE_EQUALS_DELTA_COEFFICIENT;
        }
        return Math.abs(actual - expected) <= METER_BURST_SIZE_EQUALS_DELTA;
    }

    private Map<OfFlowReference, List<FlowEntry>> unpackOfFlows(SpeakerSwitchSchema switchSchema) {
        final Map<OfFlowReference, List<FlowEntry>> ofFlows = new HashMap<>();

        SwitchId datapath;
        int tableId;
        for (SwitchOfTableDump tableDump : switchSchema.getTables().values()) {
            datapath = tableDump.getDatapath();
            tableId = tableDump.getTableId();

            for (FlowEntry entry : tableDump.getEntries()) {
                OfFlowReference key = new OfFlowReference(tableId, entry.getCookie(), datapath);
                ofFlows.computeIfAbsent(key, ignore -> new ArrayList<>())
                        .add(entry);
            }
        }

        return ofFlows;
    }

    private void verifyCookieCollisions(
            ValidateSwitchReport.ValidateSwitchReportBuilder switchReport,
            Map<OfFlowReference, List<FlowEntry>> flows) {
        for (Map.Entry<OfFlowReference, List<FlowEntry>> entry : flows.entrySet()) {
            if (1 < entry.getValue().size()) {
                switchReport.cookieCollision(entry.getKey());
            }
        }
    }

    private void verifyFlowSegments(
            ValidateSwitchReport.ValidateSwitchReportBuilder switchReport, List<ValidateFlowSegmentEntry> flowSegments,
            Map<OfFlowReference, List<FlowEntry>> existingOfFlows) {

        for (ValidateFlowSegmentEntry segment : flowSegments) {
            ValidateFlowSegmentReport.ValidateFlowSegmentReportBuilder segmentReport = ValidateFlowSegmentReport
                    .builder()
                    .requestBlank(segment.getRequestBlank());

            FlowSegmentSchema schema = segment.getSchema();
            for (FlowSegmentSchemaEntry entry : schema.getEntries()) {
                OfFlowReference ref = new OfFlowReference(schema, entry);
                FlowSegmentEntryEqualDetector equalDetector = new FlowSegmentEntryEqualDetector(entry);
                if (removeFirstOfFlowMatch(existingOfFlows, ref, equalDetector)) {
                    segmentReport.properFlow(ref);
                } else {
                    segmentReport.missingFlow(ref);
                }
            }
            switchReport.segmentReport(segmentReport.build());
        }
    }

    private void verifyDefaultFlows(
            ValidateSwitchReportBuilder switchReport, SwitchDefaultFlowsSchema schema,
            Map<OfFlowReference, List<FlowEntry>> existingOfFlows) {
        ValidateDefaultFlowsReport.ValidateDefaultFlowsReportBuilder report = ValidateDefaultFlowsReport.builder();

        for (FlowEntry schemaEntry : schema.getEntries()) {
            OfFlowReference ref = new OfFlowReference(schema.getDatapath(), schemaEntry);
            DefaultFlowsSchemaEntryEqualDetector equalDetector = new DefaultFlowsSchemaEntryEqualDetector(schemaEntry);
            if (! isOfFlowExists(existingOfFlows, ref)) {
                report.missingFlow(ref);
            } else if (removeFirstOfFlowMatch(existingOfFlows, ref, equalDetector)) {
                report.properFlow(ref);
            } else {
                report.invalidFlow(ref);
            }
        }

        switchReport.defaultFlowsReport(report.build());
    }

    private boolean isOfFlowExists(Map<OfFlowReference, List<FlowEntry>> existingOfFlows, OfFlowReference ref) {
        List<FlowEntry> sequence = existingOfFlows.get(ref);
        return sequence != null && 0 < sequence.size();
    }

    private boolean removeFirstOfFlowMatch(
            Map<OfFlowReference, List<FlowEntry>> existingOfFlows, OfFlowReference ref,
            SchemaEqualDetector equalDetector) {
        List<FlowEntry> sequence = existingOfFlows.get(ref);
        if (sequence == null) {
            return false;
        }

        Iterator<FlowEntry> iter = sequence.iterator();
        boolean match = false;
        while (iter.hasNext()) {
            if (equalDetector.isEquals(iter.next())) {
                match = true;
                iter.remove();
                break;
            }
        }
        return match;
    }

    private abstract static class SchemaEqualDetector {
        abstract boolean isEquals(FlowEntry actualEntry);
    }

    @AllArgsConstructor
    private static class FlowSegmentEntryEqualDetector extends SchemaEqualDetector {
        private final FlowSegmentSchemaEntry schemaEntry;

        @Override
        boolean isEquals(FlowEntry actualEntry) {
            // cookie and tableId match is guaranteed by reference match, so it can be skipped
            final MeterId meterId = schemaEntry.getMeterId();
            if (meterId != null) {
                FlowInstructions instructions = actualEntry.getInstructions();
                MeterId actualMeter = new MeterId(instructions.getGoToMeter());
                if (meterId.equals(actualMeter)) {
                    log.info("Invalid meter {} != {} into flow entry {}", meterId, actualMeter, actualEntry);
                    return false;
                }
            }
            return true;
        }
    }

    @AllArgsConstructor
    private static class DefaultFlowsSchemaEntryEqualDetector extends SchemaEqualDetector {
        private final FlowEntry schemaEntry;

        @Override
        boolean isEquals(FlowEntry actualEntry) {
            return Objects.equals(schemaEntry, actualEntry);
        }
    }
}
