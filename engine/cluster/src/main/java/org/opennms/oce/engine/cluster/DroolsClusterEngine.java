/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2019 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2019 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.oce.engine.cluster;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.drools.core.time.SessionPseudoClock;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.FactHandle;
import org.opennms.oce.datasource.api.Alarm;
import org.opennms.oce.datasource.api.AlarmFeedback;
import org.opennms.oce.datasource.api.InventoryObject;
import org.opennms.oce.datasource.api.Situation;
import org.opennms.oce.datasource.api.SituationHandler;
import org.opennms.oce.engine.api.Engine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Sets;

import edu.uci.ics.jung.algorithms.shortestpath.DijkstraShortestPath;
import edu.uci.ics.jung.graph.Graph;

public class DroolsClusterEngine implements Engine, SpatialDistanceCalculator {
    private static Logger LOG = LoggerFactory.getLogger(DroolsClusterEngine.class);

    private final double epsilon = ClusterEngine.DEFAULT_EPSILON;
    private final double alpha = ClusterEngine.DEFAULT_ALPHA;
    private final double beta = ClusterEngine.DEFAULT_BETA;
    private DijkstraShortestPath<CEVertex, CEEdge> shortestPath;

    private final AlarmInSpaceTimeDistanceMeasure distanceMeasure;

    private final ManagedDroolsContext managedDroolsContext;

    private KieSession kieSession;
    private SessionPseudoClock pseudoClock;

    private final RuleService ruleService;

    private final GraphManager graphManager = new GraphManager();
    private final Map<String, Situation> situationsById = new HashMap<>();
    private final Map<String, Situation> alarmIdToSituationMap = new HashMap<>();

    private final Map<CEVertex, FactHandle> vertexToFactMap = new HashMap<>();
    private final Map<CEAlarm, FactHandle> alarmToFactMap = new HashMap<>();
    private final Map<String, FactHandle> situationIdToFactMap = new HashMap<>();

    private boolean alarmsChangedSinceLastTick = false;

    private long tickResolutionMs = TimeUnit.SECONDS.toMillis(30);

    // Used to prevent processing callbacks before the init has completed
    private final CountDownLatch initLock = new CountDownLatch(1);

    private SituationHandler situationHandler;

    public DroolsClusterEngine() {
        distanceMeasure = new AlarmInSpaceTimeDistanceMeasure(this, alpha, beta);
        ruleService = new RuleService(this, distanceMeasure);

        managedDroolsContext = new ManagedDroolsContext(
                new File("/home/jesse/git/oce/engine/cluster/src/main/resources/org/opennms/oce/engine/cluster"),
                "clusterEngineKB",
                "clusterEngineSession");
        managedDroolsContext.setUseManualTick(true);
        managedDroolsContext.setUsePseudoClock(true);
        managedDroolsContext.setOnNewKiewSessionCallback(kieSession -> {
            kieSession.setGlobal("svc", ruleService);
        });
    }

    @Override
    public void init(List<Alarm> alarms, List<AlarmFeedback> alarmFeedback, List<Situation> situations, List<InventoryObject> inventory) {
        try {
            managedDroolsContext.start();
            kieSession = managedDroolsContext.getKieSession();
            pseudoClock = managedDroolsContext.getClock();

            LOG.debug("Initialized with {} alarms, {} alarm feedback, {} situations and {} inventory objects.",
                    alarms.size(), alarmFeedback.size(), situations.size(), inventory.size());
            LOG.trace("Alarms on init: {}", alarms);
            LOG.trace("Situations on init: {}", situations);
            LOG.trace("Inventory objects on init: {}", inventory);
            graphManager.addInventory(inventory);
            graphManager.addOrUpdateAlarms(alarms);

            // Index the given situations and the alarms they contain, so that we can cluster alarms in existing
            // situations when applicable
            situations.forEach(situation -> {
                situationsById.put(situation.getId(), situation);
                if (situation.getAlarms() != null) {
                    for (Alarm alarmInSituation : situation.getAlarms()) {
                        alarmIdToSituationMap.put(alarmInSituation.getId(), situation);
                    }
                }
            });

            // Process all the alarm feedback provided on init
            alarmFeedback.forEach(this::handleAlarmFeedback);

            if (alarms.size() > 0) {
                alarmsChangedSinceLastTick = true;
            }
        } finally {
            initLock.countDown();
        }
    }

    @Override
    public void tick(long timestampInMillis) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Tick at {} ({})", new Date(timestampInMillis), timestampInMillis);
        }
        long delta = timestampInMillis - pseudoClock.getCurrentTime();
        if (delta < 0) {
            throw new IllegalStateException("" + delta);
        }
        pseudoClock.advanceTime(delta, TimeUnit.MILLISECONDS);

        // Synchronize facts
        // TODO: We should be smarter here and make this more efficient
        graphManager.withGraph(g -> {
            final Set<CEVertex> verticesThatShouldBeInDrools = new HashSet<>();
            for (CEVertex vertex : g.getVertices()) {
                if (!vertex.getAlarms().isEmpty()) {
                    verticesThatShouldBeInDrools.add(vertex);
                }
            }
            final Set<CEVertex> verticesThatAreInDrools = new HashSet<>(vertexToFactMap.keySet());

            // Remove vertices that shouldn't be there
            for (CEVertex vertex : Sets.difference(verticesThatAreInDrools, verticesThatShouldBeInDrools)) {
                final FactHandle fact = vertexToFactMap.get(vertex);
                kieSession.delete(fact);
            }

            // Add missing vertices
            for (CEVertex vertex : Sets.difference(verticesThatShouldBeInDrools, verticesThatAreInDrools)) {
                vertexToFactMap.put(vertex, kieSession.insert(vertex));

                for (CEAlarm alarm : vertex.getCEAlarms()) {
                    alarmToFactMap.put(alarm, kieSession.insert(alarm));
                }
            }

            // Update existing vertices
            for (CEVertex vertex : verticesThatAreInDrools) {
                kieSession.update(vertexToFactMap.get(vertex), vertex);
            }
        });

        managedDroolsContext.tick();
    }

    @Override
    public void destroy() {
        if (kieSession != null) {
            kieSession.dispose();
            LOG.info("KieSession disposed.");
        }
    }

    @Override
    public void registerSituationHandler(SituationHandler handler) {
        this.situationHandler = handler;
    }

    public void submitSituation(Situation situation) {
        // Insert/update the fact
        FactHandle fact = situationIdToFactMap.get(situation.getId());
        if (fact != null) {
            kieSession.update(fact, situation);
        } else {
            situationIdToFactMap.put(situation.getId(), kieSession.insert(situation));
        }

        // Notify the handler
        if (situationHandler != null) {
            situationHandler.onSituation(situation);
        } else {
            LOG.warn("Oops.");
        }
    }

    @Override
    public void deleteSituation(String situationId) {

    }

    @Override
    public void handleAlarmFeedback(AlarmFeedback alarmFeedback) {
        kieSession.insert(alarmFeedback);
    }

    public KieSession getKieSession() {
        return kieSession;
    }

    @Override
    public void onAlarmCreatedOrUpdated(Alarm alarm) {
        try {
            initLock.await();
            graphManager.addOrUpdateAlarm(alarm);
            alarmsChangedSinceLastTick = true;
        } catch (InterruptedException ignore) {
            LOG.debug("Interrupted while handling callback, skipping processing onAlarmCreatedOrUpdated.");
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void onAlarmCleared(Alarm alarm) {

    }

    @Override
    public void onInventoryAdded(Collection<InventoryObject> inventoryObjects) {

    }

    @Override
    public void onInventoryRemoved(Collection<InventoryObject> inventoryObjects) {

    }

    /**
     * Retrieve an immutable copy of the situations keyed by id,
     * as currently known by the engine.
     *
     * @return immutable map
     */
    Map<String, Situation> getSituationsById() {
        return Collections.emptyMap();
    }

    @Override
    public long getTickResolutionMs() {
        return tickResolutionMs;
    }

    public void setTickResolutionMs(long tickResolutionMs) {
        this.tickResolutionMs = tickResolutionMs;
    }


    @VisibleForTesting
    Graph<CEVertex, CEEdge> getGraph() {
        return graphManager.getGraph();
    }


    @Override
    public double getSpatialDistanceBetween(long vertexIdA, long vertexIdB) {
        final EdgeKey key = new EdgeKey(vertexIdA, vertexIdB);
        try {
            return spatialDistances.get(key);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private final LoadingCache<EdgeKey, Double> spatialDistances = CacheBuilder.newBuilder()
            .maximumSize(10000)
            .build(new CacheLoader<EdgeKey, Double>() {
                public Double load(EdgeKey key) {
                    /*if (disconnectedVertices.contains(key.vertexIdA) || disconnectedVertices.contains(key.vertexIdB)) {
                        // No path exists
                        return Integer.valueOf(Integer.MAX_VALUE).doubleValue();
                    }*/
                    final CEVertex vertexA = graphManager.getVertexWithId(key.vertexIdA);
                    if (vertexA == null) {
                        throw new IllegalStateException("Could not find vertex with id: " + key.vertexIdA);
                    }
                    final CEVertex vertexB = graphManager.getVertexWithId(key.vertexIdB);
                    if (vertexB == null) {
                        throw new IllegalStateException("Could not find vertex with id: " + key.vertexIdB);
                    }

                    if (shortestPath == null) {
                        shortestPath = new DijkstraShortestPath<>(graphManager.getGraph(), CEEdge::getWeight,true);
                    }

                    Number distance = shortestPath.getDistance(vertexA, vertexB);

                    if (distance == null) {
                        // No path exists
                        return Integer.valueOf(Integer.MAX_VALUE).doubleValue();
                    } else {
                        return distance.doubleValue();
                    }
                }
            });

    private static class EdgeKey {
        private long vertexIdA;
        private long vertexIdB;

        private EdgeKey(long vertexIdA, long vertexIdB) {
            if (vertexIdA <= vertexIdB) {
                this.vertexIdA = vertexIdA;
                this.vertexIdB = vertexIdB;
            } else {
                this.vertexIdA = vertexIdB;
                this.vertexIdB = vertexIdA;
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            EdgeKey edgeKey = (EdgeKey) o;
            return vertexIdA == edgeKey.vertexIdA &&
                    vertexIdB == edgeKey.vertexIdB;
        }

        @Override
        public int hashCode() {
            return Objects.hash(vertexIdA, vertexIdB);
        }
    }

}
