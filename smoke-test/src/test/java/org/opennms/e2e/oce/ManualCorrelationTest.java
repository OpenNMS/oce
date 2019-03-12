///*******************************************************************************
// * This file is part of OpenNMS(R).
// *
// * Copyright (C) 2018 The OpenNMS Group, Inc.
// * OpenNMS(R) is Copyright (C) 1999-2018 The OpenNMS Group, Inc.
// *
// * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
// *
// * OpenNMS(R) is free software: you can redistribute it and/or modify
// * it under the terms of the GNU Affero General Public License as published
// * by the Free Software Foundation, either version 3 of the License,
// * or (at your option) any later version.
// *
// * OpenNMS(R) is distributed in the hope that it will be useful,
// * but WITHOUT ANY WARRANTY; without even the implied warranty of
// * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// * GNU Affero General Public License for more details.
// *
// * You should have received a copy of the GNU Affero General Public License
// * along with OpenNMS(R).  If not, see:
// *      http://www.gnu.org/licenses/
// *
// * For more information contact:
// *     OpenNMS(R) Licensing <license@opennms.org>
// *     http://www.opennms.org/
// *     http://www.opennms.com/
// *******************************************************************************/
//
//package org.opennms.e2e.oce;
//
//import org.junit.Ignore;
//import org.junit.Rule;
//import org.junit.Test;
//import org.opennms.e2e.core.WebDriverStrategy;
//import org.opennms.e2e.grafana.Grafana44SeleniumDriver;
//import org.opennms.e2e.containers.OpenNMSHelmOCEStack;
//import org.opennms.gizmo.docker.GizmoDockerRule;
//
//@Ignore("Subset of the end2end topology for manual verification")
//public class ManualCorrelationTest extends CorrelationTestBase {
//    @Rule
//    public final GizmoDockerRule gizmo = getGizmoRule();
//
//    @Test
//    public void canStartStack() throws Exception {
//        try (final WebDriverStrategy webDriverStrategy =
//                     getWebDriverStrategy(gizmo.getDescription().getClassName() + "." + gizmo.getDescription().getMethodName())) {
//            try {
//                Grafana44SeleniumDriver grafanaDriver = new Grafana44SeleniumDriver(webDriverStrategy.getDriver(),
//                        stack.getHelmUrl());
//                grafanaDriver.home();
//            } catch (Exception e) {
//                webDriverStrategy.setFailed(true);
//                throw new RuntimeException(e);
//            }
//        }
//    }
//
//    @Test
//    public void canViewRelatedAlarms() throws Exception {
//        try {
//            setup();
//
//            // Trigger a situation alarm on OpenNMS
//            openNMSRestClient.triggerGenericSituation();
//
//            // Login, navigate to dashboard, view alarm in table, verify the related alarms
//            verifyGenericSituation(gizmo);
//        } finally {
//            cleanup();
//        }
//    }
//
//    @Override
//    OpenNMSHelmOCEStack getStack() {
//        return OpenNMSHelmOCEStack.withStandaloneOCE();
//    }
//}
