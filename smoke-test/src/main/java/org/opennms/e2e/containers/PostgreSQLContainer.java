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

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;

import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;

import org.opennms.e2e.containers.util.Network;
import org.postgresql.util.PSQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.AbstractWaitStrategy;

public class PostgreSQLContainer {
    private static final Logger LOG = LoggerFactory.getLogger(PostgreSQLContainer.class);
    public static String ALIAS = "db";
    public static final int POSTGRES_PORT = 5432;
    public static String USERNAME = "postgres";
    public static String PASSWORD = USERNAME;

    private final GenericContainer container = new GenericContainer<>(DockerTagResolver.getTag("postgres"))
            .withExposedPorts(POSTGRES_PORT)
            .withEnv("POSTGRES_PASSWORD", PASSWORD)
            .withNetwork(Network.getNetwork())
            .withNetworkAliases(ALIAS)
            .waitingFor(new WaitForPostgres());

    public GenericContainer getContainer() {
        return container;
    }

    private static class WaitForPostgres extends AbstractWaitStrategy {
        @Override
        protected void waitUntilReady() {
            InetSocketAddress postgresAddr = Network.getConnectionAddress(waitStrategyTarget, POSTGRES_PORT);
            String url = String.format("jdbc:postgresql://%s:%d/template1",
                    postgresAddr.getHostString(), postgresAddr.getPort());
            Properties props = new Properties();
            props.setProperty("user", USERNAME);
            props.setProperty("password", PASSWORD);
            LOG.info("Waiting for PostgreSQL service @ {}", url);
            await().atMost(2, MINUTES)
                    .pollInterval(5, SECONDS).pollDelay(5, SECONDS)
                    .ignoreException(PSQLException.class)
                    .until(() -> {
                        try (Connection c = DriverManager.getConnection(url, props)) {
                            // We got a connection!
                            return true;
                        }
                    }, equalTo(true));
            LOG.info("PostgreSQL service is online.");
        }
    }
}
