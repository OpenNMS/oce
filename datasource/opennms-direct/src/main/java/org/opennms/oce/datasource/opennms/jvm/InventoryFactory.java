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

package org.opennms.oce.datasource.opennms.jvm;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collections;
import java.util.List;

import javax.script.ScriptException;

import org.opennms.integration.api.v1.model.Alarm;
import org.opennms.integration.api.v1.model.Node;
import org.opennms.oce.datasource.api.InventoryObject;

/**
 * A utility class used by the OpenNMS direct data source to derive inventory from nodes and alarms.
 */
public class InventoryFactory {

    private static ScriptedInventoryFactory getScriptedInventoryFactory() {
        URL scriptUri = ClassLoader.getSystemResource("inventory.groovy");
        try {
            File script = new File(scriptUri.toURI());
            return new ScriptedInventoryFactory(script);
        } catch (URISyntaxException | IOException | ScriptException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Derives inventory from a {@link Node node}.
     *
     * @param node the node to derive inventory from
     * @return the list of derived inventory
     */
    public static List<InventoryObject> createInventoryObjects(Node node) {
        try {
            return getScriptedInventoryFactory().createInventoryObjects(node);
        } catch (NoSuchMethodException | ScriptException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    /**
     * Derives inventory from an {@link Alarm alarm}.
     *
     * @param alarm the alarm to derive inventory from
     * @return the list of derived inventory
     */
    public static List<InventoryObject> createInventoryObjects(Alarm alarm) {
        try {
            return getScriptedInventoryFactory().createInventoryObjects(alarm);
        } catch (NoSuchMethodException | ScriptException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    /**
     * Gets the node criteria string for the given node.
     *
     * @param node the node
     * @return the node criteria string
     */
    protected static String toNodeCriteria(Node node) {
        if (node == null) {
            return null;
        }
        return toNodeCriteria(node.getForeignSource(), node.getForeignId(), node.getId());
    }

    /**
     * @return a node criteria string formatted based on the given values
     */
    private static String toNodeCriteria(String foreignSource, String foreignId, int id) {
        if (!Strings.isNullOrEmpty(foreignSource) && !Strings.isNullOrEmpty(foreignId)) {
            return foreignSource + ":" + foreignId;
        } else {
            return Long.valueOf(id).toString();
        }
    }
}
