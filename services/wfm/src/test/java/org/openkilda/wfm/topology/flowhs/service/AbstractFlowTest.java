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

package org.openkilda.wfm.topology.flowhs.service;

import static org.mockito.Mockito.when;

import org.openkilda.floodlight.api.request.EgressFlowSegmentInstallRequest;
import org.openkilda.floodlight.api.request.EgressFlowSegmentRemoveRequest;
import org.openkilda.floodlight.api.request.EgressFlowSegmentVerifyRequest;
import org.openkilda.floodlight.api.request.FlowSegmentRequest;
import org.openkilda.floodlight.api.request.IngressFlowSegmentInstallRequest;
import org.openkilda.floodlight.api.request.IngressFlowSegmentRemoveRequest;
import org.openkilda.floodlight.api.request.IngressFlowSegmentVerifyRequest;
import org.openkilda.floodlight.api.request.OneSwitchFlowInstallRequest;
import org.openkilda.floodlight.api.request.OneSwitchFlowRemoveRequest;
import org.openkilda.floodlight.api.request.OneSwitchFlowVerifyRequest;
import org.openkilda.floodlight.api.request.TransitFlowSegmentInstallRequest;
import org.openkilda.floodlight.api.request.TransitFlowSegmentRemoveRequest;
import org.openkilda.floodlight.api.request.TransitFlowSegmentVerifyRequest;
import org.openkilda.floodlight.api.response.SpeakerFlowSegmentResponse;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.Cookie;
import org.openkilda.model.SwitchId;
import org.openkilda.pce.PathComputer;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.TransactionCallback;
import org.openkilda.persistence.TransactionCallbackWithoutResult;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;

import lombok.SneakyThrows;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.junit.Before;
import org.mockito.Mock;
import org.mockito.stubbing.Answer;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.stream.Stream;

public abstract class AbstractFlowTest {
    @Mock
    PersistenceManager persistenceManager;
    @Mock
    FlowRepository flowRepository;
    @Mock
    FlowPathRepository flowPathRepository;
    @Mock
    PathComputer pathComputer;
    @Mock
    FlowResourcesManager flowResourcesManager;

    final Queue<FlowSegmentRequest> requests = new ArrayDeque<>();
    final Map<SwitchId, Map<Cookie, FlowSegmentRequest>> installedSegments = new HashMap<>();

    private static final Iterator<Integer> idGenerator = Stream.iterate(0, current -> current + 1).iterator();

    @Before
    public void before() {
        when(persistenceManager.getTransactionManager()).thenReturn(new TransactionManager() {
            @SneakyThrows
            @Override
            public <T, E extends Throwable> T doInTransaction(TransactionCallback<T, E> action) throws E {
                return action.doInTransaction();
            }

            @Override
            public <T, E extends Throwable> T doInTransaction(RetryPolicy retryPolicy, TransactionCallback<T, E> action)
                    throws E {
                return Failsafe.with(retryPolicy).get(action::doInTransaction);
            }

            @SneakyThrows
            @Override
            public <E extends Throwable> void doInTransaction(TransactionCallbackWithoutResult<E> action) throws E {
                action.doInTransaction();
            }

            @Override
            public <E extends Throwable> void doInTransaction(RetryPolicy retryPolicy,
                                                              TransactionCallbackWithoutResult<E> action) throws E {
                Failsafe.with(retryPolicy).run(action::doInTransaction);
            }
        });
    }

    Answer getSpeakerCommandsAnswer() {
        return invocation -> {
            FlowSegmentRequest request = invocation.getArgument(0);
            requests.offer(request);

            if (isFlowSegmentInstallRequest(request)) {
                installedSegments.computeIfAbsent(request.getSwitchId(), ignore -> new HashMap<>())
                        .put(request.getCookie(), request);
            }

            return request;
        };
    }

    SpeakerFlowSegmentResponse buildResponseOnVerifyRequest(FlowSegmentRequest request) {
        return SpeakerFlowSegmentResponse.builder()
                .commandId(request.getCommandId())
                .flowId(request.getFlowId())
                .cookie(request.getCookie())
                .messageContext(request.getMessageContext())
                .switchId(request.getSwitchId())
                .success(true)
                .build();
    }

    protected boolean isFlowSegmentInstallRequest(FlowSegmentRequest request) {
        return request instanceof TransitFlowSegmentInstallRequest
                || request instanceof IngressFlowSegmentInstallRequest
                || request instanceof OneSwitchFlowInstallRequest
                || request instanceof EgressFlowSegmentInstallRequest;
    }

    protected boolean isFlowSegmentVerifyRequest(FlowSegmentRequest request) {
        return request instanceof TransitFlowSegmentVerifyRequest
                || request instanceof IngressFlowSegmentVerifyRequest
                || request instanceof OneSwitchFlowVerifyRequest
                || request instanceof EgressFlowSegmentVerifyRequest;
    }

    protected boolean isFlowSegmentRemoveRequest(FlowSegmentRequest request) {
        return request instanceof TransitFlowSegmentRemoveRequest
                || request instanceof IngressFlowSegmentRemoveRequest
                || request instanceof OneSwitchFlowRemoveRequest
                || request instanceof EgressFlowSegmentRemoveRequest;
    }

    protected MessageContext makeMessageContext() {
        return new MessageContext(String.format(getClass().getCanonicalName() + "-unit-test--%d", idGenerator.next()));
    }
}
