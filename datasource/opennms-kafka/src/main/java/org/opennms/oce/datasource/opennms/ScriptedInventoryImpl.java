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

package org.opennms.oce.datasource.opennms;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.List;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.opennms.oce.datasource.common.ScriptedInventoryException;
import org.opennms.oce.datasource.common.inventory.script.OSGiScriptEngineManager;
import org.opennms.oce.datasource.opennms.proto.InventoryModelProtos;
import org.opennms.oce.datasource.opennms.proto.InventoryModelProtos.InventoryObjects;
import org.opennms.oce.datasource.opennms.proto.OpennmsModelProtos;
import org.opennms.oce.datasource.opennms.proto.OpennmsModelProtos.TopologyEdge;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.Files;

/**
 * @author smith
 *
 */
public class ScriptedInventoryImpl implements ScriptedInventoryService {

    private static final Logger LOG = LoggerFactory.getLogger(ScriptedInventoryImpl.class);

    private static final String DEFAULT_SCRIPT = "/inventory.groovy";

    private Invocable invocable;

    private String scriptPath;

    private long timestamp;

    public ScriptedInventoryImpl(String scriptPath) {
        this(scriptPath, new ScriptEngineManager());
    }

    public ScriptedInventoryImpl(String scriptPath, BundleContext bundleContext) {
        this(scriptPath, new OSGiScriptEngineManager(bundleContext));
    }

    public ScriptedInventoryImpl(String scriptPath, ScriptEngineManager manager) {
        if (scriptPath == null) {
            throw new IllegalArgumentException("Null value for scriptFile.");
        }

        String script;
        String scriptExtension;

        if (scriptPath.isEmpty()) {
            // load default from classpath
            InputStream inputStream = ScriptedInventoryImpl.class.getResourceAsStream(DEFAULT_SCRIPT);
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            } catch (IOException e) {
                throw new IllegalArgumentException("Cannot find script in classpath : " + e.getMessage());
            }
            script = sb.toString();
            this.scriptPath = DEFAULT_SCRIPT;
            scriptExtension = "groovy";
            LOG.info("Loaded inventory.groovy from the classpath");
        } else {
            // read the script from the file system
            this.scriptPath = scriptPath;

            File file = new File(scriptPath);
            if (!file.canRead()) {
                throw new IllegalStateException("Cannot read script at '" + file + "'.");
            }
            try {
                byte[] fileBytes = java.nio.file.Files.readAllBytes(file.toPath());
                script = new String(fileBytes);
                scriptExtension = Files.getFileExtension(scriptPath);
                timestamp = file.lastModified();
                LOG.info("Loaded script {} from {} with timestamp: {}", file, scriptPath, timestamp);
            } catch (IOException e) {
                throw new IllegalStateException("Reading reading script at '" + file + "'.");
            }
        }

        final ScriptEngine engine = manager.getEngineByExtension(scriptExtension);
        if (engine == null) {
            throw new IllegalStateException("No engine found for extension: " + scriptExtension);
        }

        try {
            engine.eval(script);
        } catch (ScriptException e) {
            throw new IllegalStateException("Failed to eval() script file - " + this.scriptPath, e);
        }

        invocable = (Invocable) engine;
    }

    public void init() {
        LOG.info("ScriptedInventoryImpl init'd");
    }

    @Override
    public InventoryObjects edgeToInventory(TopologyEdge edge) throws ScriptedInventoryException {
        try {
            return (InventoryObjects) invocable.invokeFunction("edgeToInventory", edge);
        } catch (NoSuchMethodException | ScriptException e) {
            throw new ScriptedInventoryException("Failed getInventoryFromAlarm", e);
        }
    }

    @Override
    public EnrichedAlarm enrichAlarm(OpennmsModelProtos.Alarm alarm) throws ScriptedInventoryException {
        try {
            return (EnrichedAlarm) invocable.invokeFunction("enrichAlarm", alarm);
        } catch (NoSuchMethodException | ScriptException e) {
            throw new ScriptedInventoryException("Failed enrichAlarm", e);
        }
    }

    @Override
    public InventoryFromAlarm getInventoryFromAlarm(OpennmsModelProtos.Alarm alarm) throws ScriptedInventoryException {
        try {
            return (InventoryFromAlarm) invocable.invokeFunction("getInventoryFromAlarm", alarm);
        } catch (NoSuchMethodException | ScriptException e) {
            throw new ScriptedInventoryException("Failed getInventoryFromAlarm", e);
        }
    }

    @Override
    public InventoryModelProtos.InventoryObject toInventoryObject(OpennmsModelProtos.SnmpInterface snmpInterface,
            InventoryModelProtos.InventoryObject parent) throws ScriptedInventoryException {
        try {
            return (InventoryModelProtos.InventoryObject) invocable.invokeFunction("toInventoryObject", snmpInterface, parent);
        } catch (NoSuchMethodException | ScriptException e) {
            throw new ScriptedInventoryException("Failed snmpInterface toInventoryObject", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Collection<InventoryModelProtos.InventoryObject> toInventoryObjects(OpennmsModelProtos.Node node) throws ScriptedInventoryException {
        try {
            return (List<InventoryModelProtos.InventoryObject>) invocable.invokeFunction("toInventoryObjects", node);
        } catch (NoSuchMethodException | ScriptException e) {
            throw new ScriptedInventoryException("Failed node toInventoryObjects", e);
        }
    }

    private Invocable getInvocable() {
        if (cachedIsLatest()) {
            return invocable;
        } else {
            return getNewIvocable();
        }
    }

    /**
     * 
     */
    private Invocable getNewIvocable() {
        return invocable;
        // TODO == refresh script

    }

    private boolean cachedIsLatest() {
        // TODO return false if script on disc has been updated
        // TODO - test for script being updated otherwise can return cached version.

        /*if (file.lastModified() <= timestamp && cached != null) {
            return cached.i;
        }
        */
        return true;
    }

}
