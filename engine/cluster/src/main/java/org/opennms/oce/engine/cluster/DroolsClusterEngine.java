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
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.drools.core.time.SessionPseudoClock;
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
import com.google.common.collect.ImmutableMap;

import edu.uci.ics.jung.algorithms.shortestpath.DijkstraShortestPath;
import edu.uci.ics.jung.graph.Graph;

public class DroolsClusterEngine implements Engine, SpatialDistanceCalculator {
    private static Logger LOG = LoggerFactory.getLogger(DroolsClusterEngine.class);
    private DijkstraShortestPath<CEVertex, CEEdge> shortestPath;

    private final ManagedDroolsContext managedDroolsContext;
    private SessionPseudoClock pseudoClock;

    private final GraphManager graphManager = new GraphManager();
    private final Map<String, Situation> situationsById = new HashMap<>();

    private long tickResolutionMs = TimeUnit.SECONDS.toMillis(30);

    // Used to prevent processing callbacks before the init has completed
    private final CountDownLatch initLock = new CountDownLatch(1);

    private SituationHandler situationHandler;

    private DroolsFactManager droolsFactManager;
    private DroolsService droolsService;

    public DroolsClusterEngine() {
        managedDroolsContext = new ManagedDroolsContext(
                new File("/home/jesse/git/oce/engine/cluster/src/main/resources/org/opennms/oce/engine/cluster"),
                "clusterEngineKB",
                "clusterEngineSession");
        managedDroolsContext.setUseManualTick(true);
        managedDroolsContext.setUsePseudoClock(true);
        managedDroolsContext.setOnNewKiewSessionCallback(kieSession -> {
            droolsFactManager = new DroolsFactManager(kieSession);
            AlarmInSpaceTimeDistanceMeasure distanceMeasure = new AlarmInSpaceTimeDistanceMeasure(this, ClusterEngine.DEFAULT_ALPHA, ClusterEngine.DEFAULT_BETA);
            droolsService = new DroolsServiceImpl(this, droolsFactManager, distanceMeasure);
            kieSession.setGlobal("svc", droolsService);
        });
    }

    @Override
    public void init(List<Alarm> alarms, List<AlarmFeedback> alarmFeedback, List<Situation> situations, List<InventoryObject> inventory) {
        try {
            managedDroolsContext.start();
            pseudoClock = managedDroolsContext.getClock();

            LOG.debug("Initialized with {} alarms, {} alarm feedback, {} situations and {} inventory objects.",
                    alarms.size(), alarmFeedback.size(), situations.size(), inventory.size());
            LOG.trace("Alarms on init: {}", alarms);
            LOG.trace("Situations on init: {}", situations);
            LOG.trace("Inventory objects on init: {}", inventory);
            graphManager.addInventory(inventory);
            graphManager.addOrUpdateAlarms(alarms);

            // Add the vertices to the graph
            graphManager.withGraph(g -> {
                for (CEVertex v : g.getVertices()) {
                    droolsFactManager.upsertVertex(v);
                }
            });

            // Index the given situations and the alarms they contain, so that we can cluster alarms in existing
            // situations when applicable
            situations.forEach(situation -> {
                situationsById.put(situation.getId(), situation);
                droolsFactManager.upsertSituation(situation);
            });

            // Process all the alarm feedback provided on init
            alarmFeedback.forEach(this::handleAlarmFeedback);
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
        managedDroolsContext.tick();
    }

    @Override
    public void destroy() {
        if (managedDroolsContext != null) {
            managedDroolsContext.stop();
        }
    }

    @Override
    public void registerSituationHandler(SituationHandler handler) {
        this.situationHandler = handler;
    }

    public void submitSituation(Situation situation) {
        droolsFactManager.upsertSituation(situation);

        // Notify the handler
        if (situationHandler != null) {
            situationHandler.onSituation(situation);
        } else {
            LOG.warn("Oops.");
        }
    }

    @Override
    public void deleteSituation(String situationId) {
        droolsFactManager.deleteSituation(situationId);
    }

    @Override
    public void handleAlarmFeedback(AlarmFeedback alarmFeedback) {
        droolsFactManager.insertFeedback(alarmFeedback);
    }

    @Override
    public void onAlarmCreatedOrUpdated(Alarm alarm) {
        try {
            initLock.await();
            final CEVertex vertex = graphManager.addOrUpdateAlarm(alarm);
            if (vertex != null) {
                droolsFactManager.upsertVertex(vertex);
            }
        } catch (InterruptedException ignore) {
            LOG.debug("Interrupted while handling callback, skipping processing onAlarmCreatedOrUpdated.");
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void onAlarmCleared(Alarm alarm) {
        try {
            initLock.await();
            final CEVertex vertex = graphManager.addOrUpdateAlarm(alarm);
            if (vertex != null) {
                droolsFactManager.upsertVertex(vertex);
            }
        } catch (InterruptedException ignore) {
            LOG.debug("Interrupted while handling callback, skipping processing onAlarmCleared.");
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void onInventoryAdded(Collection<InventoryObject> inventory) {
        try {
            initLock.await();
            graphManager.addInventory(inventory);
        } catch (InterruptedException ignore) {
            LOG.debug("Interrupted while handling callback, skipping processing onInventoryAdded.");
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void onInventoryRemoved(Collection<InventoryObject> inventory) {
        try {
            initLock.await();
            graphManager.removeInventory(inventory);
        } catch (InterruptedException ignore) {
            LOG.debug("Interrupted while handling callback, skipping processing onInventoryRemoved.");
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Retrieve an immutable copy of the situations keyed by id,
     * as currently known by the engine.
     *
     * @return immutable map
     */
    Map<String, Situation> getSituationsById() {
        return ImmutableMap.copyOf(situationsById);
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
