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
import org.openkilda.floodlight.command.SpeakerCommandProcessor;
import org.openkilda.floodlight.error.SwitchIncorrectMeterException;
import org.openkilda.floodlight.error.SwitchMissingMeterException;
import org.openkilda.floodlight.error.UnsupportedSwitchOperationException;
import org.openkilda.floodlight.utils.CompletableFutureAdapter;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchId;

import org.projectfloodlight.openflow.protocol.OFMeterConfig;
import org.projectfloodlight.openflow.protocol.OFMeterConfigStatsReply;
import org.projectfloodlight.openflow.protocol.OFMeterConfigStatsRequest;
import org.projectfloodlight.openflow.protocol.meterband.OFMeterBand;
import org.projectfloodlight.openflow.protocol.meterband.OFMeterBandDrop;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class MeterVerifyCommand extends MeterBlankCommand {
    public MeterVerifyCommand(
            SwitchId switchId, MessageContext messageContext, MeterConfig meterConfig) {
        super(switchId, messageContext, meterConfig);
    }

    @Override
    protected CompletableFuture<MeterReport> makeExecutePlan(SpeakerCommandProcessor commandProcessor)
            throws UnsupportedSwitchOperationException {
        ensureSwitchSupportMeters();
        return new CompletableFutureAdapter<>(
                 messageContext, getSw().writeStatsRequest(makeMeterReadCommand()))
                .thenAccept(this::handleMeterStats)
                .thenApply(ignore -> makeSuccessReport());
    }

    private OFMeterConfigStatsRequest makeMeterReadCommand() {
        return getSw().getOFFactory().buildMeterConfigStatsRequest()
                .setMeterId(meterConfig.getId().getValue())
                .build();
    }

    private void handleMeterStats(List<OFMeterConfigStatsReply> meterStatResponses) {
        Optional<OFMeterConfig> target = Optional.empty();
        for (OFMeterConfigStatsReply meterConfigReply : meterStatResponses) {
            target = findMeter(meterConfigReply);
            if (target.isPresent()) {
                break;
            }
        }

        if (! target.isPresent()) {
            throw maskCallbackException(new SwitchMissingMeterException(getSw().getId(), meterConfig.getId()));
        }
        validateMeterConfig(target.get());
    }

    private Optional<OFMeterConfig> findMeter(OFMeterConfigStatsReply meterConfigReply) {
        MeterId meterId = meterConfig.getId();
        for (OFMeterConfig entry : meterConfigReply.getEntries()) {
            if (meterId.getValue() == entry.getMeterId()) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    private void validateMeterConfig(OFMeterConfig meterConfig) {
        validateMeterConfigFlags(meterConfig);
        validateMeterConfigBands(meterConfig);
    }

    private void validateMeterConfigFlags(OFMeterConfig config) {
        if (! makeMeterFlags().equals(config.getFlags())) {
            throw maskCallbackException(new SwitchIncorrectMeterException(getSw().getId(), meterConfig, config));
        }
    }

    private void validateMeterConfigBands(OFMeterConfig config) {
        List<OFMeterBand> expectBands = makeMeterBands(0);  // to ignore burst value comparison
        List<OFMeterBand> actualBands = config.getEntries();

        if (expectBands.size() != actualBands.size()) {
            throw maskCallbackException(new SwitchIncorrectMeterException(getSw().getId(), meterConfig, config));
        }

        boolean mismatch = false;
        for (int i = 0; i < expectBands.size(); i++) {
            OFMeterBand expect = expectBands.get(i);
            OFMeterBand actual = actualBands.get(i);

            if (actual instanceof OFMeterBandDrop) {
                actual = ((OFMeterBandDrop) actual).createBuilder()
                        .setBurstSize(0)  // to ignore burst value comparison
                        .build();
            }

            mismatch = !expect.equals(actual);
            if (mismatch) {
                break;
            }
        }

        if (mismatch) {
            throw maskCallbackException(new SwitchIncorrectMeterException(getSw().getId(), meterConfig, config));
        }
    }
}
