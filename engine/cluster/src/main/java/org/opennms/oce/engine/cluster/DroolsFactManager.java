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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.FactHandle;
import org.opennms.oce.datasource.api.Alarm;
import org.opennms.oce.datasource.api.AlarmFeedback;
import org.opennms.oce.datasource.api.Situation;

public class DroolsFactManager {

    private final KieSession kieSession;

    private final Map<CEVertex, FactHandle> vertexToFactMap = new HashMap<>();
    private final Map<String, FactHandle> situationIdToFactMap = new HashMap<>();


    private final Map<String, FactHandle> alarmIdToFactMap = new HashMap<>();
    private final AlarmToSituationMap alarmToSituationMap = new AlarmToSituationMap();
    private final FactHandle alarmToSituationMapHandle;

    public DroolsFactManager(KieSession kieSession) {
        this.kieSession = Objects.requireNonNull(kieSession);
        // Insert an empty alarm <-> situation map
        alarmToSituationMapHandle = kieSession.insert(alarmToSituationMap);
    }

    public void upsertSituation(Situation situation) {
        // Insert/update the fact
        final FactHandle fact = situationIdToFactMap.get(situation.getId());
        if (fact != null) {
            kieSession.update(fact, situation);
        } else {
            situationIdToFactMap.put(situation.getId(), kieSession.insert(situation));
        }
    }

    public void deleteSituation(String situationId) {
        final FactHandle fact = situationIdToFactMap.remove(situationId);
        if (fact != null) {
            kieSession.delete(fact);
        }
    }

    public void upsertVertex(CEVertex vertex) {
        final boolean shouldVertexBeInWorkingMemory = !vertex.getAlarms().isEmpty();
        final FactHandle fact = vertexToFactMap.get(vertex);
        if (fact != null) {
            if (shouldVertexBeInWorkingMemory) {
                // Update
                kieSession.update(fact, vertex);
            } else {
                // Delete
                kieSession.delete(fact);
            }
        } else if (shouldVertexBeInWorkingMemory) {
            // Insert
            vertexToFactMap.put(vertex, kieSession.insert(vertex));
        }

        // Add/update alarms too
        for (Alarm alarm : vertex.getAlarms()) {
            final CEAlarm ceAlarm = new CEAlarm(vertex, alarm);
            final FactHandle alarmFact = alarmIdToFactMap.get(alarm.getId());
            if (alarmFact != null) {
                kieSession.update(alarmFact, ceAlarm);
            } else {
                alarmIdToFactMap.put(alarm.getId(), kieSession.insert(ceAlarm));
            }
        }
    }

    public void deleteAlarm(CEAlarm alarm) {
        // Delete the alarm
        final FactHandle fact = alarmIdToFactMap.remove(alarm.getId());
        if (fact != null) {
            kieSession.delete(fact);
        }
    }

    public void associateAlarmsWithSituation(Collection<CEAlarm> alarms, String situationId) {
        alarmToSituationMap.associateAlarmsWithSituation(alarms, situationId);
        kieSession.update(alarmToSituationMapHandle, alarmToSituationMap);
    }

    public void disassociateAlarmFromSituation(String alarmId, String situationId) {
        alarmToSituationMap.disassociateAlarmFromSituation(alarmId, situationId);
        kieSession.update(alarmToSituationMapHandle, alarmToSituationMap);

    }

    public void insertFeedback(AlarmFeedback alarmFeedback) {
        kieSession.insert(alarmFeedback);
    }
}
