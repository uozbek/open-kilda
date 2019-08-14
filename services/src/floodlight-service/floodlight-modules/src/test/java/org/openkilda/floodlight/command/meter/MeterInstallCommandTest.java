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

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.getCurrentArguments;

import org.openkilda.floodlight.api.MeterConfig;
import org.openkilda.floodlight.command.AbstractSpeakerCommandTest;
import org.openkilda.floodlight.error.SessionErrorResponseException;
import org.openkilda.floodlight.error.SwitchErrorResponseException;
import org.openkilda.floodlight.error.SwitchMeterConflictException;
import org.openkilda.floodlight.error.UnsupportedSwitchOperationException;
import org.openkilda.floodlight.service.FeatureDetectorService;
import org.openkilda.floodlight.service.session.Session;
import org.openkilda.messaging.MessageContext;
import org.openkilda.messaging.model.SpeakerSwitchView.Feature;
import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchId;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import net.floodlightcontroller.core.SwitchDescription;
import net.floodlightcontroller.core.SwitchDisconnectedException;
import org.easymock.IAnswer;
import org.easymock.Mock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFBadRequestCode;
import org.projectfloodlight.openflow.protocol.OFErrorMsg;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFMeterConfig;
import org.projectfloodlight.openflow.protocol.OFMeterConfigStatsReply;
import org.projectfloodlight.openflow.protocol.OFMeterConfigStatsRequest;
import org.projectfloodlight.openflow.protocol.OFMeterMod;
import org.projectfloodlight.openflow.protocol.OFMeterModCommand;
import org.projectfloodlight.openflow.protocol.OFMeterModFailedCode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class MeterInstallCommandTest extends AbstractSpeakerCommandTest {
    private MessageContext messageContext = new MessageContext();
    private final MeterConfig meterConfig = new MeterConfig(new MeterId(2), 1000);
    private MeterInstallCommand command = new MeterInstallCommand(
            messageContext, new SwitchId(dpId.getLong()), meterConfig);

    private final SwitchDescription swDesc = SwitchDescription.builder()
            .setManufacturerDescription("manufacturer")
            .setSoftwareDescription("software")
            .build();

    private final List<SessionWriteRecord> writeHistory = new ArrayList<>();

    @Mock
    private FeatureDetectorService featureDetectorService;

    @Mock
    private Session session;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        expect(sw.getSwitchDescription()).andReturn(swDesc).anyTimes();

        moduleContext.addService(FeatureDetectorService.class, featureDetectorService);

        switchSessionProducePlan.put(dpId, ImmutableList.of(session).iterator());

        expect(session.write(anyObject(OFMessage.class)))
                .andAnswer(new IAnswer<CompletableFuture<Optional<OFMessage>>>() {
                    @Override
                    public CompletableFuture<Optional<OFMessage>> answer() throws Throwable {
                        SessionWriteRecord historyEntry = new SessionWriteRecord(
                                (OFMessage) (getCurrentArguments()[0]));
                        writeHistory.add(historyEntry);
                        return historyEntry.getFuture();
                    }
                })
                .anyTimes();

        session.close();
        expectLastCall();
    }

    @Test
    public void happyPath() throws Throwable {
        switchFeaturesSetup(true);
        replayAll();

        CompletableFuture<MeterReport> result = command.execute(moduleContext);

        SessionWriteRecord write0 = getWriteRecord(0);
        Assert.assertTrue(write0.getRequest() instanceof OFMeterMod);
        OFMeterMod request = (OFMeterMod) write0.getRequest();
        Assert.assertEquals(OFMeterModCommand.ADD, request.getCommand());

        write0.getFuture().complete(Optional.empty());

        result.get(4, TimeUnit.SECONDS).raiseError();
    }

    @Test
    public void switchDoNotSupportMeters() throws Throwable {
        switchFeaturesSetup(false);
        replayAll();

        CompletableFuture<MeterReport> result = command.execute(moduleContext);
        verifyErrorCompletion(result, UnsupportedSwitchOperationException.class);
    }

    @Test
    public void notConflictError() throws Throwable {
        switchFeaturesSetup(true);
        replayAll();

        CompletableFuture<MeterReport> result = command.execute(moduleContext);

        SessionWriteRecord write0 = getWriteRecord(0);
        OFErrorMsg error = sw.getOFFactory().errorMsgs().buildBadRequestErrorMsg()
                .setCode(OFBadRequestCode.BAD_LEN)
                .build();
        write0.getFuture().completeExceptionally(new SessionErrorResponseException(sw.getId(), error));
        verifyErrorCompletion(result, SwitchErrorResponseException.class);
    }

    @Test
    public void conflictError() throws Throwable {
        switchFeaturesSetup(true);
        SettableFuture<List<OFMeterConfigStatsReply>> metersConfigReplyFuture = setupMeterConfigStatsReply();
        replayAll();

        CompletableFuture<MeterReport> result = processConflictError();

        SessionWriteRecord write0 = getWriteRecord(0);
        OFMeterMod requestRaw = (OFMeterMod) write0.getRequest();
        OFMeterConfig existingMeterConfig = sw.getOFFactory().buildMeterConfig()
                .setMeterId(meterConfig.getId().getValue())
                .setFlags(requestRaw.getFlags())
                .setEntries(requestRaw.getMeters())
                .build();
        OFMeterConfigStatsReply statsReplyEntry = sw.getOFFactory().buildMeterConfigStatsReply()
                .setEntries(ImmutableList.of(existingMeterConfig))
                .build();

        metersConfigReplyFuture.set(ImmutableList.of(statsReplyEntry));

        result.get(4, TimeUnit.SECONDS).raiseError();
    }

    @Test
    public void missingConflictError() throws Throwable {
        switchFeaturesSetup(true);
        SettableFuture<List<OFMeterConfigStatsReply>> metersConfigReplyFuture = setupMeterConfigStatsReply();
        replayAll();

        CompletableFuture<MeterReport> result = processConflictError();

        metersConfigReplyFuture.set(Collections.emptyList());
        verifyErrorCompletion(result, SwitchMeterConflictException.class);
    }

    @Test
    public void conflictAndDisconnectError() throws Throwable {
        switchFeaturesSetup(true);
        SettableFuture<List<OFMeterConfigStatsReply>> metersConfigReplyFuture = setupMeterConfigStatsReply();
        replayAll();

        CompletableFuture<MeterReport> result = processConflictError();

        metersConfigReplyFuture.setException(new SwitchDisconnectedException(dpId));
        verifyErrorCompletion(result, SwitchDisconnectedException.class);
    }

    private void verifyErrorCompletion(CompletableFuture<MeterReport> result, Class<? extends Throwable> errorType)
            throws Throwable {
        try {
            result.get().raiseError();
            Assert.fail("must never reach this line");
        } catch (ExecutionException e) {
            Assert.assertNotNull(e.getCause());
            Assert.assertTrue(errorType.isAssignableFrom(e.getCause().getClass()));
        }
    }

    private CompletableFuture<MeterReport> processConflictError() throws Exception {
        CompletableFuture<MeterReport> result = command.execute(moduleContext);

        SessionWriteRecord write0 = getWriteRecord(0);
        OFErrorMsg error = sw.getOFFactory().errorMsgs().buildMeterModFailedErrorMsg()
                .setCode(OFMeterModFailedCode.METER_EXISTS)
                .build();
        write0.getFuture().completeExceptionally(new SessionErrorResponseException(sw.getId(), error));

        return result;
    }

    private SettableFuture<List<OFMeterConfigStatsReply>> setupMeterConfigStatsReply() {
        SettableFuture<List<OFMeterConfigStatsReply>> meterStatsReply = SettableFuture.create();
        expect(sw.writeStatsRequest(anyObject(OFMeterConfigStatsRequest.class)))
                .andAnswer(new IAnswer<ListenableFuture<List<OFMeterConfigStatsReply>>>() {
                    @Override
                    public ListenableFuture<List<OFMeterConfigStatsReply>> answer() throws Throwable {
                        return meterStatsReply;
                    }
                });
        return meterStatsReply;
    }

    private void switchFeaturesSetup(boolean metersSupport) {
        Set<Feature> features = new HashSet<>();

        if (metersSupport) {
            features.add(Feature.METERS);
        }

        expect(featureDetectorService.detectSwitch(sw))
                .andReturn(ImmutableSet.copyOf(features))
                .anyTimes();
    }

    private SessionWriteRecord getWriteRecord(int idx) {
        Assert.assertTrue(idx < writeHistory.size());
        return writeHistory.get(0);
    }
}
