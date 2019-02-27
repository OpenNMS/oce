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

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;

import javax.script.ScriptException;

import org.opennms.oce.datasource.opennms.proto.InventoryModelProtos;
import org.opennms.oce.datasource.opennms.proto.OpennmsModelProtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NodeToInventory {

    private static final Logger LOG = LoggerFactory.getLogger(NodeToInventory.class);

    public static Collection<InventoryModelProtos.InventoryObject> toInventoryObjects(OpennmsModelProtos.Node node) {
        try {
            return getScriptedInventoryFactory().toInventoryObjects(node);
        } catch (NoSuchMethodException | ScriptException e) {
            LOG.warn("Failed to create inventory for node {} : {}", node, e.getMessage());
            return Collections.emptyList();
        }
    }

    public static InventoryModelProtos.InventoryObject toInventoryObject(OpennmsModelProtos.SnmpInterface snmpInterface,
            InventoryModelProtos.InventoryObject parent) throws NoSuchMethodException, ScriptException {
        return getScriptedInventoryFactory().toInventoryObject(snmpInterface, parent);
    }

    private static ScriptedInventoryFactory getScriptedInventoryFactory() {
        URL scriptUri = ClassLoader.getSystemResource("inventory.groovy");
        try {
            File script = new File(scriptUri.toURI());
            return new ScriptedInventoryFactory(script);
        } catch (URISyntaxException | IOException | ScriptException e) {
            throw new RuntimeException(e);
        }
    }
}
