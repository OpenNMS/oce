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

package org.opennms.oce.engine.dbscan;

import java.util.Collections;

import org.junit.Test;
import org.opennms.oce.datasource.common.ImmutableAlarm;
import org.opennms.oce.datasource.common.ImmutableInventoryObject;
import org.opennms.oce.driver.test.MockInventoryType;

public class DBScanEnginePerfTest {

    /**
     * Utility method for testing performance of ticks.
     *
     * No assertions here, but useful for tracking performance.
     */
    @Test
    public void canRunDBScanOnLargeGraphs() {
        final DBScanEngine dbScanEngine = new DBScanEngine();
        dbScanEngine.init(Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList());
        final int K = 500;
        for (int k = 0; k < K; k++) {
            dbScanEngine.onInventoryAdded(Collections.singletonList(ImmutableInventoryObject.newBuilder()
                    .setType(MockInventoryType.COMPONENT.getType())
                    .setId(Integer.toString(k))
                    .build()));
        }

        for (int j = 1; j <= 3; j++) {
            // Reset the alarms before every tick
            for (int k = 0; k < K; k++) {
                dbScanEngine.onAlarmCreatedOrUpdated(ImmutableAlarm.newBuilder()
                        .setTime(0)
                        .setId(Integer.toString(k))
                        .setInventoryObjectType(MockInventoryType.COMPONENT.getType())
                        .setInventoryObjectId(Integer.toString(k))
                        .build());
            }

            long start = System.currentTimeMillis();
            dbScanEngine.tick(dbScanEngine.getTickResolutionMs() * j);
            long delta = System.currentTimeMillis() - start;
            System.out.printf("%d ms for %d vertices.\n", delta, K);
        }
    }
}
