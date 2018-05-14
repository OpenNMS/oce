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

package org.opennms.oce.engine.community;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.opennms.oce.engine.api.Engine;
import org.opennms.oce.engine.api.IncidentHandler;
import org.opennms.oce.model.alarm.api.Alarm;
import org.opennms.oce.model.alarm.api.ResourceKey;
import org.opennms.oce.model.api.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.SparseMultigraph;
import edu.uci.ics.jung.graph.util.EdgeType;

/**
 * Community detection based correlation
 *
 * Hypothesis: We can group alarms into incidents by applying community detection
 * algorithms against the network topology graph while using time to weigh the edges.
 *
 * Assume we have all of the alarms mapped on a network topology graph G, where the alarms
 * themselves are directly connected to the affected elements. Let's define a subset of the
 * graph G' that contains A) all of the vertices which have 1+ associated alarms and B) the
 * vertices on the shortest paths between these.
 *
 * In order to incorporate the notion of time, we can weigh the edges according the difference
 * in time between A) the time at which an alarm was last observed and B) the mean time of all
 * the other connected alarms scaled by some notion of distance.
 *
 * Example implementation we can leverage:
 *   http://igraph.org/c/doc/igraph-Community.html#idm470942725872
 *
 */
public class CommunityEngine implements Engine {
    private static final Logger LOG = LoggerFactory.getLogger(CommunityEngine.class);

    private final Graph<Vertex, Edge> g = new SparseMultigraph<>();
    private boolean alarmsChangedSinceLastTick = false;
    private final AtomicLong vertexIdGenerator = new AtomicLong();
    private final Map<Long, Vertex> idtoVertexMap = new HashMap<>();
    private final Map<ResourceKey, Vertex> resourceToVertexMap = new HashMap<>();

    @Override
    public void onAlarm(Alarm alarm) {
        // a1 has key [a,b,c,d]
        // a2 has key [a,b,c,d]
        // a3 has key [a,b,c,z]

        // (a) -- (a,b) -- (a,b,c) -- (a,b,c,d) -- a1
        //
        // (a) -- (a,b) -- (a,b,c) -- (a,b,c,d) -- a1
        //                                      -- a2
        //
        // (a) -- (a,b) -- (a,b,c) -- (a,b,c,d) -- a1
        //                     |                -- a2
        //                     -   -- (a,b,c,z) -- a3

        // (a,b,c,d) -- a1 (t=1)       weight = 100 - variance of times on associated alarm vertices
        //           -- a2 (t=2)
        //  weight = function(neighbors and time) - TBD
        getVertexForResource(alarm.getResourceKey());
        alarmsChangedSinceLastTick = true;
    }

    @Override
    public void setInventory(Model inventory) {

    }

    @Override
    public void registerIncidentHandler(IncidentHandler handler) {

    }

    @Override
    public long getTickResolutionMs() {
        return 30 * 1000;
    }

    @Override
    public void tick(long timestampInMillis) {
        if (!alarmsChangedSinceLastTick) {
            return;
        } else {
            // Reset
            alarmsChangedSinceLastTick = false;
        }

        //Getting alarms which are verticis
        final List<CDAlarm> alarms = g.getVertices().stream()
                .filter(e -> e.getType() == VertexType.ALARM)
                .map(a -> new CDAlarm())
                .collect(Collectors.toList());

        //TODO Removing JNI interface for a while
    }

    private Vertex getVertexForResource(ResourceKey resourceKey) {
        Vertex vertex = resourceToVertexMap.get(resourceKey);
        if (vertex != null) {
            // it already exists
            return vertex;
        } else {
            vertex = new Vertex(vertexIdGenerator.getAndIncrement(), resourceKey);
            // Index it
            resourceToVertexMap.put(vertex.getResourceKey(), vertex);
            idtoVertexMap.put(vertex.getId(), vertex);
            // Add it to the graph
            g.addVertex(vertex);
        }

        if (resourceKey.length() <= 1) {
            // This is a root element, no edges to add
            return vertex;
        } else {
            // Retrieve the parent and link the two vertices
            final Vertex parent = getVertexForResource(resourceKey.getParentKey());
            g.addEdge(new Edge(), parent, vertex, EdgeType.UNDIRECTED);
        }

        return vertex;
    }

    @VisibleForTesting
    Graph<Vertex, Edge> getGraph() {
        return g;
    }
}
