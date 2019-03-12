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

package org.opennms.e2e.oce_new;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.util.Collections;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.opennms.e2e.containers.KafkaContainer;
import org.opennms.e2e.containers.OCEContainer;
import org.opennms.e2e.containers.OpenNMSHelmContainer;
import org.opennms.e2e.containers.OpenNMSHorizonContainer;
import org.opennms.e2e.containers.PostgreSQLContainer;
import org.opennms.e2e.core.WebDriverStrategy;
import org.opennms.e2e.grafana.Grafana44SeleniumDriver;
import org.opennms.e2e.grafana.GrafanaRestClient;
import org.opennms.e2e.opennms.OpenNMSRestClient;
import org.opennms.e2e.selenium.LocalChromeWebDriverStrategy;
import org.opennms.e2e.selenium.SauceLabsWebDriverStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class CorrelationTestBase {
    private static final Logger LOG = LoggerFactory.getLogger(CorrelationTestBase.class);
    private static final String pluginName = "opennms-helm-app";
    private static final String dataSourceName = "OpenNMS-Fault-Management";
    private static final String dashboardName = "Helm-Dashboard";
    private static final String genericAlarmTitle = "Alarm: Generic Trigger";

    GrafanaRestClient grafanaRestClient;
    OpenNMSRestClient openNMSRestClient;
    OpenNMSHelmContainer helmContainer;
    OpenNMSHorizonContainer opennmsContainer;

    @Before
    public void init() {
        new KafkaContainer().getContainer().start();
        new PostgreSQLContainer().getContainer().start();
        opennmsContainer = new OpenNMSHorizonContainer();
        opennmsContainer.getContainer().start();
        helmContainer = new OpenNMSHelmContainer();
        helmContainer.getContainer().start();
        new OCEContainer().getContainer().start();
    }

    WebDriverStrategy getWebDriverStrategy(String testName) throws IOException {
//        return new SauceLabsWebDriverStrategy(testName);
        return new LocalChromeWebDriverStrategy();
    }

    void setupHelm(GrafanaRestClient grafanaRestClient) throws IOException {
        // Enable Helm plugin
        grafanaRestClient.setPluginStatus(pluginName, true);

        // Create FM datasource
        grafanaRestClient.addFMDataSource(dataSourceName);

        // Create dashboard with alarm table
        grafanaRestClient.addFMDasboard(dashboardName, dataSourceName);
    }

    void cleanupHelm(GrafanaRestClient grafanaRestClient) {
        grafanaRestClient.deleteDashboard(dashboardName);
        grafanaRestClient.deleteDataSource(dataSourceName);
        grafanaRestClient.setPluginStatus(pluginName, false);
    }

    void verifyGenericSituation() throws Exception {
        try (final WebDriverStrategy webDriverStrategy = getWebDriverStrategy("test")) {
            try {
                new Grafana44SeleniumDriver(webDriverStrategy.getDriver(), helmContainer.getHelmUrl())
                        .home()
                        .dashboard(dashboardName)
                        .verifyAnAlarmIsPresent()
                        .verifyRelatedAlarmLabels(Collections.singletonList(Pair.of(genericAlarmTitle, 3)));
            } catch (Exception e) {
                webDriverStrategy.setFailed(true);
                throw new RuntimeException(e);
            }
        }
    }

    void setup() throws IOException {
        LOG.info("Setting up...");
        grafanaRestClient = new GrafanaRestClient(helmContainer.getHelmUrl());
        openNMSRestClient = new OpenNMSRestClient(opennmsContainer.getOpenNMSUrl());
        setupHelm(grafanaRestClient);
        // No alarms/situations
        assertThat(openNMSRestClient.getAlarms(), hasSize(0));
    }

    void cleanup() {
        LOG.info("Cleaning up...");
        openNMSRestClient.clearAllAlarms();
        cleanupHelm(grafanaRestClient);
    }
}
