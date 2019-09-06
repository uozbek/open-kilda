/* Copyright 2018 Telstra Open Source
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

package org.openkilda.northbound.service;

import static java.util.Collections.singletonList;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.openkilda.messaging.info.switches.SwitchSyncResponse;
import org.openkilda.model.Cookie;
import org.openkilda.model.PathId;
import org.openkilda.model.SwitchId;
import org.openkilda.model.of.OfFlowSchema;
import org.openkilda.model.validate.FlowSegmentReference;
import org.openkilda.model.validate.OfFlowReference;
import org.openkilda.model.validate.ValidateDefaultOfFlowsReport;
import org.openkilda.model.validate.ValidateDefect;
import org.openkilda.model.validate.ValidateFlowSegmentReport;
import org.openkilda.model.validate.ValidateOfFlowDefect;
import org.openkilda.model.validate.ValidateSwitchReport;
import org.openkilda.northbound.MessageExchanger;
import org.openkilda.northbound.config.KafkaConfig;
import org.openkilda.northbound.converter.FlowMapper;
import org.openkilda.northbound.converter.FlowMapperImpl;
import org.openkilda.northbound.converter.SwitchMapper;
import org.openkilda.northbound.converter.SwitchMapperImpl;
import org.openkilda.northbound.dto.v1.switches.RulesSyncDto;
import org.openkilda.northbound.dto.v1.switches.RulesSyncResult;
import org.openkilda.northbound.dto.v1.switches.SwitchSyncResult;
import org.openkilda.northbound.messaging.MessagingChannel;
import org.openkilda.northbound.service.impl.SwitchServiceImpl;
import org.openkilda.northbound.utils.RequestCorrelationId;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Collections;
import java.util.concurrent.ExecutionException;

@RunWith(SpringRunner.class)
public class SwitchServiceTest {

    @Autowired
    private SwitchService switchService;

    @Autowired
    private MessageExchanger messageExchanger;

    @Before
    public void reset() {
        messageExchanger.resetMockedResponses();
    }

    @Test
    public void shouldSynchronizeRules() throws Exception {
        String correlationId = "sync-rules";
        RequestCorrelationId.create(correlationId);

        Long missingRule = 100L;
        Long excessRule = 101L;
        Long properRule = 10L;
        SwitchId switchId = new SwitchId(1L);

        ValidateSwitchReport report = makeValidateReport(switchId, missingRule, excessRule, properRule);
        SwitchSyncResponse rules = new SwitchSyncResponse(report, true, true, true);
        messageExchanger.mockResponse(correlationId, rules);

        RulesSyncResult result = switchService.syncRules(switchId).get();
        assertThat(result.getMissingRules(), is(singletonList(missingRule)));
        assertThat(result.getInstalledRules(), is(singletonList(missingRule)));
        assertThat(result.getExcessRules(), is(singletonList(excessRule)));
        assertThat(result.getInstalledRules(), is(singletonList(missingRule)));
    }

    @Test
    public void shouldSynchronizeSwitch() throws ExecutionException, InterruptedException {
        String correlationId = "not-sync-rules";
        RequestCorrelationId.create(correlationId);

        Long missingRule = 100L;
        Long excessRule = 101L;
        Long properRule = 10L;
        SwitchId switchId = new SwitchId(1L);

        ValidateSwitchReport report = makeValidateReport(switchId, missingRule, excessRule, properRule);
        SwitchSyncResponse validationResult = new SwitchSyncResponse(report, true, true, true);
        messageExchanger.mockResponse(correlationId, validationResult);

        SwitchSyncResult result = switchService.syncSwitch(switchId, true).get();
        RulesSyncDto rules = result.getRules();
        assertThat(rules.getMissing(), is(singletonList(missingRule)));
        assertThat(rules.getMisconfigured(), is(Collections.emptyList()));
        assertThat(rules.getInstalled(), is(singletonList(missingRule)));
        assertThat(rules.getExcess(), is(singletonList(excessRule)));
        assertThat(rules.getInstalled(), is(singletonList(missingRule)));
        assertThat(rules.getRemoved(), is(singletonList(excessRule)));
    }

    private ValidateSwitchReport makeValidateReport(
            SwitchId switchId, Long missingRule, Long excessRule, Long properRule) {
        return ValidateSwitchReport.builder()
                .datapath(switchId)
                .excessOfFlow(new OfFlowReference(0, new Cookie(excessRule), switchId))
                .segmentReport(new ValidateFlowSegmentReport(
                        new FlowSegmentReference("flow-0", new PathId("flow-0-fwd"), switchId, new Cookie(properRule)),
                        Collections.singletonList(new OfFlowReference(0, new Cookie(properRule), switchId)),
                        Collections.emptyList(), Collections.emptyList()))
                .segmentReport(new ValidateFlowSegmentReport(
                        new FlowSegmentReference("flow-1", new PathId("flow-1-fwd"), switchId, new Cookie(missingRule)),
                        Collections.emptyList(), Collections.emptyList(),
                        Collections.singletonList(
                                ValidateDefect.builder().flow(
                                        ValidateOfFlowDefect.builder()
                                                .reference(new OfFlowReference(0, new Cookie(missingRule), switchId))
                                                .expected(OfFlowSchema.builder()
                                                                  .tableId((short) 0)
                                                                  .cookie(new Cookie(missingRule))
                                                                  .build())
                                                .build()).build())))
                .defaultFlowsReport(new ValidateDefaultOfFlowsReport(
                        switchId, Collections.emptyList(), Collections.emptyList(), Collections.emptyList()))
                .build();
    }

    @TestConfiguration
    @Import(KafkaConfig.class)
    @PropertySource({"classpath:northbound.properties"})
    static class Config {
        @Bean
        public MessagingChannel messagingChannel() {
            return new MessageExchanger();
        }

        @Bean
        public SwitchService switchService() {
            return new SwitchServiceImpl();
        }

        @Bean
        public SwitchMapper switchMapper() {
            return new SwitchMapperImpl();
        }

        @Bean
        public FlowMapper flowMapper() {
            return new FlowMapperImpl();
        }
    }
}
