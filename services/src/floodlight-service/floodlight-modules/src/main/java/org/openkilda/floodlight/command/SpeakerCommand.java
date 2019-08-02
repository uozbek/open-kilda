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

import org.openkilda.floodlight.KafkaChannel;
import org.openkilda.floodlight.command.flow.FlowRemoveCommand;
import org.openkilda.floodlight.command.flow.GetRuleCommand;
import org.openkilda.floodlight.command.flow.InstallEgressRuleCommand;
import org.openkilda.floodlight.command.flow.InstallIngressRuleCommand;
import org.openkilda.floodlight.command.flow.InstallOneSwitchRuleCommand;
import org.openkilda.floodlight.command.flow.InstallTransitRuleCommand;
import org.openkilda.floodlight.service.kafka.IKafkaProducerService;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import lombok.Getter;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@JsonTypeInfo(use = Id.NAME, property = "clazz")
@JsonSubTypes({
        @Type(value = InstallIngressRuleCommand.class,
                name = "org.openkilda.floodlight.flow.request.InstallMultiSwitchIngressRule"),
        @Type(value = InstallOneSwitchRuleCommand.class,
                name = "org.openkilda.floodlight.flow.request.InstallSingleSwitchIngressRule"),
        @Type(value = InstallTransitRuleCommand.class,
                name = "org.openkilda.floodlight.flow.request.InstallTransitRule"),
        @Type(value = InstallEgressRuleCommand.class,
                name = "org.openkilda.floodlight.flow.request.InstallEgressRule"),
        @Type(value = FlowRemoveCommand.class,
                name = "org.openkilda.floodlight.flow.request.RemoveRule"),
        @Type(value = GetRuleCommand.class,
                name = "org.openkilda.floodlight.flow.request.GetInstalledRule")
})
@Getter
public abstract class SpeakerCommand {
    protected final SwitchId switchId;
    protected final MessageContext messageContext;

    protected final Logger log = LoggerFactory.getLogger(getClass());

    public SpeakerCommand(SwitchId switchId, MessageContext messageContext) {
        this.switchId = switchId;
        this.messageContext = messageContext;
    }

    public abstract CompletableFuture<Void> execute(FloodlightModuleContext moduleContext);

    public abstract void handleResult(KafkaChannel kafkaChannel, IKafkaProducerService kafkaProducerService,
                                      String requestKey, Throwable error);

    protected Throwable unwrapError(Throwable error) {
        if (error == null) {
            return null;
        }

        if (error instanceof ExecutionException) {
            return error.getCause();
        }
        return error;
    }
}
