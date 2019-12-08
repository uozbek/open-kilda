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

package org.openkilda.wfm.topology.stats.bolts;

import static org.openkilda.wfm.topology.stats.StatsComponentType.STATS_OFS_BOLT;
import static org.openkilda.wfm.topology.stats.StatsStreamType.FLOW_STATS;
import static org.openkilda.wfm.topology.stats.StatsStreamType.METER_STATS;
import static org.openkilda.wfm.topology.stats.StatsTopology.STATS_FIELD;

import org.openkilda.api.priv.notifycation.FlowCreateNotification;
import org.openkilda.api.priv.notifycation.FlowDeleteNotification;
import org.openkilda.api.priv.notifycation.FlowNotification;
import org.openkilda.api.priv.notifycation.FlowNotificationHandler;
import org.openkilda.api.priv.notifycation.FlowUpdateNotification;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.stats.FlowStatsData;
import org.openkilda.messaging.info.stats.FlowStatsEntry;
import org.openkilda.messaging.info.stats.MeterStatsData;
import org.openkilda.messaging.info.stats.MeterStatsEntry;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.topology.stats.CacheFlowEntry;
import org.openkilda.wfm.topology.stats.MeasurePoint;
import org.openkilda.wfm.topology.stats.MeterCacheKey;
import org.openkilda.wfm.topology.stats.StatsComponentType;
import org.openkilda.wfm.topology.utils.KafkaRecordTranslator;

import com.google.common.annotations.VisibleForTesting;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

public class CacheBolt extends AbstractBolt implements FlowNotificationHandler {

    public static final String COOKIE_CACHE_FIELD = "cookie_cache";
    public static final String METER_CACHE_FIELD = "meter_cache";

    public static final Fields statsWithCacheFields =
            new Fields(STATS_FIELD, COOKIE_CACHE_FIELD, METER_CACHE_FIELD, FIELD_ID_CONTEXT);
    /**
     * The logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(
            CacheBolt.class);

    /**
     * Path computation instance.
     */
    private final PersistenceManager persistenceManager;

    /**
     * Cookie to flow and meter to flow maps.
     */
    private Map<Long, CacheFlowEntry> cookieToFlow = new HashMap<>();
    private Map<MeterCacheKey, CacheFlowEntry> switchAndMeterToFlow = new HashMap<>();

    public CacheBolt(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    private void initFlowCache(FlowRepository flowRepository) {
        try {
            flowRepository.findAll().stream()
                    .flatMap(this::extractAllFlowPaths)
                    .forEach(path -> {
                        CacheFlowEntry entry = new CacheFlowEntry(
                                path.getFlow().getFlowId(),
                                path.getSrcSwitch().getSwitchId().toOtsdFormat(),
                                path.getDestSwitch().getSwitchId().toOtsdFormat(),
                                path.getCookie().getValue());

                        cookieToFlow.put(path.getCookie().getValue(), entry);
                        if (path.getMeterId() != null) {
                            switchAndMeterToFlow.put(
                                    new MeterCacheKey(
                                            path.getSrcSwitch().getSwitchId(), path.getMeterId().getValue()), entry);
                        } else {
                            log.warn("Flow {} has no meter ID", path.getFlow().getFlowId());
                        }
                    });
            logger.debug("cookieToFlow cache: {}, switchAndMeterToFlow cache: {}", cookieToFlow, switchAndMeterToFlow);
            logger.info("Stats Cache: Initialized");
        } catch (Exception ex) {
            logger.error("Error on initFlowCache", ex);
        }
    }

    private Stream<FlowPath> extractAllFlowPaths(Flow flow) {
        return Stream.concat(
                Stream.of(flow.getForwardPath(), flow.getProtectedForwardPath()).filter(Objects::nonNull).peek(p -> {
                    p.setSrcSwitch(flow.getSrcSwitch());
                    p.setDestSwitch(flow.getDestSwitch());
                }),
                Stream.of(flow.getReversePath(), flow.getProtectedReversePath()).filter(Objects::nonNull).peek(p -> {
                    p.setSrcSwitch(flow.getDestSwitch());
                    p.setDestSwitch(flow.getSrcSwitch());
                })
        );
    }

    @Override
    public void init() {
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        initFlowCache(repositoryFactory.createFlowRepository());
    }

    @Override
    protected void handleInput(Tuple tuple) throws PipelineException {
        String sourceComponent = tuple.getSourceComponent();

        if (sourceComponent.equals(StatsComponentType.INPUT_FLOW_NOTIFICATION.name())) {
            handleUpdateCache(tuple);
        } else if (sourceComponent.equals(STATS_OFS_BOLT.name())) {
            handleGetDataFromCache(tuple);
        } else {
            unhandledInput(tuple);
        }
    }

    private void handleGetDataFromCache(Tuple tuple) throws PipelineException {
        InfoData data = pullValue(tuple, STATS_FIELD, InfoData.class);
        Map<Long, CacheFlowEntry> cookieDataCache = null;
        Map<MeterCacheKey, CacheFlowEntry> meterDataCache = null;
        String streamId;

        if (data instanceof FlowStatsData) {
            streamId = FLOW_STATS.name();
            cookieDataCache = createCookieToFlowCache((FlowStatsData) data);
            logger.debug("execute:cookieDataCache: {}", cookieDataCache);
        } else if (data instanceof MeterStatsData) {
            streamId = METER_STATS.name();
            meterDataCache = createSwitchAndMeterToFlowCache((MeterStatsData) data);
            logger.debug("execute:meterDataCache: {}", meterDataCache);
        } else {
            unhandledInput(tuple);
            return;
        }

        Values values = new Values(data, cookieDataCache, meterDataCache, getCommandContext());
        getOutput().emit(streamId, tuple, values);
    }

    private void handleUpdateCache(Tuple input) throws PipelineException {
        FlowNotification notification = pullValue(
                input, KafkaRecordTranslator.FIELD_ID_PAYLOAD, FlowNotification.class);
        routeFlowNotification(notification);
    }

    @VisibleForTesting
    Map<Long, CacheFlowEntry> createCookieToFlowCache(FlowStatsData data) {
        Map<Long, CacheFlowEntry> cache = new HashMap<>();

        for (FlowStatsEntry entry : data.getStats()) {
            if (cookieToFlow.containsKey(entry.getCookie())) {
                CacheFlowEntry cacheFlowEntry = cookieToFlow.get(entry.getCookie());
                cache.put(entry.getCookie(), cacheFlowEntry);
            }
        }
        return cache;
    }

    @VisibleForTesting
    Map<MeterCacheKey, CacheFlowEntry> createSwitchAndMeterToFlowCache(MeterStatsData data) {
        Map<MeterCacheKey, CacheFlowEntry> cache = new HashMap<>();

        for (MeterStatsEntry entry : data.getStats()) {
            MeterCacheKey key = new MeterCacheKey(data.getSwitchId(), entry.getMeterId());
            if (switchAndMeterToFlow.containsKey(key)) {
                CacheFlowEntry cacheEntry = switchAndMeterToFlow.get(key);
                cache.put(key, cacheEntry);
            }
        }
        return cache;
    }

    @Override
    public void routeFlowNotification(FlowNotification notification) {
        notification.route(this);
    }

    @Override
    public void handleFlowNotification(FlowCreateNotification notification) {
        updateCacheAdd(notification.getFlow());
    }

    @Override
    public void handleFlowNotification(FlowUpdateNotification notification) {
        updateCacheDelete(notification.getBefore());
        updateCacheAdd(notification.getAfter());
    }

    @Override
    public void handleFlowNotification(FlowDeleteNotification notification) {
        updateCacheDelete(notification.getFlow());
    }

    private void updateCacheAdd(Flow flow) {
        for (FlowPath path : flow.getPaths()) {
            updateCacheAddPath(flow, path);
        }
    }

    private void updateCacheDelete(Flow flow) {
        for (FlowPath path : flow.getPaths()) {
            updateCacheDeletePath(flow, path);
        }
    }

    private void updateCacheAddPath(Flow flow, FlowPath path) {
        SwitchId ingressSwitchId = path.getSrcSwitch().getSwitchId();
        updateCookieFlowCache(
                path.getCookie().getValue(), flow.getFlowId(), ingressSwitchId, MeasurePoint.INGRESS);
        updateCookieFlowCache(
                path.getCookie().getValue(), flow.getFlowId(), path.getDestSwitch().getSwitchId(), MeasurePoint.EGRESS);

        MeterId meterId = path.getMeterId();
        if (meterId != null) {
            updateSwitchMeterFlowCache(
                    path.getCookie().getValue(), meterId.getValue(), flow.getFlowId(), ingressSwitchId);
        }
    }

    private void updateCacheDeletePath(Flow flow, FlowPath path) {
        long rawCookie = path.getCookie().getValue();
        cookieToFlow.remove(rawCookie);

        MeterId meterId = path.getMeterId();
        if (meterId != null) {
            switchAndMeterToFlow.remove(new MeterCacheKey(path.getSrcSwitch().getSwitchId(), meterId.getValue()));
        }
    }

    private void updateCookieFlowCache(
            Long cookie, String flowId, SwitchId switchId, MeasurePoint measurePoint) {
        CacheFlowEntry current = cookieToFlow.getOrDefault(cookie, new CacheFlowEntry(flowId, cookie));
        CacheFlowEntry replacement = current.replaceSwitch(switchId.toOtsdFormat(), measurePoint);
        cookieToFlow.put(cookie, replacement);

    }

    private void updateSwitchMeterFlowCache(Long cookie, Long meterId, String flowId, SwitchId switchId) {
        MeterCacheKey key = new MeterCacheKey(switchId, meterId);
        CacheFlowEntry current = switchAndMeterToFlow.get(key);

        if (current == null) {
            switchAndMeterToFlow.put(key, new CacheFlowEntry(flowId, cookie));
        } else {
            switchAndMeterToFlow.put(key, current.replaceCookie(cookie));
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        outputFieldsDeclarer.declareStream(FLOW_STATS.name(), statsWithCacheFields);
        outputFieldsDeclarer.declareStream(METER_STATS.name(), statsWithCacheFields);
    }
}
