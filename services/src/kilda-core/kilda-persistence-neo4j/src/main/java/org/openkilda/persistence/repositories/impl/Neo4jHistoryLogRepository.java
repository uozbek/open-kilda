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

package org.openkilda.persistence.repositories.impl;

import org.openkilda.model.history.HistoryLog;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.history.HistoryLogRepository;

public class Neo4jHistoryLogRepository extends Neo4jGenericRepository<HistoryLog> implements HistoryLogRepository {
    Neo4jHistoryLogRepository(Neo4jSessionFactory sessionFactory, TransactionManager transactionManager) {
        super(sessionFactory, transactionManager);
    }

    @Override
    protected Class<HistoryLog> getEntityType() {
        return HistoryLog.class;
    }
}
