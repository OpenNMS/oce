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

import java.util.Collection;
import java.util.List;

import org.opennms.oce.datasource.api.Situation;
import org.opennms.oce.datasource.common.ImmutableSituation;

public interface DroolsService {

    /**
     * Disassociate the given alarm from the vertex it is attached to and remove
     * facts related to the alarm from working memory.
     *
     * If the alarm was already in a situation, then it should remain part of that situation.
     *
     * @param alarm alarm to gc
     */
    void garbageCollectAlarm(CEAlarm alarm);

    /**
     * Cluster the alarms using the configured cluster settings.
     *
     * @param alarms alarms to cluster
     * @return clustered alarms
     */
    List<CECluster> cluster(Collection<CEAlarm> alarms);

    void mapClusterToNewSituation(List<CEAlarm> alarmsInClusterWithoutSituation, TickContext context);

    void mapClusterToExistingSituations(List<CEAlarm> alarmsInClusterWithoutSituation,
                                        List<CEAlarm> alarmsInClusterWithSituation,
                                        AlarmToSituationMap alarmToSituationMap,
                                        TickContext context);

    /**
     * Create a new situation for the given set of alarms.
     *
     * @param now current timestamp
     * @param alarms set of alarms
     * @return situation builder
     */
    ImmutableSituation.Builder createSituationFor(long now, Collection<CEAlarm> alarms);

    void createSituation(ImmutableSituation.Builder situationBuilder);

    void createOrUpdateSituation(Situation situation);

    void associateAlarmsWithSituation(Collection<CEAlarm> alarms, String situationId);

    void disassociateAlarmFromSituation(String alarmId, String situationId);

    void debug(String message, Object... objects);

    void info(String message, Object... objects);

    void warn(String message, Object... objects);

}
