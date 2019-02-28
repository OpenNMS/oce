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
import java.io.FileReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collection;
import java.util.List;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.opennms.oce.datasource.common.inventory.ManagedObjectType;
import org.opennms.oce.datasource.opennms.proto.InventoryModelProtos;
import org.opennms.oce.datasource.opennms.proto.OpennmsModelProtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.Files;

/**
 * @author smith
 *
 */
public class ScriptedInventoryFactory {

    private static final Logger LOG = LoggerFactory.getLogger(ScriptedInventoryFactory.class);

    private final Invocable invocable;

    private static String pathToScript;

    private static long timestamp;

    private static ScriptedInventoryFactory cached;

    public ScriptedInventoryFactory(File script) throws IOException, ScriptException {
        if (!script.canRead()) {
            throw new IllegalStateException("Cannot read script at '" + script + "'.");
        }

        final String ext = Files.getFileExtension(script.getAbsolutePath());

        ScriptEngineManager manager = new ScriptEngineManager();
        final ScriptEngine engine = manager.getEngineByExtension(ext);
        if (engine == null) {
            throw new IllegalStateException("No engine found for extension: " + ext);
        }

        engine.eval(new FileReader(script));
        invocable = (Invocable) engine;
    }

    public EnrichedAlarm enrichAlarm(OpennmsModelProtos.Alarm alarm) throws ScriptedInventoryException {
        try {
            return (EnrichedAlarm) invocable.invokeFunction("enrichAlarm", alarm);
        } catch (NoSuchMethodException | ScriptException e) {
            throw new ScriptedInventoryException("Failed enrichAlarm", e);
        }
    }

    public InventoryFromAlarm getInventoryFromAlarm(OpennmsModelProtos.Alarm alarm) throws ScriptedInventoryException {
        try {
            return (InventoryFromAlarm) invocable.invokeFunction("getInventoryFromAlarm", alarm);
        } catch (NoSuchMethodException | ScriptException e) {
            throw new ScriptedInventoryException("Failed getInventoryFromAlarm", e);
        }
    }

    public InventoryModelProtos.InventoryObject toInventoryObject(OpennmsModelProtos.SnmpInterface snmpInterface,
            InventoryModelProtos.InventoryObject parent) throws ScriptedInventoryException {
        try {
            return (InventoryModelProtos.InventoryObject) invocable.invokeFunction("toInventoryObject", snmpInterface, parent);
        } catch (NoSuchMethodException | ScriptException e) {
            throw new ScriptedInventoryException("Failed snmpInterface toInventoryObject", e);
        }
    }

    public Collection<InventoryModelProtos.InventoryObject> toInventoryObjects(OpennmsModelProtos.Node node) throws ScriptedInventoryException {
        try {
            return (List<InventoryModelProtos.InventoryObject>) invocable.invokeFunction("toInventoryObjects", node);
        } catch (NoSuchMethodException | ScriptException e) {
            throw new ScriptedInventoryException("Failed node toInventoryObjects", e);
        }
    }

    public Object createInventoryObjects(OpennmsModelProtos.SnmpInterface snmpInterface, InventoryModelProtos.InventoryObject parent) {
        // TODO - move this down to script....
        return InventoryModelProtos.InventoryObject.newBuilder()
                .setType(ManagedObjectType.SnmpInterface.getName())
                .setId(parent.getId() + ":" + snmpInterface.getIfIndex())
                .setFriendlyName(snmpInterface.getIfDescr())
                .setParentType(parent.getType())
                .setParentId(parent.getId())
                .build();
    }

    public static ScriptedInventoryFactory getFactory() throws ScriptedInventoryException {
        // TODO - parameterize
        String script = "inventory.groovy";
        // TODO - test for script being updated otherwise can return cached version.
        URL scriptUri = ClassLoader.getSystemResource(script);

        try {
            File file = new File(scriptUri.toURI());
            if (file.lastModified() <= timestamp && cached != null) {
                return cached;
            }
            LOG.info("Loading script {} from {} with timestamp: {}", script, scriptUri, file.lastModified());
            timestamp = file.lastModified();
            pathToScript = scriptUri.getPath();
            cached = new ScriptedInventoryFactory(file);

            return cached;
        } catch (URISyntaxException | IOException | ScriptException e) {
            LOG.error("Failed to retrieve ScriptInventoryFactory : {}", e.getMessage());
            throw new ScriptedInventoryException("Failed to retrieve ScriptInventoryFactory.", e);
        }
    }

}
