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

package org.openkilda.wfm.topology.flowhs.utils;

import org.openkilda.adapter.FlowSideAdapter;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.persistence.FetchStrategy;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;

import java.util.Optional;

public class FlowNotificationProvider {
    private final FlowRepository flowRepository;

    public FlowNotificationProvider(PersistenceManager persistenceManager) {
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        flowRepository = repositoryFactory.createFlowRepository();
    }

    public Optional<Flow> loadFlow(String flowId) {
        Optional<Flow> potentialFlow = flowRepository.findById(flowId, FetchStrategy.DIRECT_RELATIONS);
        if (! potentialFlow.isPresent()) {
            return potentialFlow;
        }

        Flow flow = potentialFlow.get();
        for (FlowPath path : flow.getPaths()) {
            path.setSrcSwitch(FlowSideAdapter.makeIngressAdapter(flow, path).getSwitch());
            path.setDestSwitch(FlowSideAdapter.makeEgressAdapter(flow, path).getSwitch());
        }

        return Optional.of(flow);
    }
}
