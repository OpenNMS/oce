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

package org.opennms.oce.engine.itest;

import static org.hamcrest.Matchers.containsInAnyOrder;

import java.util.Set;
import java.util.stream.Collectors;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.opennms.oce.datasource.api.Alarm;
import org.opennms.oce.datasource.api.Situation;

public class SituationMatchers {

    private static class ContainsAlarmsWithIds extends BaseMatcher<Situation> {
        private final String[] alarmsIds;

        public ContainsAlarmsWithIds(String[] alarmsIds) {
            this.alarmsIds = alarmsIds;
        }

        @Override
        public boolean matches(Object item) {
            return containsInAnyOrder(alarmsIds).matches(getAlarmIdsInSituation((Situation)item));
        }

        @Override
        public void describeTo(Description description) {
            containsInAnyOrder(alarmsIds).describeTo(description);
        }

        @Override
        public void describeMismatch(final Object item, final Description description) {
            containsInAnyOrder(alarmsIds).describeMismatch(getAlarmIdsInSituation((Situation)item), description);
        }
    }

    public static ContainsAlarmsWithIds containsAlarmsWithIds(String... alarmsIds) {
        return new ContainsAlarmsWithIds(alarmsIds);
    }

    public static Set<String> getAlarmIdsInSituation(Situation situation) {
        return situation.getAlarms().stream()
                .map(Alarm::getId)
                .collect(Collectors.toSet());
    }
}
