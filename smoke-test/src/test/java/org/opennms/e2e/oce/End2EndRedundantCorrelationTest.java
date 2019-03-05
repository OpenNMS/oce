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

package org.opennms.e2e.oce;

import static org.awaitility.Awaitility.await;

import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.Rule;
import org.junit.Test;
import org.opennms.e2e.stacks.OpenNMSHelmOCEStack;
import org.opennms.e2e.stacks.OpenNMSHelmStack;
import org.opennms.gizmo.docker.GizmoDockerRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class End2EndRedundantCorrelationTest extends CorrelationTestBase {
    private static final Logger LOG = LoggerFactory.getLogger(End2EndRedundantCorrelationTest.class);
    private String activeOCEAlias;
    @Rule
    public final GizmoDockerRule gizmo = getGizmoRule();

    @Test
    public void canCorrelateAlarmsAfterFailure() throws Exception {
        try {
            setup();
            waitForActiveOCE();
            shutdownKarafOnInstance(activeOCEAlias);

            LOG.info("Triggering alarms for correlation via API...");
            // TODO: This will be replaced with a call to switch sim to generate some alarms
            openNMSRestClient.triggerAlarmsForCorrelation();

            // OCE Should now correlate them, we need to wait here for the situation alarm to show up
            LOG.info("Waiting for a situation to be received by OpenNMS...");
            openNMSRestClient.waitForOutstandingSituation();

            // Login, navigate to dashboard, view alarm in table, verify the related alarms
            LOG.info("Situation received, verifying via Helm...");
            verifyGenericSituation(gizmo);
        } finally {
            cleanup();
        }
    }

    @Override
    OpenNMSHelmOCEStack getStack() {
        return OpenNMSHelmOCEStack.withRedundantOCE();
    }

    private Optional<String> getActiveOCEAlias() throws Exception {
        for (String oceAlias : OpenNMSHelmOCEStack.redundanctOCEs) {
            String[] output = OpenNMSHelmStack.runKarafCommands(stack.getOCESSHAddress(oceAlias),
                    "processor:current-role").split("\n");

            if (Arrays.stream(output).anyMatch(row -> row.contains("ACTIVE"))) {
                return Optional.of(oceAlias);
            }
        }

        return Optional.empty();
    }

    private void waitForActiveOCE() {
        LOG.info("Waiting for an active OCE instance...");

        await()
                .atMost(2, TimeUnit.MINUTES)
                .pollInterval(10, TimeUnit.SECONDS)
                .until(() -> {
                    Optional<String> activeOCE = getActiveOCEAlias();

                    if (activeOCE.isPresent()) {
                        activeOCEAlias = activeOCE.get();

                        return true;
                    }

                    return false;
                });

        LOG.info("OCE {} is now active", activeOCEAlias);
    }

    private void shutdownKarafOnInstance(String alias) throws Exception {
        LOG.info("Shutting down Karaf on {}", alias);
        OpenNMSHelmStack.runKarafCommands(stack.getOCESSHAddress(alias), "system:shutdown -f");
        // Make sure the Karaf instance is finished shutting down
        stack.waitForOCEToTerminateByAlias(alias);
    }
}
