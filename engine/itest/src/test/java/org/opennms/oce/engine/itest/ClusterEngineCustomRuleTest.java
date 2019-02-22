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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.opennms.oce.engine.itest.SituationMatchers.containsAlarmsWithIds;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.opennms.oce.datasource.api.Alarm;
import org.opennms.oce.datasource.api.InventoryObject;
import org.opennms.oce.datasource.api.Severity;
import org.opennms.oce.datasource.api.Situation;
import org.opennms.oce.driver.test.MockAlarmBuilder;
import org.opennms.oce.driver.test.MockInventoryBuilder;
import org.opennms.oce.driver.test.MockInventoryType;
import org.opennms.oce.driver.test.TestDriver;
import org.opennms.oce.engine.cluster.ClusterEngine;
import org.opennms.oce.engine.cluster.ClusterEngineFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;

public class ClusterEngineCustomRuleTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    /**
     * Verify that we can enhance the cluster correlation with a custom rule-set.
     *
     * t=0    a1 triggered
     * t=1    a2 triggered
     * t=2    a3 triggered
     * t=300  a4 triggered
     *
     * Using the default configuration we expect:
     *   s1 = {a1,a2,a3}
     *   uncorrelated = {a4}
     *
     * Using custom rules we should be able to have:
     *   s1 = {a1,a2,a4}
     *   uncorrelated = {a3}
     *
     * This will show to to both add, and exclude specific alarms from correlation.
     */
    @Test
    public void canEnhanceClusterCorrelationWithCustomRules() throws IOException {
        final List<InventoryObject> inventory = ImmutableList.copyOf(new MockInventoryBuilder()
                .withInventoryObject(MockInventoryType.DEVICE, "n1")
                .withInventoryObject(MockInventoryType.PORT, "n1-p1", MockInventoryType.PORT, "n1")
                .getInventory());

        long clearedAt = TimeUnit.HOURS.toMillis(2);

        final List<Alarm> alarms = new ArrayList<>();
        alarms.addAll(new MockAlarmBuilder()
                .withId("a1")
                .withInventoryObject(MockInventoryType.PORT, "n1-p1")
                .withEvent(1, Severity.MAJOR)
                .withEvent(clearedAt, Severity.CLEARED)
                .build());
        alarms.addAll(new MockAlarmBuilder()
                .withId("a2")
                .withInventoryObject(MockInventoryType.PORT, "n1-p1")
                .withEvent(2, Severity.MAJOR)
                .withEvent(clearedAt, Severity.CLEARED)
                .build());
        alarms.addAll(new MockAlarmBuilder()
                .withId("a3")
                .withInventoryObject(MockInventoryType.PORT, "n1-p1")
                .withEvent(3, Severity.MAJOR)
                .withEvent(clearedAt, Severity.CLEARED)
                .build());

        long offset = TimeUnit.MINUTES.toMillis(5); // should be far enough so that it doesn't get automatically correlated
        alarms.addAll(new MockAlarmBuilder()
                .withId("a4")
                .withInventoryObject(MockInventoryType.PORT, "n1-p1")
                .withEvent(4 + offset, Severity.MAJOR)
                .withEvent(clearedAt + offset, Severity.CLEARED)
                .build());

        ClusterEngineFactory clusterEngineFactory = new ClusterEngineFactory();
        TestDriver driver = TestDriver.builder()
                .withEngineFactory(clusterEngineFactory)
                .build();
        List<Situation> situations = driver.run(alarms, inventory);

        // Verify that by default, a1, a2 and a3 get correlated together
        assertThat(situations, hasSize(1));
        assertThat(situations.get(0), containsAlarmsWithIds("a1", "a2", "a3"));

        // TODO: Make it possible to extend the current ruleset instead of having to copy over the existing rules too
        // Now let's use our rule-set
        clusterEngineFactory.setRulesFolder(temporaryFolder.getRoot().getAbsolutePath());
        // Copy the default rules over
        URL url = Resources.getResource(ClusterEngine.class,"cluster.drl");
        try (InputStream is = url.openStream()) {
            Files.copy(is, temporaryFolder.getRoot().toPath().resolve("cluster.drl"));
        }
        url = Resources.getResource("cluster_ext.drl");
        try (InputStream is = url.openStream()) {
            Files.copy(is, temporaryFolder.getRoot().toPath().resolve("cluster_ext.drl"));
        }

        // Run the test driver again
        situations = driver.run(alarms, inventory);

        // Verify that by default, a1, a2 and a4 should get correlated together
        assertThat(situations, hasSize(1));
        assertThat(situations.get(0), containsAlarmsWithIds("a1", "a2", "a4"));
    }

    @Test
    public void canGenerateCardDownSituations() {
        final List<InventoryObject> inventory = ImmutableList.copyOf(new MockInventoryBuilder()
                .withInventoryObject(MockInventoryType.DEVICE, "n1")
                .withInventoryObject(MockInventoryType.CARD, "n1-c1", MockInventoryType.DEVICE, "n1")
                .withInventoryObject(MockInventoryType.PORT, "n1-c1-p1", MockInventoryType.CARD, "n1-c1", 25)
                .withInventoryObject(MockInventoryType.PORT, "n1-c1-p2", MockInventoryType.CARD, "n1-c1", 25)
                .getInventory());

        final List<Alarm> alarms = new ArrayList<>();
        alarms.addAll(new MockAlarmBuilder()
                .withId("a1")
                .withInventoryObject(MockInventoryType.PORT, "n1-c1-p1")
                .withEvent(1525579974000L, Severity.MAJOR)
                .withEvent(1525580004000L, Severity.CLEARED) // 30 seconds since last event
                .build());
        alarms.addAll(new MockAlarmBuilder()
                .withId("a2")
                .withInventoryObject(MockInventoryType.PORT, "n1-c1-p2")
                .withEvent(1525579974000L, Severity.MINOR)
                .withEvent(1525580004000L, Severity.CLEARED) // 30 seconds since last event
                .build());
    }
}
