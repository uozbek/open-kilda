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

package org.openkilda.floodlight.command.flow;

import static org.projectfloodlight.openflow.protocol.OFVersion.OF_12;

import org.openkilda.floodlight.command.SpeakerCommand;
import org.openkilda.floodlight.error.SwitchNotFoundException;
import org.openkilda.floodlight.model.SwitchDescriptor;
import org.openkilda.floodlight.service.FeatureDetectorService;
import org.openkilda.floodlight.utils.CompletableFutureAdapter;
import org.openkilda.messaging.MessageContext;
import org.openkilda.messaging.model.SpeakerSwitchView;
import org.openkilda.model.Cookie;
import org.openkilda.model.SwitchId;

import lombok.AccessLevel;
import lombok.Getter;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.util.FlowModUtils;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFFlowStatsEntry;
import org.projectfloodlight.openflow.protocol.OFFlowStatsReply;
import org.projectfloodlight.openflow.protocol.OFFlowStatsRequest;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.OFGroup;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.U64;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public abstract class AbstractFlowSegmentCommand extends SpeakerCommand<FlowSegmentReport> {
    protected static final long FLOW_COOKIE_MASK = 0x7FFFFFFFFFFFFFFFL;
    protected static final int FLOW_PRIORITY = FlowModUtils.PRIORITY_HIGH;

    // payload
    protected final UUID commandId;
    protected final String flowId;
    protected final Cookie cookie;

    // operation data
    @Getter(AccessLevel.PROTECTED)
    private FloodlightModuleContext moduleContext;

    @Getter(AccessLevel.PROTECTED)
    private Set<SpeakerSwitchView.Feature> switchFeatures;
    @Getter(AccessLevel.PROTECTED)
    private SwitchDescriptor switchDescriptor;

    public AbstractFlowSegmentCommand(
            MessageContext messageContext, SwitchId switchId, UUID commandId, String flowId, Cookie cookie) {
        super(messageContext, switchId);
        this.commandId = commandId;
        this.flowId = flowId;
        this.cookie = cookie;
    }

    protected FlowSegmentReport makeReport(Exception error) {
        return new FlowSegmentReport(this, error);
    }

    protected FlowSegmentReport makeSuccessReport() {
        return new FlowSegmentReport(this);
    }

    protected abstract OFFlowMod.Builder makeFlowModBuilder();

    @Override
    protected void setup(FloodlightModuleContext moduleContext) throws SwitchNotFoundException {
        super.setup(moduleContext);

        this.moduleContext = moduleContext;

        FeatureDetectorService featureDetectorService = moduleContext.getServiceImpl(FeatureDetectorService.class);
        switchFeatures = featureDetectorService.detectSwitch(getSw());

        switchDescriptor = new SwitchDescriptor(getSw());
    }

    final Match matchFlow(Integer inputPort, Integer inputVlan, OFFactory ofFactory) {
        Match.Builder mb = ofFactory.buildMatch();
        mb.setExact(MatchField.IN_PORT, OFPort.of(inputPort));
        if (inputVlan > 0) {
            matchVlan(ofFactory, mb, inputVlan);
        }

        return mb.build();
    }

    final void matchVlan(OFFactory ofFactory, Match.Builder matchBuilder, int vlanId) {
        if (OF_12.compareTo(ofFactory.getVersion()) >= 0) {
            // This is invalid VID mask - it cut of highest bit that indicate presence of VLAN tag on package. But valid
            // mask 0x1FFF lead to rule reject during install attempt on accton based switches.
            // TODO(surabujin): we should use exact match here
            matchBuilder.setMasked(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(vlanId),
                                   OFVlanVidMatch.ofRawVid((short) 0x0FFF));
        } else {
            matchBuilder.setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(vlanId));
        }
    }

    protected final CompletableFuture<List<OFFlowStatsEntry>> planOfFlowTableDump() {
        return planOfFlowTableDump(makeOfFlowTableDumpBuilder().build());
    }

    protected final CompletableFuture<List<OFFlowStatsEntry>> planOfFlowTableDump(Cookie matchCookie) {
        OFFlowStatsRequest dumpMessage = makeOfFlowTableDumpBuilder()
                .setCookie(U64.of(matchCookie.getValue()))
                .setCookieMask(U64.NO_MASK)
                .build();
        return planOfFlowTableDump(dumpMessage);
    }

    private CompletableFuture<List<OFFlowStatsEntry>> planOfFlowTableDump(OFFlowStatsRequest dumpMessage) {
        CompletableFuture<List<OFFlowStatsReply>> future =
                new CompletableFutureAdapter<>(messageContext, getSw().writeStatsRequest(dumpMessage));
        return future.thenApply(values -> values.stream()
                .map(OFFlowStatsReply::getEntries)
                .flatMap(List::stream)
                .collect(Collectors.toList()));
    }

    private OFFlowStatsRequest.Builder makeOfFlowTableDumpBuilder() {
        return getSw().getOFFactory().buildFlowStatsRequest()
                .setOutGroup(OFGroup.ANY)
                .setCookieMask(U64.ZERO);
    }
}
