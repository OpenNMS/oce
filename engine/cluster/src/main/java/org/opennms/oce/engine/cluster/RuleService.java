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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.math3.ml.clustering.Cluster;
import org.apache.commons.math3.ml.clustering.DBSCANClusterer;
import org.opennms.oce.datasource.api.Situation;
import org.opennms.oce.datasource.common.ImmutableSituation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RuleService {
    private static final Logger LOG = LoggerFactory.getLogger(RuleService.class);

    private final DroolsClusterEngine engine;
    private final AlarmInSpaceTimeDistanceMeasure distanceMeasure;

    public RuleService(DroolsClusterEngine engine, AlarmInSpaceTimeDistanceMeasure distanceMeasure) {
        this.engine = Objects.requireNonNull(engine);
        this.distanceMeasure = Objects.requireNonNull(distanceMeasure);
    }

    public List<CECluster> cluster(Collection<CEAlarm> alarms) {
        // Ensure the points are sorted in order to make sure that the output of the clusterer is deterministic
        // OPTIMIZATION: Can we avoid doing this every tick?
        final List<AlarmInSpaceTime> alarmsInSpaceTime = alarms.stream()
                .map(a -> new AlarmInSpaceTime(a.getVertex(), a.getAlarm()))
                .sorted(Comparator.comparing(AlarmInSpaceTime::getAlarmTime).thenComparing(AlarmInSpaceTime::getAlarmId))
                .collect(Collectors.toList());
        if (alarms.size() < 1) {
            LOG.debug("The graph contains no alarms. No clustering will be performed.");
            return Collections.emptyList();
        }

        LOG.debug("Clustering {} alarms.", alarms.size());
        final DBSCANClusterer<AlarmInSpaceTime> clusterer = new DBSCANClusterer<>(ClusterEngine.DEFAULT_EPSILON, 1, distanceMeasure);
        final List<Cluster<AlarmInSpaceTime>> clustersOfAlarms = clusterer.cluster(alarmsInSpaceTime);
        LOG.debug("Found {} clusters of alarms.", clustersOfAlarms.size());


        final List<CECluster> clusters = new ArrayList<>(clustersOfAlarms.size());
        final Map<String, CEAlarm> alarmsById = alarms.stream()
                .collect(Collectors.toMap(CEAlarm::getId, a -> a));
        for (Cluster<AlarmInSpaceTime> cluster : clustersOfAlarms) {
            final List<CEAlarm> alarmsInCluster = cluster.getPoints().stream()
                    .map(a -> alarmsById.get(a.getAlarmId()))
                    .collect(Collectors.toList());
            clusters.add(new CECluster(alarmsInCluster));
        }

        return clusters;
    }

    public ImmutableSituation.Builder createSituationFor(long now, Collection<CEAlarm> alarms) {
        final String situationId = UUID.randomUUID().toString();
        final ImmutableSituation.Builder situationBuilder = ImmutableSituation.newBuilder()
                .setId(situationId)
                .setCreationTime(now);
        for (CEAlarm alarm : alarms) {
            situationBuilder.addAlarm(alarm.getAlarm());
        }
        return situationBuilder;
    }

    public void debug(String message, Object... objects) {
        LOG.debug(message, objects);
    }

    public void info(String message, Object... objects) {
        LOG.info(message, objects);
    }

    public void warn(String message, Object... objects) {
        LOG.warn(message, objects);
    }

    public void createSituation(ImmutableSituation.Builder situationBuilder) {
        final Situation situation = situationBuilder.build();
        engine.submitSituation(situation);
    }
}
