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

package org.opennms.oce.datasource.opennms;

import org.opennms.oce.datasource.opennms.proto.OpennmsModelProtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AlarmToInventory {

    private static final Logger LOG = LoggerFactory.getLogger(AlarmToInventory.class);

    public static EnrichedAlarm enrichAlarm(OpennmsModelProtos.Alarm alarm) {
        try {
            return ScriptedInventoryFactory.getFactory().enrichAlarm(alarm);
        } catch (ScriptedInventoryException e) {
            LOG.error("Failed to enrich Alarm: {} : {}", alarm, e.getLocalizedMessage());
            throw new RuntimeException(e);
        }
    }

    public static InventoryFromAlarm getInventoryFromAlarm(OpennmsModelProtos.Alarm alarm) {
        try {
            return ScriptedInventoryFactory.getFactory().getInventoryFromAlarm(alarm);
        } catch (ScriptedInventoryException e) {
            LOG.error("Failed to get Inventory for Alarm: {} : {}", alarm, e.getMessage());
            throw new RuntimeException(e);
        }
    }

}
