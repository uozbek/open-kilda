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

import static org.projectfloodlight.openflow.protocol.OFVersion.OF_13;

import org.openkilda.floodlight.api.MeterConfig;
import org.openkilda.floodlight.command.IOfErrorResponseHandler;
import org.openkilda.floodlight.config.provider.FloodlightModuleConfigurationProvider;
import org.openkilda.floodlight.error.InvalidMeterIdException;
import org.openkilda.floodlight.error.SwitchErrorResponseException;
import org.openkilda.floodlight.error.SwitchMeterConflictException;
import org.openkilda.floodlight.error.SwitchNotFoundException;
import org.openkilda.floodlight.error.UnsupportedSwitchOperationException;
import org.openkilda.floodlight.service.session.Session;
import org.openkilda.floodlight.switchmanager.SwitchManager;
import org.openkilda.floodlight.switchmanager.SwitchManagerConfig;
import org.openkilda.floodlight.utils.CompletableFutureAdapter;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.Meter;
import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchId;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.projectfloodlight.openflow.protocol.OFErrorMsg;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFMeterConfig;
import org.projectfloodlight.openflow.protocol.OFMeterConfigStatsReply;
import org.projectfloodlight.openflow.protocol.OFMeterConfigStatsRequest;
import org.projectfloodlight.openflow.protocol.OFMeterFlags;
import org.projectfloodlight.openflow.protocol.OFMeterMod;
import org.projectfloodlight.openflow.protocol.OFMeterModCommand;
import org.projectfloodlight.openflow.protocol.OFMeterModFailedCode;
import org.projectfloodlight.openflow.protocol.errormsg.OFMeterModFailedErrorMsg;
import org.projectfloodlight.openflow.protocol.meterband.OFMeterBand;
import org.projectfloodlight.openflow.protocol.meterband.OFMeterBandDrop;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class MeterInstallCommand extends MeterBlankCommand implements IOfErrorResponseHandler {
    private SwitchManagerConfig switchManagerConfig;

    public MeterInstallCommand(MessageContext messageContext, SwitchId switchId, MeterConfig meterConfig) {
        super(switchId, messageContext, meterConfig);
    }

    @Override
    protected CompletableFuture<MeterReport> makeExecutePlan()
            throws UnsupportedSwitchOperationException, InvalidMeterIdException {
        final OFMeterMod meterAddMessage = makeMeterAddMessage();
        try (Session session = getSessionService().open(messageContext, getSw())) {
            return setupErrorHandler(session.write(meterAddMessage), this)
                    .thenApply(ignore -> new MeterReport(meterConfig.getMeterId()));
        }
    }

    @Override
    public CompletableFuture<Optional<OFMessage>> handleOfError(OFErrorMsg response) {
        CompletableFuture<Optional<OFMessage>> future = new CompletableFuture<>();
        if (!isAddConflict(response)) {
            future.completeExceptionally(new SwitchErrorResponseException(getSw().getId(), String.format(
                    "Can't install meter %s - %s", meterConfig.getMeterId(), response)));
            return future;
        }

        CompletableFuture<Optional<OFMessage>> lookupExistingBranch = new CompletableFutureAdapter<>(
                 messageContext, getSw().writeStatsRequest(makeMeterReadCommand()))
                .thenAccept(this::ensureSameMeterExists)
                .thenApply(ignore -> Optional.empty());
        propagateFutureResponse(future, lookupExistingBranch);

        return future;
    }

    @Override
    protected void setup(FloodlightModuleContext moduleContext) throws SwitchNotFoundException {
        super.setup(moduleContext);

        FloodlightModuleConfigurationProvider provider =
                FloodlightModuleConfigurationProvider.of(moduleContext, SwitchManager.class);
        switchManagerConfig = provider.getConfiguration(SwitchManagerConfig.class);
    }

    private OFMeterMod makeMeterAddMessage() throws UnsupportedSwitchOperationException, InvalidMeterIdException {
        ensureSwitchSupportMeters();
        ensureMeterIdIsValid();

        final OFFactory ofFactory = getSw().getOFFactory();

        OFMeterMod.Builder meterModBuilder = ofFactory.buildMeterMod()
                .setMeterId(meterConfig.getMeterId().getValue())
                .setCommand(OFMeterModCommand.ADD)
                .setFlags(makeMeterFlags());

        // NB: some switches might replace 0 burst size value with some predefined value
        List<OFMeterBand> meterBand = makeMeterBands();
        if (ofFactory.getVersion().compareTo(OF_13) > 0) {
            meterModBuilder.setBands(meterBand);
        } else {
            meterModBuilder.setMeters(meterBand);
        }

        return meterModBuilder.build();
    }

    private OFMeterConfigStatsRequest makeMeterReadCommand() {
        return getSw().getOFFactory().buildMeterConfigStatsRequest()
                .setMeterId(meterConfig.getMeterId().getValue())
                .build();
    }

    private boolean isAddConflict(OFErrorMsg response) {
        if (!(response instanceof OFMeterModFailedErrorMsg)) {
            return false;
        }
        return ((OFMeterModFailedErrorMsg) response).getCode() == OFMeterModFailedCode.METER_EXISTS;
    }

    private void ensureMeterIdIsValid() throws InvalidMeterIdException {
        MeterId meterId = meterConfig.getMeterId();
        if (meterId == null || meterId.getValue() <= 0L) {
            throw new InvalidMeterIdException(getSw().getId(), String.format(
                    "Invalid meterId value - expect not negative integer, got - %s", meterId));
        }
    }

    private void ensureSameMeterExists(List<OFMeterConfigStatsReply> meterStatResponses) {
        Optional<OFMeterConfig> target = Optional.empty();
        for (OFMeterConfigStatsReply meterConfigReply : meterStatResponses) {
            target = findMeter(meterConfigReply);
            if (target.isPresent()) {
                break;
            }
        }

        if (target.isPresent()) {
            validateMeterConfig(target.get());
        } else {
            throw maskCallbackException(new SwitchMeterConflictException(
                    getSw().getId(), meterConfig.getMeterId(),
                    "switch report id conflict, but validation procedure can't locate it on switch (race condition?)"));
        }
    }

    private Optional<OFMeterConfig> findMeter(OFMeterConfigStatsReply meterConfigReply) {
        MeterId meterId = meterConfig.getMeterId();
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
            throw maskCallbackException(new SwitchMeterConflictException(getSw().getId(), meterConfig.getMeterId()));
        }
    }

    private void validateMeterConfigBands(OFMeterConfig config) {
        List<OFMeterBand> expectBands = makeMeterBands(0);  // to ignore burst value comparison
        List<OFMeterBand> actualBands = config.getEntries();

        if (expectBands.size() != actualBands.size()) {
            throw maskCallbackException(new SwitchMeterConflictException(getSw().getId(), meterConfig.getMeterId()));
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
            throw maskCallbackException(new SwitchMeterConflictException(getSw().getId(), meterConfig.getMeterId()));
        }
    }

    private Set<OFMeterFlags> makeMeterFlags() {
        return ImmutableSet.of(OFMeterFlags.KBPS, OFMeterFlags.BURST, OFMeterFlags.STATS);
    }

    private List<OFMeterBand> makeMeterBands() {
        long burstSize = Meter.calculateBurstSize(
                meterConfig.getBandwidth(), switchManagerConfig.getFlowMeterMinBurstSizeInKbits(),
                switchManagerConfig.getFlowMeterBurstCoefficient(),
                getSw().getSwitchDescription().getManufacturerDescription(),
                getSw().getSwitchDescription().getSoftwareDescription());
        return makeMeterBands(burstSize);
    }

    private List<OFMeterBand> makeMeterBands(long burstSize) {
        return ImmutableList.of(getSw().getOFFactory().meterBands()
                .buildDrop()
                .setRate(meterConfig.getBandwidth())
                .setBurstSize(burstSize)
                .build());
    }
}
