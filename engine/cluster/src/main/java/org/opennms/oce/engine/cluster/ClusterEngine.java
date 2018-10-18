/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2018 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2018 The OpenNMS Group, Inc.
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.math3.ml.clustering.Cluster;
import org.apache.commons.math3.ml.clustering.DBSCANClusterer;
import org.opennms.oce.datasource.api.Alarm;
import org.opennms.oce.datasource.api.InventoryObject;
import org.opennms.oce.datasource.api.Situation;
import org.opennms.oce.datasource.api.SituationHandler;
import org.opennms.oce.datasource.common.SituationBean;
import org.opennms.oce.engine.api.Engine;
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

import edu.uci.ics.jung.algorithms.shortestpath.DijkstraShortestPath;
import edu.uci.ics.jung.graph.Graph;

/**
 * Clustering based correlation
 * Hypothesis: We can group alarms into situations by an existing clustering algorithm (i.e. DBSCAN)
 * in conjunction with a distance metric that takes in account both space and time (i.e. spatio-temporal clustering).
 *
 * For measuring distance in time, we can use a metric which grows exponentially as time passes,
 * giving distances which are order of magnitudes smaller for events that are close in time.
 *
 * For measuring distances in space between alarms, we can map the alarms onto a network topology graph and
 * use a standard graph metric which measures the distance in the shortest path between the two vertices.
 *
 * Assume a_i and a_k are some alarms we can define:
 *
 *   d(a_i,a_k) = A(e^|a_i_t - a_k_t| - 1) + B(dg(a_i,a_k) ^2)
 *
 * where a_i_t is the time at which a_i was last observed
 * where dg(a_i,a_k) is the distance of the shortest path of the network graph (the sum of the relative weights of all
 * edges, based on edge relationship type, composing the shortest path)
 * where A and B are some constants (need to be tweaked based on how important we want to make space vs time)
 */
public class ClusterEngine implements Engine, GraphProvider {

    private static final Logger LOG = LoggerFactory.getLogger(ClusterEngine.class);

    public static final double DEFAULT_EPSILON = 100d;
    public static final double DEFAULT_ALPHA = 781.78317629d;
    public static final double DEFAULT_BETA = 0.53244801d;

    private static final int NUM_VERTEX_THRESHOLD_FOR_HOP_DIAG = 10;

    /**
     * A special situation id that can be used to key alarms in a map that do not belong to an situation.
     * This value must be non-null, and must never collide with a valid situation id generated by this class.
     */
    private static final String EMPTY_SITUATION_ID = "";

    private final AlarmInSpaceTimeDistanceMeasure distanceMeasure;

    private final Map<String, SituationBean> alarmIdToSituationMap = new HashMap<>();

    private final Map<String, SituationBean> situationsById = new HashMap<>();

    private long tickResolutionMs = TimeUnit.SECONDS.toMillis(30);

    private SituationHandler situationHandler;

    private long lastRun = 0;

    private final double epsilon;

    @VisibleForTesting
    public final long problemTimeoutMs = TimeUnit.HOURS.toMillis(2);

    @VisibleForTesting
    public final long clearTimeoutMs = TimeUnit.MINUTES.toMillis(5);

    private boolean alarmsChangedSinceLastTick = false;
    private DijkstraShortestPath<CEVertex, CEEdge> shortestPath;
    private Set<Long> disconnectedVertices = new HashSet<>();

    private final ManagedCEGraph ceGraph = new ManagedCEGraph();
    private final GraphManager graphManager;
    
    // Used to prevent processing callbacks before the init has completed
    private final CountDownLatch initLock = new CountDownLatch(1);

    public ClusterEngine() {
        this(DEFAULT_EPSILON, DEFAULT_ALPHA, DEFAULT_BETA, null);
    }

    public ClusterEngine(double epsilon, double alpha, double beta, GraphManager graphManager) {
        this.epsilon = epsilon;
        distanceMeasure = new AlarmInSpaceTimeDistanceMeasure(this, alpha, beta);

        if (graphManager != null) {
            this.graphManager = graphManager;
            this.graphManager.addGraph(ceGraph);
        } else {
            this.graphManager = GraphManager.newGraphManagerWithGraphs(Collections.singleton(ceGraph));
        }
    }

    @Override
    public void registerSituationHandler(SituationHandler handler) {
        this.situationHandler = handler;
    }


    @Override
    public long getTickResolutionMs() {
        return tickResolutionMs;
    }


    public void setTickResolutionMs(long tickResolutionMs) {
        this.tickResolutionMs = tickResolutionMs;
    }


    @Override
    public void tick(long timestampInMillis) {
        LOG.debug("Starting tick for {}", timestampInMillis);
        if (timestampInMillis - lastRun >= tickResolutionMs - 1) {
            onTick(timestampInMillis);
            lastRun = timestampInMillis;
        } else {
            LOG.debug("Less than {} milliseconds elapsed since last tick. Ignoring.", tickResolutionMs);
        }
        LOG.debug("Done tick for {}", timestampInMillis);
    }

    @Override
    public void init(List<Alarm> alarms, List<Situation> situations, List<InventoryObject> inventory) {
        try {
            LOG.debug("Initialized with {} alarms, {} situations and {} inventory objects.", alarms.size(), situations.size(), inventory.size());
            LOG.trace("Alarms on init: {}", alarms);
            LOG.trace("Situations on init: {}", situations);
            LOG.trace("Inventory objects on init: {}", inventory);
            graphManager.addInventory(inventory);
            graphManager.addOrUpdateAlarms(alarms);

            // Index the given situations and the alarms they contain, so that we can cluster alarms in existing
            // situations when applicable
            for (Situation situation : situations) {
                final SituationBean situationBean = new SituationBean(situation);
                situationsById.put(situationBean.getId(), situationBean);
                for (Alarm alarmInSituation : situationBean.getAlarms()) {
                    alarmIdToSituationMap.put(alarmInSituation.getId(), situationBean);
                }
            }

            if (alarms.size() > 0) {
                alarmsChangedSinceLastTick = true;
            }
        } finally {
            initLock.countDown();
        }
    }

    @Override
    public void destroy() {
        graphManager.close();
    }

    @Override
    public synchronized void deleteSituation(String situationId) throws InterruptedException {
        // Make sure the engine has init'd before we attempt to delete anything since situations can be provided on init
        initLock.await();

        LOG.trace("Deleting situation references for situation Id {}", situationId);
        Situation situationBeingRemoved = situationsById.remove(situationId);

        if (situationBeingRemoved == null) {
            LOG.warn("Situation Id {} was not found when attempting to delete", situationId);

            return;
        }

        situationBeingRemoved.getAlarms().stream()
                .map(Alarm::getId)
                .forEach(alarmIdToSituationMap::remove);
    }

    public synchronized void onTick(long timestampInMillis) {
        if (!alarmsChangedSinceLastTick) {
            LOG.debug("{}: No alarm changes since last tick. Nothing to do.", timestampInMillis);
            return;
        }
        // Reset
        alarmsChangedSinceLastTick = false;

        // Perform the clustering with the graph locked
        final Set<SituationBean> situations = new HashSet<>();
        ceGraph.withGraph(g -> {
            if (ceGraph.getDidGraphChangeAndReset()) {
                // If the graph has changed, then reset the cache
                LOG.debug("{}: Graph has changed. Resetting hop cache.", timestampInMillis);
                spatialDistances.invalidateAll();
                shortestPath = null;
                disconnectedVertices = ceGraph.getDisconnectedVertices();
            }

            graphManager.garbageCollectAlarms(timestampInMillis, problemTimeoutMs, clearTimeoutMs);

            // Ensure the points are sorted in order to make sure that the output of the clusterer is deterministic
            // OPTIMIZATION: Can we avoid doing this every tick?
            final List<AlarmInSpaceTime> alarms = g.getVertices().stream()
                    .map(v -> v.getAlarms().stream()
                            .map(a -> new AlarmInSpaceTime(v,a))
                            .collect(Collectors.toList()))
                    .flatMap(Collection::stream)
                    .sorted(Comparator.comparing(AlarmInSpaceTime::getAlarmTime).thenComparing(AlarmInSpaceTime::getAlarmId))
                    .collect(Collectors.toList());
            if (alarms.isEmpty()) {
                LOG.debug("{}: The graph contains no alarms. No clustering will be performed.", timestampInMillis);
                return;
            }

            LOG.debug("{}: Clustering {} alarms.", timestampInMillis, alarms.size());
            final DBSCANClusterer<AlarmInSpaceTime> clusterer = new DBSCANClusterer<>(epsilon, 1, distanceMeasure);
            final List<Cluster<AlarmInSpaceTime>> clustersOfAlarms = clusterer.cluster(alarms);
            LOG.debug("{}: Found {} clusters of alarms.", timestampInMillis, clustersOfAlarms.size());
            for (Cluster<AlarmInSpaceTime> clusterOfAlarms : clustersOfAlarms) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("{}: Processing cluster containing {} alarms.", timestampInMillis, clusterOfAlarms.getPoints().size());
                }
                situations.addAll(mapClusterToSituations(clusterOfAlarms, alarmIdToSituationMap, situationsById));
            }
        });

        // Index and notify the situation handler
        LOG.debug("{}: Creating/updating {} situations.", timestampInMillis, situations.size());
        for (SituationBean situation : situations) {
            for (Alarm alarm : situation.getAlarms()) {
                alarmIdToSituationMap.put(alarm.getId(), situation);
            }
            situationsById.put(situation.getId(), situation);
            situationHandler.onSituation(situation);
        }
    }

    /**
     * Maps the clusters to situations and returns a set of updated situation
     * which should be forwarded to the situation handler.
     *
     * @param clusterOfAlarms clusters to group into situations
     * @param alarmIdToSituationMap map of existing alarm ids to situation ids
     * @param situationsById map of existing situations ids to situations
     * @return set of updated situations
     */
    @VisibleForTesting
    protected Set<SituationBean> mapClusterToSituations(Cluster<AlarmInSpaceTime> clusterOfAlarms,
            Map<String, SituationBean> alarmIdToSituationMap, Map<String, SituationBean> situationsById) {
        // Map the alarms by existing situation id, using the empty situation id id if they are not associated with an situation
        final Map<String, List<Alarm>> alarmsBySituationId = clusterOfAlarms.getPoints().stream()
                .map(AlarmInSpaceTime::getAlarm)
                .collect(Collectors.groupingBy(a -> {
                final Situation situation = alarmIdToSituationMap.get(a.getId());
                if (situation != null) {
                    return situation.getId();
                    }
                return EMPTY_SITUATION_ID;
                }));

        final Set<SituationBean> situations = new LinkedHashSet<>();
        final boolean existsAlarmWithoutSituation = alarmsBySituationId.containsKey(EMPTY_SITUATION_ID);
        if (!existsAlarmWithoutSituation) {
            // All of the alarms are already in situations, nothing to do here
            return situations;
        }

        if (alarmsBySituationId.size() == 1) {
            // All of the alarms in the cluster are not associated with an situation yet
            // Create a new situation with all of the alarms
            final SituationBean situation = new SituationBean();
            situation.setId(UUID.randomUUID().toString());
            for (AlarmInSpaceTime alarm : clusterOfAlarms.getPoints()) {
                situation.addAlarm(alarm.getAlarm());

            }
            situations.add(situation);
        } else if (alarmsBySituationId.size() == 2) {
            // Some of the alarms in the cluster already belong to an situation whereas other don't
            // Add them all to the same situation
            final String situationId = alarmsBySituationId.keySet().stream().filter(k -> !EMPTY_SITUATION_ID.equals(k))
                    .findFirst().orElseThrow(() -> new IllegalStateException("Should not happen."));
            final SituationBean situation = situationsById.get(situationId);
            if (situation == null) {
                throw new IllegalStateException("Should not happen.");
            }

            alarmsBySituationId.get(EMPTY_SITUATION_ID).forEach(situation::addAlarm);
            situations.add(situation);
        } else {
            // The alarms in this cluster already belong to different situations
            // Let's locate the ones that aren't part of any situation
            final List<Alarm> alarmsWithoutSituations = alarmsBySituationId.get(EMPTY_SITUATION_ID);
            if (LOG.isTraceEnabled()) {
                LOG.trace("Found {} unclassified alarms in cluster where alarms are associated with {} situations.",
                          alarmsWithoutSituations.size(), alarmsBySituationId.size());
            }

            final List<Alarm> candidateAlarms = alarmsBySituationId.entrySet().stream()
                .filter(e -> !EMPTY_SITUATION_ID.equals(e.getKey()))
                    .flatMap(e -> e.getValue().stream())
                    .collect(Collectors.toList());

            // For each of these we want to associate the alarm with the other alarm that is the "closest"
            for (Alarm alarm : alarmsWithoutSituations) {
                final Alarm closestNeighbor = getClosestNeighborInSituation(alarm, candidateAlarms);
                final SituationBean situation = alarmIdToSituationMap.get(closestNeighbor.getId());
                if (situation == null) {
                    throw new IllegalStateException("Should not happen.");
                }
                situation.addAlarm(alarm);
                situations.add(situation);
            }
        }

        LOG.debug("Generating diagnostic texts for {} situations...", situations.size());
        for (SituationBean situation : situations) {
            situation.setDiagnosticText(getDiagnosticTextForSituation(situation));
        }
        LOG.debug("Done generating diagnostic texts.");

        return situations;
    }

    private String getDiagnosticTextForSituation(SituationBean situation) {
        long minTime = Long.MAX_VALUE;
        long maxTime = Long.MIN_VALUE;
        Long maxSpatialDistance = null;

        final Set<Long> vertexIds = new HashSet<>();
        for (Alarm alarm : situation.getAlarms()) {
            minTime = Math.min(minTime, alarm.getTime());
            maxTime = Math.max(maxTime, alarm.getTime());
            // The alarm may no longer be in this graph
            getOptionalVertexIdForAlarm(alarm).ifPresent(vertexIds::add);
        }

        if (vertexIds.size() < NUM_VERTEX_THRESHOLD_FOR_HOP_DIAG) {
            maxSpatialDistance = 0L;
            for (Long vertexIdA : vertexIds) {
                for (Long vertexIdB : vertexIds) {
                    if (!vertexIdA.equals(vertexIdB)) {
                        maxSpatialDistance = Math.max(maxSpatialDistance, getSpatialDistanceBetween(vertexIdA,
                                vertexIdB));
                    }
                }
            }
        }

        String diagText = String.format("The %d alarms happened within %.2f seconds across %d vertices",
                situation.getAlarms().size(), Math.abs(maxTime - minTime) / 1000d, vertexIds.size());
        if (maxSpatialDistance != null && maxSpatialDistance > 0) {
            diagText += String.format(" %d distance apart", maxSpatialDistance);
        }
        diagText += ".";
        return diagText;
    }

    @Override
    public void onAlarmCreatedOrUpdated(Alarm alarm) {
        try {
            initLock.await();
            graphManager.addOrUpdateAlarm(alarm);
            alarmsChangedSinceLastTick = true;
        } catch (InterruptedException ignore) {
            LOG.debug("Interrupted while handling callback, skipping processing onAlarmCreatedOrUpdated");
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void onAlarmCleared(Alarm alarm) {
        try {
            initLock.await();
            graphManager.addOrUpdateAlarm(alarm);
            alarmsChangedSinceLastTick = true;
        } catch (InterruptedException ignore) {
            LOG.debug("Interrupted while handling callback, skipping processing onAlarmCleared");
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void onInventoryAdded(Collection<InventoryObject> inventory) {
        try {
            initLock.await();
            graphManager.addInventory(inventory);
        } catch (InterruptedException ignore) {
            LOG.debug("Interrupted while handling callback, skipping processing onInventoryAdded");
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void onInventoryRemoved(Collection<InventoryObject> inventory) {
        try {
            initLock.await();
            graphManager.removeInventory(inventory);
        } catch (InterruptedException ignore) {
            LOG.debug("Interrupted while handling callback, skipping processing onInventoryRemoved");
            Thread.currentThread().interrupt();
        }
    }


    @Override
    public <V> V withReadOnlyGraph(Function<OceGraph, V> consumer) {
        final List<Situation> situations = new ArrayList<>(situationsById.values());
        return ceGraph.withReadOnlyGraph(g -> {
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
                    return ceGraph.getVertexWithId(idAsLong);
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

    private static class CandidateAlarmWithDistance {

        private final Alarm alarm;
        private final double distance;

        private CandidateAlarmWithDistance(Alarm alarm, double distance) {
            this.alarm = alarm;
            this.distance = distance;
        }

        public Alarm getAlarm() {
            return alarm;
        }

        public double getDistance() {
            return distance;
        }
    }

    private Optional<Long> getOptionalVertexIdForAlarm(Alarm alarm) {
        return ceGraph.withGraph(g -> {
            for (CEVertex v : ceGraph.getGraph().getVertices()) {
                final Optional<Alarm> match = v.getAlarms().stream()
                        .filter(a -> a.equals(alarm))
                        .findFirst();
                if (match.isPresent()) {
                    return Optional.of(v.getNumericId());
                }
            }
            return Optional.empty();
        });
    }

    private long getVertexIdForAlarm(Alarm alarm) {
        final Optional<Long> vertexId = getOptionalVertexIdForAlarm(alarm);
        if (vertexId.isPresent()) {
            return vertexId.get();
        }
        throw new IllegalStateException("No vertex found for alarm: " + alarm);
    }

    private Alarm getClosestNeighborInSituation(Alarm alarm, List<Alarm> candidates) {
        final double timeA = alarm.getTime();
        final long vertexIdA = getVertexIdForAlarm(alarm);

        return candidates.stream()
                .map(candidate -> {
                    final double timeB = candidate.getTime();
                    final long vertexIdB = getVertexIdForAlarm(candidate);
                    final int spatialDistance = vertexIdA == vertexIdB ? 0 : getSpatialDistanceBetween(vertexIdA,
                            vertexIdB);
                    final double distance = distanceMeasure.compute(timeA, timeB, spatialDistance);
                    return new CandidateAlarmWithDistance(candidate, distance);
                })
                .min(Comparator.comparingDouble(CandidateAlarmWithDistance::getDistance)
                        .thenComparing(c -> c.getAlarm().getId()))
                .orElseThrow(() -> new IllegalStateException("Should not happen!")).alarm;
    }

    protected int getSpatialDistanceBetween(long vertexIdA, long vertexIdB) {
        final EdgeKey key = new EdgeKey(vertexIdA, vertexIdB);
        try {
            return spatialDistances.get(key);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private final LoadingCache<EdgeKey, Integer> spatialDistances = CacheBuilder.newBuilder()
            .maximumSize(10000)
            .build(new CacheLoader<EdgeKey, Integer>() {
                        public Integer load(EdgeKey key) {
                            if (disconnectedVertices.contains(key.vertexIdA) || disconnectedVertices.contains(key.vertexIdB)) {
                                return 0;
                            }
                            final CEVertex vertexA = ceGraph.getVertexWithId(key.vertexIdA);
                            if (vertexA == null) {
                                throw new IllegalStateException("Could not find vertex with id: " + key.vertexIdA);
                            }
                            final CEVertex vertexB = ceGraph.getVertexWithId(key.vertexIdB);
                            if (vertexB == null) {
                                throw new IllegalStateException("Could not find vertex with id: " + key.vertexIdB);
                            }

                            if (shortestPath == null) {
                                shortestPath = new DijkstraShortestPath<>(ceGraph.getGraph(), CEEdge::getWeight,true);
                            }

                            Number distance = shortestPath.getDistance(vertexA, vertexB);

                            if (distance == null) {
                                // No path exists
                                return Integer.MAX_VALUE;
                            } else {
                                return distance.intValue();
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

    @VisibleForTesting
    Graph<CEVertex, CEEdge> getGraph() {
        return ceGraph.getGraph();
    }
    
    @VisibleForTesting
    public GraphManager getGraphManager() {
        return graphManager;
    }

    @VisibleForTesting
    Map<String, SituationBean> getSituationsById() {
        return situationsById;
    }
}
