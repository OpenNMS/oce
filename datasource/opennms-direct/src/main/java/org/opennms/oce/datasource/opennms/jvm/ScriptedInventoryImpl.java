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

package org.opennms.oce.datasource.opennms.jvm;

import java.util.List;

import javax.script.ScriptException;

import org.opennms.integration.api.v1.model.Alarm;
import org.opennms.integration.api.v1.model.Node;
import org.opennms.oce.datasource.api.InventoryObject;
import org.opennms.oce.datasource.common.ImmutableAlarm;
import org.opennms.oce.datasource.common.ScriptedInventoryException;
import org.opennms.oce.datasource.common.inventory.script.AbstractScriptedInventory;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author smith
 *
 */
public class ScriptedInventoryImpl extends AbstractScriptedInventory implements ScriptedInventoryService {

    private static final Logger LOG = LoggerFactory.getLogger(ScriptedInventoryImpl.class);

    public ScriptedInventoryImpl(String scriptPath) {
        super(scriptPath, 30000, null);
    }

    public ScriptedInventoryImpl(String scriptPath, long scriptCacheMillis, BundleContext bundleContext) {
        super(scriptPath, scriptCacheMillis, bundleContext);
    }

    public void init() {
        LOG.info("ScriptedInventoryImpl init'd");
    }

    /**
     * Overrides the inventory type and instance for an alarm if they aren't already scoped.
     *
     * @param alarmBuilder the alarm builder to update
     * @param alarm the original alarm to derive from
     * @throws ScriptedInventoryException 
     */
    public synchronized void overrideTypeAndInstance(ImmutableAlarm.Builder alarmBuilder,
            org.opennms.integration.api.v1.model.Alarm alarm) throws ScriptedInventoryException {
        try {
            getInvocable().invokeFunction("overrideTypeAndInstance", alarmBuilder, alarm);
        } catch (NoSuchMethodException | ScriptException e) {
            throw new ScriptedInventoryException("Failed to override inventory for alarm", e);
        }
    }

    @SuppressWarnings("unchecked")
    public synchronized List<InventoryObject> createInventoryObjects(Alarm alarm) throws ScriptedInventoryException {
        try {
            return (List<InventoryObject>) getInvocable().invokeFunction("alarmToInventory", alarm);
        } catch (NoSuchMethodException | ScriptException e) {
            throw new ScriptedInventoryException("Failed to create inventory from alarm", e);
        }
    }

    @SuppressWarnings("unchecked")
    public synchronized List<InventoryObject> createInventoryObjects(Node node) throws ScriptedInventoryException {
        try {
            return (List<InventoryObject>) getInvocable().invokeFunction("nodeToInventory", node);
        } catch (NoSuchMethodException | ScriptException e) {
            throw new ScriptedInventoryException("Failed to create inventory from node", e);
        }
    }

}
