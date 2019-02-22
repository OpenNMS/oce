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

package org.opennms.oce.datasource.api;

import java.util.List;
import java.util.Set;

/**
 * A situation represents a group of correlated alarms that have been associated together based on their characteristics
 * (such as being close in space and time).
 */
public interface Situation {

    String getId();

    long getCreationTime();

    List<ResourceKey> getResourceKeys();

    Set<Alarm> getAlarms();

    Set<String> getAlarmIds();

    /**
     * Helper function used to quickly check whether or not a given alarm id is currently
     * part of this situation.
     *
     * @param alarmId id of the alarm
     * @return true if the alarm is currently part of the situation, false otherwise
     */
    boolean containsAlarm(String alarmId);

    Severity getSeverity();

    /**
     * A human readable string that helps explain why
     * the alarms are grouped together.
     */
    String getDiagnosticText();

}
