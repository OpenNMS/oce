/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2017-2017 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2017 The OpenNMS Group, Inc.
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

import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.Function;

import org.opennms.gizmo.docker.GizmoDockerStacker;
import org.opennms.gizmo.docker.stacks.EmptyDockerStack;
import org.postgresql.util.PSQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.HostConfig;

public class PostgreSQLStack extends EmptyDockerStack {
    private static final Logger LOG = LoggerFactory.getLogger(PostgreSQLStack.class);

    public static String POSTGRES = "POSTGRES";

    public static String USERNAME = "postgres";
    public static String PASSWORD = USERNAME;

    @Override
    public Map<String, Function<GizmoDockerStacker, ContainerConfig>> getContainersByAlias() {
        return ImmutableMap.of(POSTGRES, (stacker) -> ContainerConfig.builder()
                .image("postgres:9.6.1")
                .env("POSTGRES_PASSWORD=" + PASSWORD)
                .hostConfig(HostConfig.builder()
                        .publishAllPorts(true)
                        .autoRemove(true)
                        .build())
                .build()
        );
    }

    @Override
    public List<Consumer<GizmoDockerStacker>> getWaitingRules() {
        return ImmutableList.of(PostgreSQLStack::waitForPostgres);
    }

    private static void waitForPostgres(GizmoDockerStacker stacker) {
        final InetSocketAddress postgresAddr = stacker.getServiceAddress(POSTGRES, 5432);

        String url = String.format("jdbc:postgresql://%s:%d/template1",
                postgresAddr.getHostString(), postgresAddr.getPort());
        Properties props = new Properties();
        props.setProperty("user", USERNAME);
        props.setProperty("password", PASSWORD );
        LOG.info("Waiting for PostgreSQL service @ {}", url);
        await().atMost(2, MINUTES)
                .pollInterval(5, SECONDS).pollDelay(5, SECONDS)
                .ignoreException(PSQLException.class)
                .until(() -> {
                    try(Connection c = DriverManager.getConnection(url, props)) {
                        // We got a connection!
                        return true;
                    }
                }, equalTo(true));
        LOG.info("PostgreSQL service is online.");
    }
}
