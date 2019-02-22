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
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.opennms.oce.datasource.api.Alarm;
import org.opennms.oce.datasource.api.ResourceKey;
import org.opennms.oce.datasource.api.Severity;
import org.opennms.oce.datasource.api.Situation;

public class CESituation implements Situation {

    private final Situation situation;
    private final List<CEVertex> vertices;

    public CESituation(Situation situation, List<CEVertex> vertices) {
        this.situation = Objects.requireNonNull(situation);
        this.vertices = Collections.unmodifiableList(new ArrayList<>(vertices));
    }

    @Override
    public String getId() {
        return situation.getId();
    }

    @Override
    public long getCreationTime() {
        return situation.getCreationTime();
    }

    @Override
    public List<ResourceKey> getResourceKeys() {
        return situation.getResourceKeys();
    }

    @Override
    public Set<Alarm> getAlarms() {
        return situation.getAlarms();
    }

    @Override
    public Set<String> getAlarmIds() {
        return situation.getAlarmIds();
    }

    @Override
    public boolean containsAlarm(String alarmId) {
        return situation.containsAlarm(alarmId);
    }

    @Override
    public Severity getSeverity() {
        return situation.getSeverity();
    }

    @Override
    public String getDiagnosticText() {
        return situation.getDiagnosticText();
    }

    public List<CEVertex> getVertices() {
        return vertices;
    }

    @Override
    public String toString() {
        return "CESituation{" +
                "situation=" + situation +
                ", vertices=" + vertices +
                '}';
    }
}
