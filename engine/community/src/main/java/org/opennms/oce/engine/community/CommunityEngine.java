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

import org.opennms.oce.engine.api.Engine;
import org.opennms.oce.engine.api.IncidentHandler;
import org.opennms.oce.model.alarm.api.Alarm;
import org.opennms.oce.model.api.Model;

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

    @Override
    public void onAlarm(Alarm alarm) {

    }

    @Override
    public void setInventory(Model inventory) {

    }

    @Override
    public void registerIncidentHandler(IncidentHandler handler) {

    }
}
