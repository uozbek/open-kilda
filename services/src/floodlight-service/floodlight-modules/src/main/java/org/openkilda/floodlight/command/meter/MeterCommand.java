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

import org.openkilda.floodlight.KafkaChannel;
import org.openkilda.floodlight.command.SpeakerCommandV2;
import org.openkilda.floodlight.error.SwitchNotFoundException;
import org.openkilda.floodlight.error.UnsupportedSwitchOperationException;
import org.openkilda.floodlight.service.FeatureDetectorService;
import org.openkilda.floodlight.service.kafka.IKafkaProducerService;
import org.openkilda.messaging.MessageContext;
import org.openkilda.messaging.model.SpeakerSwitchView.Feature;
import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchId;

import net.floodlightcontroller.core.module.FloodlightModuleContext;

import java.util.Set;

abstract class MeterCommand extends SpeakerCommandV2 {
    private FeatureDetectorService featureDetectorService;

    MeterId meterId;

    MeterCommand(SwitchId switchId, MessageContext messageContext, MeterId meterId) {
        super(switchId, messageContext);
        this.meterId = meterId;
    }

    @Override
    protected void setup(FloodlightModuleContext moduleContext) throws SwitchNotFoundException {
        super.setup(moduleContext);
        featureDetectorService = moduleContext.getServiceImpl(FeatureDetectorService.class);
    }

    public void handleResult(KafkaChannel kafkaChannel, IKafkaProducerService kafkaProducerService, String requestKey,
                             Throwable error) {
        log.debug("Complete command {} for meter {} (do not produce response)", getClass().getCanonicalName(), meterId);
    }

    void ensureSwitchSupportMeters() throws UnsupportedSwitchOperationException {
        Set<Feature> supportedFeatures = featureDetectorService.detectSwitch(getSw());
        if (!supportedFeatures.contains(Feature.METERS)) {
            throw new UnsupportedSwitchOperationException(getSw().getId(), "Switch doesn't support meters");
        }
    }
}
