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

package org.openkilda.wfm.topology.flowhs.fsm.reroute.actions;

import static java.lang.String.format;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.action.FlowProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class RevertFlowStatusAction extends
        FlowProcessingAction<FlowRerouteFsm, State, Event, FlowRerouteContext> {

    private TransactionManager transactionManager;

    public RevertFlowStatusAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
        transactionManager = persistenceManager.getTransactionManager();
    }

    @Override
    protected void perform(State from, State to, Event event, FlowRerouteContext context, FlowRerouteFsm stateMachine) {
        String flowId = stateMachine.getFlowId();

        transactionManager.doInTransaction(() -> {
            Optional<Flow> foundFlow = flowRepository.findById(flowId);
            if (foundFlow.isPresent()) {
                Flow flow = foundFlow.get();
                for (FlowPath fp : flow.getPaths()) {
                    if (fp.hasFailedSegments()) {
                        fp.setStatus(FlowPathStatus.INACTIVE);
                    }
                }
                flow.setStatus(flow.computeFlowStatus());
                saveHistory(stateMachine, stateMachine.getCarrier(), flowId,
                        format("Recalculate flow status to %s.", flow.getStatus()));
                flowRepository.createOrUpdate(flow);
            }
        });
    }
}
