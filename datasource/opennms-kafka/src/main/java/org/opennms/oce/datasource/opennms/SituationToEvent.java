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

package org.opennms.oce.datasource.opennms;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.opennms.oce.datasource.api.Alarm;
import org.opennms.oce.datasource.api.Severity;
import org.opennms.oce.datasource.api.Situation;
import org.opennms.oce.datasource.opennms.events.Event;

/**
 * Contains the logic used to convert a situation to an event.
 */
public class SituationToEvent {
    public static final String SITUATION_UEI = "uei.opennms.org/alarms/situation";
    public static final String SITUATION_ID_PARM_NAME = "situationId";

    public static Event toEvent(Situation situation) {
        final Event e = new Event();
        e.setUei(SITUATION_UEI);

        // Use the max severity as the situation severity
        final Severity maxSeverity = Severity.fromValue(situation.getAlarms().stream()
                .mapToInt(a -> a.getSeverity() != null ? a.getSeverity().getValue() : Severity.INDETERMINATE.getValue())
                .max()
                .getAsInt());
        e.setSeverity(maxSeverity.name().toLowerCase());

        // Relay the situation id
        e.addParam(SITUATION_ID_PARM_NAME, situation.getId());

        final Alarm alarmForDescr = getAlarmForDescription(situation.getAlarms());
        e.addParam("situationLogMsg", alarmForDescr.getSummary());

        String description = alarmForDescr.getDescription();
        if (situation.getDiagnosticText() != null) {
            description += "\n<p>OCE Diagnostic: " + situation.getDiagnosticText() + "</p>";
        }
        e.addParam("situationDescr", description);

        // Set the related reduction keys
        situation.getAlarms().stream()
                .map(Alarm::getId)
                .forEach(reductionKey -> e.addParam("related-reductionKey", reductionKey));

        // Set a node id - use the same node associated with the alarm we used to the description
        e.setNodeId(alarmForDescr.getNodeId());

        return e;
    }

    private static Alarm getAlarmForDescription(Collection<Alarm> alarms) {
        // Sort the alarms by time (ascending)
        final List<Alarm> sortedAlarms = alarms.stream()
                .sorted(Comparator.comparing(Alarm::getTime))
                .collect(Collectors.toList());

        // Use the alarm that is not cleared (if any), or fallback to the earliest alarm otherwise
        return sortedAlarms.stream()
                .filter(a -> !a.isClear())
                .findFirst()
                .orElseGet(() -> sortedAlarms.get(0));
    }
}
