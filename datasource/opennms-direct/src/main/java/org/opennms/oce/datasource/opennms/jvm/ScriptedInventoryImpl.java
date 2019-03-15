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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.opennms.integration.api.v1.model.Alarm;
import org.opennms.integration.api.v1.model.Node;
import org.opennms.oce.datasource.api.InventoryObject;
import org.opennms.oce.datasource.common.ImmutableAlarm;
import org.opennms.oce.datasource.common.ScriptedInventoryException;
import org.opennms.oce.datasource.common.inventory.script.OSGiScriptEngineManager;
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

    private final Invocable invocable;

    private String scriptPath;

    private long timestamp;

    public ScriptedInventoryImpl(String scriptPath) {
        this(scriptPath, new ScriptEngineManager());
    }

    public ScriptedInventoryImpl(String scriptPath, BundleContext bundleContext) {
        this(scriptPath, new OSGiScriptEngineManager(bundleContext));
    }

    public ScriptedInventoryImpl(String scriptPath, ScriptEngineManager manager) {

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
            this.scriptPath = "/inventory.groovy";
            scriptExtension = "groovy";
            LOG.info("Loaded inventory.groovy from the classpath");
        } else {
            // read the script from the file system
            this.scriptPath = scriptPath;

            URL scriptUri = ScriptedInventoryImpl.class.getResource(scriptPath);

            if (scriptUri == null) {
                throw new IllegalArgumentException("Cannot find script : '" + scriptPath);
            }

            File file;
            try {
                file = new File(scriptUri.toURI());
            } catch (URISyntaxException e1) {
                throw new IllegalArgumentException("Invalid script URL: '" + scriptUri + "'.");

            }
            if (!file.canRead()) {
                throw new IllegalStateException("Cannot read script at '" + file + "'.");
            }
            try {
                Path path = Paths.get(scriptUri.toURI());
                byte[] fileBytes = java.nio.file.Files.readAllBytes(path);
                script = new String(fileBytes);
                scriptExtension = Files.getFileExtension(scriptPath);
                timestamp = file.lastModified();
                LOG.info("Loaded script {} from {} with timestamp: {}", file, path, timestamp);
            } catch (URISyntaxException | IOException e) {
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

    private Invocable getInvocable() throws ScriptedInventoryException {
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
        if (scriptPath.equals(scriptPath)) {
            // TODO read timestamp
        }
        if (0 > timestamp) {
            return false;
        }
        return true;
    }

}
