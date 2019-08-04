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

package org.openkilda.floodlight.command;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.getCurrentArguments;

import org.openkilda.floodlight.service.session.Session;
import org.openkilda.floodlight.service.session.SessionService;
import org.openkilda.messaging.MessageContext;

import lombok.AllArgsConstructor;
import lombok.Value;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.OFSwitchManager;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.easymock.EasyMockSupport;
import org.easymock.IAnswer;
import org.easymock.Mock;
import org.junit.After;
import org.junit.Before;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.ver13.OFFactoryVer13;
import org.projectfloodlight.openflow.types.DatapathId;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class AbstractSpeakerCommandTest extends EasyMockSupport {
    protected final FloodlightModuleContext moduleContext = new FloodlightModuleContext();

    protected final OFFactory of = new OFFactoryVer13();
    protected final DatapathId dpId = DatapathId.of(1);
    protected final Map<DatapathId, Iterator<Session>> switchSessionProducePlan = new HashMap<>();

    @Mock
    protected SessionService sessionService;

    @Mock
    protected OFSwitchManager ofSwitchManager;

    @Mock
    protected IOFSwitch sw;

    @Before
    public void setUp() throws Exception {
        injectMocks(this);

        moduleContext.addService(SessionService.class, sessionService);
        moduleContext.addService(OFSwitchManager.class, ofSwitchManager);

        expect(sw.getId()).andReturn(dpId).anyTimes();
        expect(sw.getOFFactory()).andReturn(of).anyTimes();

        expect(ofSwitchManager.getActiveSwitch(dpId)).andReturn(sw).anyTimes();
        expect(sessionService.open(anyObject(IOFSwitch.class), anyObject(MessageContext.class)))
                .andAnswer(new IAnswer<Session>() {
                    @Override
                    public Session answer() throws Throwable {
                        IOFSwitch target = (IOFSwitch) getCurrentArguments()[0];
                        Iterator<Session> producePlan = switchSessionProducePlan.get(target.getId());
                        return producePlan.next();
                    }
                });
    }

    @After
    public void tearDown() throws Exception {
        verifyAll();
    }

    @Value
    @AllArgsConstructor
    protected static class SessionWriteRecord {
        private final OFMessage request;
        private final CompletableFuture<Optional<OFMessage>> future;

        public SessionWriteRecord(OFMessage request) {
            this(request, new CompletableFuture<Optional<OFMessage>>());
        }
    }
}
