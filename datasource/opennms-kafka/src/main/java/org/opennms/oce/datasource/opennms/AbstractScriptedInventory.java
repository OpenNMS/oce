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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.opennms.oce.datasource.common.inventory.script.OSGiScriptEngineManager;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.Files;

/**
 * @author smith
 *
 */
public abstract class AbstractScriptedInventory {

    protected static final Logger LOG = LoggerFactory.getLogger(ScriptedInventoryImpl.class);

    protected static final String DEFAULT_SCRIPT = "/inventory.groovy";

    private boolean usingDefaultScript;

    private BundleContext context;

    private Invocable invocable;

    private String scriptPath;

    private long configurationTimestamp;

    private long scriptFileTimestamp;

    public AbstractScriptedInventory(String scriptPath, BundleContext bundleContext) {
        if (scriptPath == null) {
            throw new IllegalArgumentException("Null value for scriptFile.");
        }

        String script;
        String scriptExtension;

        usingDefaultScript = true;

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
                scriptFileTimestamp = file.lastModified();
                LOG.info("Loaded script {} from {} with timestamp: {}", file, scriptPath, scriptFileTimestamp);
            } catch (IOException e) {
                throw new IllegalStateException("Reading reading script at '" + file + "'.");
            }
        }

        ScriptEngineManager manager;
        if (bundleContext == null) {
            manager = new ScriptEngineManager();
        } else {
            context = bundleContext;
            manager = new OSGiScriptEngineManager(context);
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
        configurationTimestamp = System.currentTimeMillis();
    }

    protected Invocable getInvocable() {
        if (configCacheExpired()) {
            refreshConfig();
        }
        return invocable;
    }

    /**
     * 
     */
    private void refreshConfig() {
        String scriptPath = getCurrentScriptpath();
        if (usingDefaultScript) {
            // if still using default script, carry on
            // if now using file script, attempt to evaluate.
        } else {
            // if name has changed or lastmodified is newer, attempt to re-evaluate
        }

        configurationTimestamp = System.currentTimeMillis();
    }

    private String getCurrentScriptpath() {
        // FIXME get the current value for scriptFile from the bundleContext
        return "";
    }

    // return TRUE if it's been more than 30 seconds since the config has been checked.
    private boolean configCacheExpired() {
        return System.currentTimeMillis() - configurationTimestamp > 30000;
    }

    /**
     * 
     */
    private Invocable getNewIvocable(String scriptPath) {
        try {
            // read the script from the file system
            this.scriptPath = scriptPath;

            File file = new File(scriptPath);
            if (!file.canRead()) {
                throw new IllegalStateException("Cannot read script at '" + file + "'.");
            }
            String script;
            String scriptExtension;

            try {
                byte[] fileBytes = java.nio.file.Files.readAllBytes(file.toPath());

                script = new String(fileBytes);
                scriptExtension = Files.getFileExtension(scriptPath);
                scriptFileTimestamp = file.lastModified();
                LOG.info("Loaded script {} from {} with timestamp: {}", file, scriptPath, scriptFileTimestamp);
            } catch (IOException e) {
                throw new IllegalStateException("Reading reading script at '" + file + "'.");
            }

            ScriptEngineManager manager = new OSGiScriptEngineManager(context);
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
            configurationTimestamp = System.currentTimeMillis();
            usingDefaultScript = false;

        } catch (Exception e) {
            LOG.error("Failed to update/evaluate script [{}]: {}", scriptPath, e.getMessage());
        }
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
