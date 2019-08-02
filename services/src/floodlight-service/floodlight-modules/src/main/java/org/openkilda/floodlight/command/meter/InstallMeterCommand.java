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

import static java.util.Collections.singletonList;
import static org.projectfloodlight.openflow.protocol.OFVersion.OF_13;

import org.openkilda.floodlight.command.IOfErrorResponseHandler;
import org.openkilda.floodlight.command.IdempotentMessageWriter;
import org.openkilda.floodlight.config.provider.FloodlightModuleConfigurationProvider;
import org.openkilda.floodlight.error.InvalidMeterIdException;
import org.openkilda.floodlight.error.SwitchErrorResponseException;
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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableSet;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.projectfloodlight.openflow.protocol.OFErrorMsg;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFMeterConfigStatsReply;
import org.projectfloodlight.openflow.protocol.OFMeterConfigStatsRequest;
import org.projectfloodlight.openflow.protocol.OFMeterFlags;
import org.projectfloodlight.openflow.protocol.OFMeterMod;
import org.projectfloodlight.openflow.protocol.OFMeterModCommand;
import org.projectfloodlight.openflow.protocol.OFMeterModFailedCode;
import org.projectfloodlight.openflow.protocol.errormsg.OFMeterModFailedErrorMsg;
import org.projectfloodlight.openflow.protocol.meterband.OFMeterBandDrop;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class InstallMeterCommand extends MeterCommand implements IOfErrorResponseHandler {
    private SwitchManagerConfig switchManagerConfig;

    private Long bandwidth;

    public InstallMeterCommand(@JsonProperty("message_context") MessageContext messageContext,
                               @JsonProperty("switch_id") SwitchId switchId,
                               @JsonProperty("meter_id") MeterId meterId,
                               @JsonProperty("bandwidth") Long bandwidth) {
        super(switchId, messageContext, meterId);
        this.bandwidth = bandwidth;
    }

    @Override
    protected void makeExecutePlan(CompletableFuture<Void> resultAdapter)
            throws UnsupportedSwitchOperationException, InvalidMeterIdException {
        final OFMeterMod meterAddCommand = makeMeterCreateCommand();
        try (Session session = getSessionService().open(getSw(), getMessageContext())) {
            CompletableFuture<Optional<OFMessage>> future = session.write(meterAddCommand);
            future = setupErrorHandler(future, this);
            setupExecPlanResultExtractor(resultAdapter, future);
        }
    }

    @Override
    public CompletableFuture<Optional<OFMessage>> handleOfError(OFErrorMsg response) {
        CompletableFuture<Optional<OFMessage>> future = new CompletableFuture<>();
        if (!isAddConflict(response)) {
            future.completeExceptionally(new SwitchErrorResponseException(getSw().getId(), String.format(
                    "Can't install meter %s - %s", meterId, response)));
            return future;
        }

        // TODO
        /*
        new CompletableFutureAdapter<>(getMessageContext(), getSw().writeRequest(makeMeterReadCommand()))
            checkConflict(sw)
                    .thenAccept(Void -> result.complete(response));}

        return IdempotentMessageWriter .<OFMeterConfigStatsReply>builder()
                                                 .readRequest(getMeterRequest(sw.getOFFactory()))
                                                 .ofEntryChecker(new MeterChecker(meterInstallCommand))
                                                 .build();
        */
        return future;
    }

    @Override
    protected void setup(FloodlightModuleContext moduleContext) throws SwitchNotFoundException {
        super.setup(moduleContext);

        FloodlightModuleConfigurationProvider provider =
                FloodlightModuleConfigurationProvider.of(moduleContext, SwitchManager.class);
        switchManagerConfig = provider.getConfiguration(SwitchManagerConfig.class);
    }

    private OFMeterMod makeMeterCreateCommand() throws UnsupportedSwitchOperationException, InvalidMeterIdException {
        ensureMeterIdIsValid();
        checkSwitchSupportCommand();

        final OFFactory ofFactory = getSw().getOFFactory();

        long burstSize = Meter.calculateBurstSize(
                bandwidth, switchManagerConfig.getFlowMeterMinBurstSizeInKbits(),
                switchManagerConfig.getFlowMeterBurstCoefficient(),
                getSw().getSwitchDescription().getManufacturerDescription(),
                getSw().getSwitchDescription().getSoftwareDescription());

        OFMeterMod.Builder meterModBuilder = ofFactory.buildMeterMod()
                .setMeterId(meterId.getValue())
                .setCommand(OFMeterModCommand.ADD)
                .setFlags(ImmutableSet.of(OFMeterFlags.KBPS, OFMeterFlags.BURST, OFMeterFlags.STATS));

        // NB: some switches might replace 0 burst size value with some predefined value
        OFMeterBandDrop.Builder bandBuilder = ofFactory.meterBands()
                .buildDrop()
                .setRate(bandwidth)
                .setBurstSize(burstSize);
        if (ofFactory.getVersion().compareTo(OF_13) > 0) {
            meterModBuilder.setBands(singletonList(bandBuilder.build()));
        } else {
            meterModBuilder.setMeters(singletonList(bandBuilder.build()));
        }

        return meterModBuilder.build();
    }

    private OFMeterConfigStatsRequest makeMeterReadCommand() {
        return getSw().getOFFactory().buildMeterConfigStatsRequest()
                .setMeterId(meterId.getValue())
                .build();
    }

    private boolean isAddConflict(OFErrorMsg response) {
        if (!(response instanceof OFMeterModFailedErrorMsg)) {
            return false;
        }
        if (((OFMeterModFailedErrorMsg) response).getCode() != OFMeterModFailedCode.METER_EXISTS) {
            return false;
        }
        return true;
    }

    private void ensureMeterIdIsValid() throws InvalidMeterIdException {
        if (meterId == null || meterId.getValue() <= 0L) {
            throw new InvalidMeterIdException(getSw().getId(), String.format(
                    "Invalid meterId value - expect not negative integer, got - %s", meterId));
        }
    }
}
