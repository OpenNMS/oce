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

package org.opennms.e2e.stacks;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;

import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import org.opennms.gizmo.docker.GizmoDockerStacker;
import org.opennms.gizmo.docker.stacks.EmptyDockerStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.HostConfig;

public class KafkaStack extends EmptyDockerStack {
    private static final Logger LOG = LoggerFactory.getLogger(KafkaStack.class);
    public static String KAFKA = "KAFKA";

    @Override
    public Map<String, Function<GizmoDockerStacker, ContainerConfig>> getContainersByAlias() {
        return ImmutableMap.of(KAFKA, (stacker) -> ContainerConfig.builder()
                .image(DockerTagResolver.getTag("kafka"))
                .exposedPorts("2181/tcp", "9092/tcp")
                .env("KAFKA_LISTENERS: PLAINTEXT://:9092", "KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092")
                .hostConfig(HostConfig.builder()
                        .publishAllPorts(true)
                        .autoRemove(true)
                        .build())
                .build()
        );
    }

    @Override
    public List<Consumer<GizmoDockerStacker>> getWaitingRules() {
        return ImmutableList.of(KafkaStack::waitForKafka);
    }

    private static void waitForKafka(GizmoDockerStacker stacker) {
        int kafkaAutoPort = stacker.getServiceAddress(KafkaStack.KAFKA, 9092).getPort();
        int zookeeperAutoPort = stacker.getServiceAddress(KafkaStack.KAFKA, 2181).getPort();

        LOG.info("Waiting for Kafka...");
        await().atMost(2, MINUTES)
                .pollInterval(5, SECONDS).pollDelay(5, SECONDS)
                .until(() -> serverListening("localhost", kafkaAutoPort) &&
                                serverListening("localhost", zookeeperAutoPort)
                        , equalTo(true));
        LOG.info("Kafka is ready");
    }

    private static boolean serverListening(String host, int port) {
        Socket s = null;
        try {
            s = new Socket(host, port);
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            if (s != null)
                try {
                    s.close();
                } catch (Exception e) {
                }
        }
    }
}
