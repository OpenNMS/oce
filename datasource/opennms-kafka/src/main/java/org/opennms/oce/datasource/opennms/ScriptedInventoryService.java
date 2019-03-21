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

import java.util.Collection;

import org.opennms.oce.datasource.common.ScriptedInventoryException;
import org.opennms.oce.datasource.opennms.proto.InventoryModelProtos.InventoryObject;
import org.opennms.oce.datasource.opennms.proto.InventoryModelProtos.InventoryObjects;
import org.opennms.oce.datasource.opennms.proto.OpennmsModelProtos.Alarm;
import org.opennms.oce.datasource.opennms.proto.OpennmsModelProtos.Node;
import org.opennms.oce.datasource.opennms.proto.OpennmsModelProtos.SnmpInterface;
import org.opennms.oce.datasource.opennms.proto.OpennmsModelProtos.TopologyEdge;

/**
 * @author smith
 *
 */
public interface ScriptedInventoryService {

    /**
     * @param snmpInterface
     * @param parent
     * @return
     */
    InventoryObject toInventoryObject(SnmpInterface snmpInterface, InventoryObject parent) throws ScriptedInventoryException;

    /**
     * @param node
     * @return
     */
    Collection<InventoryObject> toInventoryObjects(Node node) throws ScriptedInventoryException;

    /**
     * @param alarm
     * @return
     */
    EnrichedAlarm enrichAlarm(Alarm alarm) throws ScriptedInventoryException;

    /**
     * @param alarm
     * @return
     */
    InventoryFromAlarm getInventoryFromAlarm(Alarm alarm) throws ScriptedInventoryException;

    /**
     * @param edge
     * @return
     */
    InventoryObjects edgeToInventory(TopologyEdge edge) throws ScriptedInventoryException;

}