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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.opennms.oce.datasource.api.Situation;

public class AlarmToSituationMap {

    private final Map<String, String> alarmIdToSituationMap = new LinkedHashMap<>();

    public String getSituationIdForAlarmId(String alarmId) {
        return alarmIdToSituationMap.get(alarmId);
    }

    public Set<String> getAlarmIdsInSituations() {
        return alarmIdToSituationMap.keySet();
    }

    public void associateAlarmWithSituation(CEAlarm alarm, String situationId) {
        alarmIdToSituationMap.put(alarm.getId(), situationId);
    }

    void associateAlarmsWithSituation(Collection<CEAlarm> alarms, String situationId) {
        alarms.forEach(a -> alarmIdToSituationMap.put(a.getId(), situationId));
    }

    public void disassociateAlarmFromSituation(String alarmId, String situationId) {
        alarmIdToSituationMap.remove(alarmId);
    }

    public Situation getSituationById(String situationId) {
        throw new UnsupportedOperationException("TODO");
    }

}
