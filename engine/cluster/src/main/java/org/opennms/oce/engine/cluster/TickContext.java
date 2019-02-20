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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.opennms.oce.datasource.api.Situation;
import org.opennms.oce.datasource.common.ImmutableSituation;

/**
 * Any operations that update a situation during a tick should be done using this context in order
 * to ensure we get consistent results.
 *
 * This helps align the updates performed by the feedback on the cluster mapping operations, since
 * each of these may end up touching the same situations.
 */
public class TickContext {
    private final long timestampInMillis;
    private final AlarmToSituationMap alarmToSituationMap;
    private final Map<String, ImmutableSituation.Builder> newOrUpdatedSituationsById = new LinkedHashMap<>();

    public TickContext(long timestampInMillis, AlarmToSituationMap alarmToSituationMap) {
        this.timestampInMillis = timestampInMillis;
        this.alarmToSituationMap = Objects.requireNonNull(alarmToSituationMap);
    }

    public long getTimestampInMillis() {
        return timestampInMillis;
    }

    public Collection<ImmutableSituation.Builder> getBuildersForNewOrUpdatedSituations() {
        return newOrUpdatedSituationsById.values();
    }

    public ImmutableSituation.Builder getBuilderForExistingSituation(Situation existingSituation) {
        final String situationId = Objects.requireNonNull(existingSituation).getId();
        return newOrUpdatedSituationsById.computeIfAbsent(situationId,
                (sid) -> ImmutableSituation.newBuilderFrom(existingSituation));
    }

    public ImmutableSituation.Builder getBuilderForNewSituationWithId(String situationId) {
        final ImmutableSituation.Builder situationBuilder = ImmutableSituation.newBuilder()
                .setId(situationId)
                .setCreationTime(timestampInMillis);
        newOrUpdatedSituationsById.put(situationId, situationBuilder);
        return situationBuilder;
    }

    public List<Situation> getNewOrUpdatedSituations() {
        return newOrUpdatedSituationsById.values().stream()
                .map(ImmutableSituation.Builder::build)
                .collect(Collectors.toList());
    }

    public Set<String> getNewOrUpdatedSituationIds() {
        return Collections.unmodifiableSet(newOrUpdatedSituationsById.keySet());
    }
}
