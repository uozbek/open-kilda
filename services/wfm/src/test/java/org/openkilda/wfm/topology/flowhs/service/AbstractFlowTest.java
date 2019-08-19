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

import org.openkilda.floodlight.flow.request.GetInstalledRule;
import org.openkilda.floodlight.api.request.EgressFlowSegmentInstallRequest;
import org.openkilda.floodlight.flow.request.InstallFlowRule;
import org.openkilda.floodlight.api.request.SpeakerIngressActModRequest;
import org.openkilda.floodlight.api.request.TransitFlowSegmentInstallRequest;
import org.openkilda.floodlight.api.request.FlowSegmentRequest;
import org.openkilda.floodlight.api.response.SpeakerActModResponse;
import org.openkilda.floodlight.flow.response.FlowRuleResponse;
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
import java.util.Map;
import java.util.Optional;
import java.util.Queue;

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
    final Map<SwitchId, Map<Cookie, InstallFlowRule>> installedRules = new HashMap<>();

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

            if (request instanceof InstallFlowRule) {
                Map<Cookie, InstallFlowRule> switchRules =
                        installedRules.getOrDefault(request.getSwitchId(), new HashMap<>());
                switchRules.put(((InstallFlowRule) request).getCookie(), ((InstallFlowRule) request));
                installedRules.put(request.getSwitchId(), switchRules);
            }
            return request;
        };
    }

    SpeakerActModResponse buildResponseOnGetInstalled(GetInstalledRule request) {
        Cookie cookie = request.getCookie();

        InstallFlowRule rule = Optional.ofNullable(installedRules.get(request.getSwitchId()))
                .map(switchRules -> switchRules.get(cookie))
                .orElse(null);

        FlowRuleResponse.FlowRuleResponseBuilder builder = FlowRuleResponse.flowRuleResponseBuilder()
                .commandId(request.getCommandId())
                .flowId(request.getFlowId())
                .switchId(request.getSwitchId())
                .cookie(rule.getCookie())
                .inPort(rule.getInputPort())
                .outPort(rule.getOutputPort());
        if (rule instanceof EgressFlowSegmentInstallRequest) {
            builder.inVlan(((EgressFlowSegmentInstallRequest) rule).getTransitEncapsulationId());
            builder.outVlan(((EgressFlowSegmentInstallRequest) rule).getOutputVlanId());
        } else if (rule instanceof TransitFlowSegmentInstallRequest) {
            builder.inVlan(((TransitFlowSegmentInstallRequest) rule).getTransitEncapsulationId());
            builder.outVlan(((TransitFlowSegmentInstallRequest) rule).getTransitEncapsulationId());
        } else if (rule instanceof SpeakerIngressActModRequest) {
            SpeakerIngressActModRequest ingressRule = (SpeakerIngressActModRequest) rule;
            builder.inVlan(ingressRule.getInputOuterVlanId())
                    .meterId(ingressRule.getMeterId());
        }

        return builder.build();
    }

}
