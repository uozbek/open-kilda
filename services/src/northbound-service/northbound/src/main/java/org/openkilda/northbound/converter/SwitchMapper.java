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

package org.openkilda.northbound.converter;

import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.info.switches.MeterInfoEntry;
import org.openkilda.messaging.info.switches.MeterMisconfiguredInfoEntry;
import org.openkilda.messaging.info.switches.MetersSyncEntry;
import org.openkilda.messaging.info.switches.MetersValidationEntry;
import org.openkilda.messaging.info.switches.RulesSyncEntry;
import org.openkilda.messaging.info.switches.SwitchSyncResponse;
import org.openkilda.messaging.info.switches.SwitchValidationResponse;
import org.openkilda.model.SwitchId;
import org.openkilda.model.validate.OfFlowMissing;
import org.openkilda.model.validate.OfFlowReference;
import org.openkilda.model.validate.OfMeterReference;
import org.openkilda.model.validate.ValidateDefaultOfFlowsReport;
import org.openkilda.model.validate.ValidateDefect;
import org.openkilda.model.validate.ValidateFlowSegmentReport;
import org.openkilda.model.validate.ValidateOfFlowDefect;
import org.openkilda.model.validate.ValidateSwitchReport;
import org.openkilda.northbound.dto.v1.switches.MeterInfoDto;
import org.openkilda.northbound.dto.v1.switches.MeterMisconfiguredInfoDto;
import org.openkilda.northbound.dto.v1.switches.MetersSyncDto;
import org.openkilda.northbound.dto.v1.switches.MetersValidationDto;
import org.openkilda.northbound.dto.v1.switches.RulesSyncDto;
import org.openkilda.northbound.dto.v1.switches.RulesSyncResult;
import org.openkilda.northbound.dto.v1.switches.RulesValidationDto;
import org.openkilda.northbound.dto.v1.switches.RulesValidationResult;
import org.openkilda.northbound.dto.v1.switches.SwitchDto;
import org.openkilda.northbound.dto.v1.switches.SwitchSyncResult;
import org.openkilda.northbound.dto.v1.switches.SwitchValidationResult;

import com.sun.javaws.exceptions.InvalidArgumentException;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Mapper(componentModel = "spring")
public abstract class SwitchMapper {

    /**
     * Convert {@link SwitchInfoData} to {@link SwitchDto}.
     */
    public SwitchDto toSwitchDto(SwitchInfoData data) {
        if (data == null) {
            return null;
        }
        SwitchDto dto = SwitchDto.builder()
                .switchId(data.getSwitchId().toString())
                .address(data.getAddress())
                .hostname(data.getHostname())
                .description(data.getDescription())
                .state(data.getState().toString())
                .underMaintenance(data.isUnderMaintenance())
                .build();

        if (data.getSwitchView() != null) {
            dto.setOfVersion(data.getSwitchView().getOfVersion());
            if (data.getSwitchView().getDescription() != null) {
                dto.setManufacturer(data.getSwitchView().getDescription().getManufacturer());
                dto.setHardware(data.getSwitchView().getDescription().getHardware());
                dto.setSoftware(data.getSwitchView().getDescription().getSoftware());
                dto.setSerialNumber(data.getSwitchView().getDescription().getSerialNumber());
            }
        }

        return dto;
    }

    @Mapping(source = "rules.excess", target = "excessRules")
    @Mapping(source = "rules.missing", target = "missingRules")
    @Mapping(source = "rules.proper", target = "properRules")
    @Mapping(source = "rules.installed", target = "installedRules")
    public abstract RulesSyncResult toRulesSyncResult(SwitchSyncResponse response);

    public abstract SwitchSyncResult toSwitchSyncResult(SwitchSyncResponse response);

    public abstract RulesSyncDto toRulesSyncDto(RulesSyncEntry data);

    public abstract MetersSyncDto toMetersSyncDto(MetersSyncEntry data);

    public abstract SwitchValidationResult toSwitchValidationResult(SwitchValidationResponse response);

    public RulesValidationResult toRulesValidationResult(SwitchValidationResponse response) {
        RulesValidationDto rules = toRulesValidationDto(response.getReport());
        return new RulesValidationResult(rules.getMissing(), rules.getProper(), rules.getExcess());
    }

    public RulesValidationDto toRulesValidationDto(ValidateSwitchReport report) {
        ArrayList<Long> excess = new ArrayList<>(lookupTableZeroCookies(report.getExcessOfFlows()));
        RulesValidationDto result = new RulesValidationDto(
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), excess);

        for (ValidateFlowSegmentReport segmentReport : report.getSegmentReports()) {
            result.getProper().addAll(lookupTableZeroCookies(segmentReport.getProperOfFlows()));
            for (ValidateDefect defect : segmentReport.getDefects()) {
                collectOfFlowDefects(result, defect);
            }
        }

        ValidateDefaultOfFlowsReport defaultOfFlowReport = report.getDefaultFlowsReport();
        result.getProper().addAll(lookupTableZeroCookies(defaultOfFlowReport.getProperOfFlows()));
        for (ValidateDefect defect : defaultOfFlowReport.getDefects()) {
            collectOfFlowDefects(result, defect);
        }

        return result;
    }

    public MetersValidationDto toMetersValidationDto(ValidateSwitchReport report) {
        List<OfMeterReference> excess = report.getExcessMeters();
        MetersValidationDto result = new MetersValidationDto(
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(excess));
    }

    public abstract MeterInfoDto toMeterInfoDto(MeterInfoEntry data);

    public abstract MeterMisconfiguredInfoDto toMeterMisconfiguredInfoDto(MeterMisconfiguredInfoEntry data);

    public String toSwitchId(SwitchId switchId) {
        return switchId.toString();
    }

    private void collectOfFlowDefects(RulesValidationDto result, ValidateDefect defect) {
        if (! defect.getFlow().isPresent()) {
            return;
        }
        ValidateOfFlowDefect flowDefect = defect.getFlow().get();
        if (flowDefect.getReference().getTableId() != 0) {
            return;
        }

        long cookie = flowDefect.getReference().getCookie().getValue();
        if (flowDefect.isMissing()) {
            result.getMissing().add(cookie);
        } else if (flowDefect.isExcess()) {
            result.getExcess().add(cookie);
        } else if (flowDefect.isMismatch()) {
            result.getMissing().add(cookie);
        } else {
            throw new IllegalArgumentException(String.format("Unsupported defect kind: %s", flowDefect));
        }
    }

    private List<Long> lookupTableZeroCookies(List<OfFlowReference> references) {
        return lookupTableZeroCookies(references.stream());
    }

    private List<Long> lookupTableZeroCookies(Stream<OfFlowReference> references) {
        return references.filter(entry -> entry.getTableId() == 0)
                .map(entry -> entry.getCookie().getValue())
                .collect(Collectors.toList());
    }
}
