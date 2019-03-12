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

package org.opennms.e2e.containers;

import static org.awaitility.Awaitility.await;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.opennms.e2e.containers.util.Karaf;
import org.opennms.e2e.containers.util.Network;
import org.opennms.e2e.containers.util.Overlay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.AbstractWaitStrategy;

public class OCEContainer {
    private static final Logger LOG = LoggerFactory.getLogger(OCEContainer.class);
    private static final int OCE_SSH_PORT = 8301;
    private static Path sentinelOverlayPath;
    public static String ALIAS = "oce";

    private GenericContainer container;

    public GenericContainer getContainer() {
        prepare();
        if (container == null) {
            container = new GenericContainer<>(DockerTagResolver.getTag("oce"))
                    .withExposedPorts(OCE_SSH_PORT)
                    .withEnv("KARAF_DEBUG_LOGGING", "org.opennms.oce")
                    .withFileSystemBind(sentinelOverlayPath.toString(), "/opt/sentinel-overlay")
                    .withNetwork(Network.getNetwork())
                    .withNetworkAliases(ALIAS)
                    .withCommand("-f")
                    .waitingFor(new WaitForOCE());
        }
        return container;
    }

    private static void prepare() {
        sentinelOverlayPath = Overlay.setupOverlay("sentinel-overlay", OCEContainer.class);
        Path deployPath = sentinelOverlayPath.resolve("deploy");
        Path standaloneFeatures = deployPath.resolve("features-standalone.xml");
        Path redundantFeatures = deployPath.resolve("features-redundant.xml");

        // We expect to find exactly 1 kar file that will be overlayed onto the deploy directory of the sentinel
        // container to install OCE
        File[] karFiles = deployPath.toFile().listFiles((dir, name) -> name.endsWith(".kar"));
        if (karFiles == null || karFiles.length < 1) {
//            throw new RuntimeException("Could not find the .kar file to deploy OCE");
        }
        if (karFiles.length > 1) {
//            throw new RuntimeException("Found too many .kar files for deploying OCE");
        }

//        if (redundant) {
//            standaloneFeatures.toFile().delete();
//            redundantFeatures.toFile().renameTo(deployPath.resolve("features.xml").toFile());
//
//            try {
//                insertApplicationId(sentinelOverlayPath);
//            } catch (IOException e) {
//                throw new RuntimeException(e);
//            }
//        } else {
        redundantFeatures.toFile().delete();
        standaloneFeatures.toFile().renameTo(deployPath.resolve("features.xml").toFile());
//        }
    }

//    private void insertApplicationId(Path overlayDir) throws IOException {
//        String applicationIdProperty = "\napplication.id = oce-datasource-instance-" + instanceNum++;
//        Files.write(Paths.get(overlayDir.toString(), "etc", "org.opennms.oce.datasource.opennms.kafka" +
//                ".streams.cfg"), applicationIdProperty.getBytes(), StandardOpenOption.APPEND);
//    }

//    private void shutdownKarafOnInstance(String alias) throws Exception {
//        LOG.info("Shutting down Karaf on {}", alias);
//        OpenNMSHelmStack.runKarafCommands(stack.getOCESSHAddress(alias), "system:shutdown -f");
//        // Make sure the Karaf instance is finished shutting down
//        stack.waitForOCEToTerminateByAlias(alias);
//    }

//    public void waitForOCEToTerminateByAlias(String alias) {
//        LOG.info("Waiting for {} to terminate...", alias);
//
//        await()
//                .atMost(1, TimeUnit.MINUTES)
//                .pollInterval(5, TimeUnit.SECONDS)
//                .until(() -> {
//                    try {
//                        runKarafCommands(stacker.getServiceAddress(alias, 8301), "logout");
//                    } catch (Exception e) {
//                        return true;
//                    }
//
//                    return false;
//                });
//
//        LOG.info("{} has terminated", alias);
//    }

    private static class WaitForOCE extends AbstractWaitStrategy {
        @Override
        protected void waitUntilReady() {
            Karaf.waitForBundleActive("org.opennms.oce.driver", Network.getConnectionAddress(waitStrategyTarget,
                    OCE_SSH_PORT));
        }
    }
}
