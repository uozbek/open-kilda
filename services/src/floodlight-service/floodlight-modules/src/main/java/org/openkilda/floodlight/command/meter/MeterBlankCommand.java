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

package org.openkilda.floodlight.command.meter;

import org.openkilda.floodlight.api.MeterConfig;
import org.openkilda.floodlight.command.SpeakerCommand;
import org.openkilda.floodlight.config.provider.FloodlightModuleConfigurationProvider;
import org.openkilda.floodlight.error.SwitchNotFoundException;
import org.openkilda.floodlight.error.UnsupportedSwitchOperationException;
import org.openkilda.floodlight.service.FeatureDetectorService;
import org.openkilda.floodlight.switchmanager.SwitchManager;
import org.openkilda.floodlight.switchmanager.SwitchManagerConfig;
import org.openkilda.messaging.MessageContext;
import org.openkilda.messaging.model.SpeakerSwitchView;
import org.openkilda.messaging.model.SpeakerSwitchView.Feature;
import org.openkilda.model.Meter;
import org.openkilda.model.SwitchId;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import lombok.AccessLevel;
import lombok.Getter;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.projectfloodlight.openflow.protocol.OFMeterFlags;
import org.projectfloodlight.openflow.protocol.meterband.OFMeterBand;

import java.util.List;
import java.util.Set;

abstract class MeterBlankCommand extends SpeakerCommand<MeterReport> {
    // payload
    protected final MeterConfig meterConfig;

    // operation data
    private SwitchManagerConfig switchManagerConfig;
    @Getter(AccessLevel.PROTECTED)
    private Set<SpeakerSwitchView.Feature> switchFeatures;

    MeterBlankCommand(SwitchId switchId, MessageContext messageContext, MeterConfig meterConfig) {
        super(messageContext, switchId);
        this.meterConfig = meterConfig;
    }

    @Override
    protected MeterReport makeReport(Exception error) {
        return new MeterReport(error);
    }

    protected MeterReport makeSuccessReport() {
        return new MeterReport(meterConfig.getId());
    }

    Set<OFMeterFlags> makeMeterFlags() {
        return ImmutableSet.of(OFMeterFlags.KBPS, OFMeterFlags.BURST, OFMeterFlags.STATS);
    }

    List<OFMeterBand> makeMeterBands() {
        long burstSize = Meter.calculateBurstSize(
                meterConfig.getBandwidth(), switchManagerConfig.getFlowMeterMinBurstSizeInKbits(),
                switchManagerConfig.getFlowMeterBurstCoefficient(),
                getSw().getSwitchDescription().getManufacturerDescription(),
                getSw().getSwitchDescription().getSoftwareDescription());
        return makeMeterBands(burstSize);
    }

    List<OFMeterBand> makeMeterBands(long burstSize) {
        return ImmutableList.of(getSw().getOFFactory().meterBands()
                .buildDrop()
                .setRate(meterConfig.getBandwidth())
                .setBurstSize(burstSize)
                .build());
    }

    @Override
    protected void setup(FloodlightModuleContext moduleContext) throws SwitchNotFoundException {
        super.setup(moduleContext);

        FloodlightModuleConfigurationProvider provider =
                FloodlightModuleConfigurationProvider.of(moduleContext, SwitchManager.class);
        switchManagerConfig = provider.getConfiguration(SwitchManagerConfig.class);

        FeatureDetectorService featuresDetector = moduleContext.getServiceImpl(FeatureDetectorService.class);
        switchFeatures = featuresDetector.detectSwitch(getSw());
    }

    void ensureSwitchSupportMeters() throws UnsupportedSwitchOperationException {
        if (!switchFeatures.contains(Feature.METERS)) {
            throw new UnsupportedSwitchOperationException(getSw().getId(), "Switch doesn't support meters");
        }
    }
}
