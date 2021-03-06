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

package org.opennms.oce.processor.standalone;

import java.util.Objects;

import org.opennms.oce.datasource.api.Situation;
import org.opennms.oce.datasource.api.SituationDatasource;
import org.opennms.oce.processor.api.SituationProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A situation processor that immediately forwards all situations.
 */
public class StandaloneSituationProcessor implements SituationProcessor {
    /**
     * The logger.
     */
    private static final Logger LOG = LoggerFactory.getLogger(StandaloneSituationProcessor.class);

    /**
     * The situation data source.
     */
    private final SituationDatasource situationDatasource;

    /**
     * Constructor.
     *
     * @param situationDatasource the situation data source
     */
    StandaloneSituationProcessor(SituationDatasource situationDatasource) {
        this.situationDatasource = Objects.requireNonNull(situationDatasource);
    }

    @Override
    @SuppressWarnings("Duplicates")
    public void accept(Situation situation) {
        Objects.requireNonNull(situation);

        try {
            LOG.debug("Forwarding situation: {}", situation);
            situationDatasource.forwardSituation(situation);
            LOG.debug("Successfully forwarded situation.");
        } catch (Exception e) {
            LOG.error("An error occurred while forwarding situation: {}. The situation will be lost.", situation, e);
        }
    }
}
