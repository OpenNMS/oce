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
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.drools.core.time.SessionPseudoClock;
import org.opennms.oce.datasource.api.Alarm;
import org.opennms.oce.datasource.api.AlarmFeedback;
import org.opennms.oce.datasource.api.InventoryObject;
import org.opennms.oce.datasource.api.Situation;
import org.opennms.oce.datasource.api.SituationHandler;
import org.opennms.oce.engine.api.Engine;
import org.opennms.oce.engine.cluster.drools.DroolsFactManager;
import org.opennms.oce.engine.cluster.drools.DroolsService;
import org.opennms.oce.engine.cluster.drools.DroolsServiceImpl;
import org.opennms.oce.features.drools.ManagedDroolsContext;
import org.opennms.oce.features.graph.api.Edge;
import org.opennms.oce.features.graph.api.GraphProvider;
import org.opennms.oce.features.graph.api.OceGraph;
import org.opennms.oce.features.graph.api.Vertex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.common.io.MoreFiles;
import com.google.common.io.Resources;

import edu.uci.ics.jung.algorithms.shortestpath.DijkstraShortestPath;
import edu.uci.ics.jung.graph.Graph;

public class ClusterEngine implements Engine, GraphProvider, SpatialDistanceCalculator {
    private static Logger LOG = LoggerFactory.getLogger(ClusterEngine.class);

    public static final double DEFAULT_EPSILON = 100d;
    public static final double DEFAULT_ALPHA = 144.47117699d;
    public static final double DEFAULT_BETA = 0.55257784d;

    private final double epsilon;

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

    private final Path pathToRulesFolder;
    private final boolean cleanupRulesFolderOnDestroy;

    public ClusterEngine() {
        this(DEFAULT_EPSILON, DEFAULT_ALPHA, DEFAULT_BETA, null);
    }

    public ClusterEngine(double epsilon, double alpha, double beta, String rulesFolder)  {
        if (rulesFolder != null) {
            // Use the given path
            pathToRulesFolder = Paths.get(rulesFolder);
            cleanupRulesFolderOnDestroy = false;
        } else {
            // Copy the rules from the class-path to a temporary directory
            try {
                pathToRulesFolder = Files.createTempDirectory("oce-drools");
                pathToRulesFolder.toFile().deleteOnExit();
                URL url = Resources.getResource(ClusterEngine.class,"cluster.drl");
                try (InputStream is = url.openStream()) {
                    Files.copy(is, pathToRulesFolder.resolve("cluster.drl"));
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            cleanupRulesFolderOnDestroy = true;
        }

        managedDroolsContext = new ManagedDroolsContext(
                pathToRulesFolder.toFile(),
                "clusterEngineKB",
                "clusterEngineSession");
        managedDroolsContext.setUseManualTick(true);
        managedDroolsContext.setUsePseudoClock(true);
        managedDroolsContext.setOnNewKiewSessionCallback(kieSession -> {
            droolsFactManager = new DroolsFactManager(kieSession);
            AlarmInSpaceTimeDistanceMeasure distanceMeasure = new AlarmInSpaceTimeDistanceMeasure(this, alpha, beta);
            droolsService = new DroolsServiceImpl(this, droolsFactManager, distanceMeasure);
            kieSession.setGlobal("svc", droolsService);
        });
        this.epsilon = epsilon;
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
                droolsFactManager.upsertSituation(toCESituation(situation));
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
        final long deltaInMs = timestampInMillis - pseudoClock.getCurrentTime();
        if (deltaInMs < 0) {
            throw new IllegalStateException("Time went backwards! Delta in ms: " + deltaInMs);
        }
        pseudoClock.advanceTime(deltaInMs, TimeUnit.MILLISECONDS);

        managedDroolsContext.tick();
        /*
        for (String agendaGroup : Arrays.asList("pre-correlation", "correlation", "post-correlation")) {
            managedDroolsContext.getKieSession().getAgenda().getAgendaGroup(agendaGroup).setFocus();
            managedDroolsContext.tick();
        }
        */
    }

    @Override
    public void destroy() {
        if (managedDroolsContext != null) {
            managedDroolsContext.stop();
        }

        if (cleanupRulesFolderOnDestroy) {
            try {
                MoreFiles.deleteRecursively(pathToRulesFolder);
            } catch (IOException e) {
                LOG.warn("Error occurred while cleaning up temporary folder '{}'.", pathToRulesFolder, e);
            }
        }
    }

    @Override
    public void registerSituationHandler(SituationHandler handler) {
        this.situationHandler = handler;
    }

    public void submitSituation(Situation situation) {
        droolsFactManager.upsertSituation(toCESituation(situation));

        // Notify the handler
        if (situationHandler != null) {
            situationHandler.onSituation(situation);
        } else {
            LOG.warn("No situation handler is currently registered. Situation will not be forwarded: {}", situation);
        }
    }

    private CESituation toCESituation(Situation situation) {
        final Set<String> alarmIdsInSituation = situation.getAlarmIds();
        final List<CEVertex> verticesInSituation = new LinkedList<>();

        // FIXME: Should be more efficient - we shouldn't have to iterate over the entire graph
        graphManager.withGraph(g -> {
            for (CEVertex v : g.getVertices()) {
                final Set<String> alarmIdsOnVertex = v.getAlarms().stream().map(Alarm::getId).collect(Collectors.toSet());
                if (!Sets.intersection(alarmIdsInSituation, alarmIdsOnVertex).isEmpty()) {
                    verticesInSituation.add(v);
                }
            }
        });

        return new CESituation(situation, verticesInSituation);
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

    @Override
    public <V> V withReadOnlyGraph(Function<OceGraph, V> consumer) {
        final List<Situation> situations = new ArrayList<>(situationsById.values());
        return graphManager.withReadOnlyGraph(g -> {
            final OceGraph oceGraph = new OceGraph() {
                @Override
                public Graph<? extends Vertex, ? extends Edge> getGraph() {
                    return g;
                }

                @Override
                public List<Situation> getSituations() {
                    return situations;
                }

                @Override
                public Vertex getVertexById(String id) {
                    final Long idAsLong;
                    if (id == null) {
                        idAsLong = null;
                    } else {
                        try {
                            idAsLong = Long.valueOf(id);
                        } catch (NumberFormatException nfe) {
                            return null;
                        }
                    }
                    return graphManager.getVertexWithId(idAsLong);
                }
            };
            return consumer.apply(oceGraph);
        });
    }

    @Override
    public void withReadOnlyGraph(Consumer<OceGraph> consumer) {
        withReadOnlyGraph(g -> {
            consumer.accept(g);
            return null;
        });
    }

    public double getEpsilon() {
        return epsilon;
    }

    public GraphManager getGraphManager() {
        return graphManager;
    }

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
