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

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;

import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.opennms.integration.api.v1.model.Alarm;
import org.opennms.integration.api.v1.model.Node;
import org.opennms.oce.datasource.api.InventoryObject;
import org.opennms.oce.datasource.common.ImmutableAlarm;
import org.opennms.oce.datasource.common.ScriptedInventoryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.Files;

/**
 * Uses an external script, executed via JSR-223, to generate a
 * {@link CollectionSet} from some given object using the
 * {@link CollectionSetBuilder}.
 *
 * @author jwhite
 */
public class ScriptedInventoryImpl implements ScriptedInventoryService {

    private static final Logger LOG = LoggerFactory.getLogger(ScriptedInventoryImpl.class);

    private final Invocable invocable;

    private String scriptPath;

    private long timestamp;

    public ScriptedInventoryImpl(String scriptPath) throws IOException, ScriptException, URISyntaxException {
        this.scriptPath = scriptPath;

        URL scriptUri = ClassLoader.getSystemResource(scriptPath);

        File file = new File(scriptUri.toURI());
        if (!file.canRead()) {
            throw new IllegalStateException("Cannot read script at '" + file + "'.");
        }
        LOG.info("Loading script {} from {} with timestamp: {}", file, scriptUri, file.lastModified());
        timestamp = file.lastModified();

        //        } catch (URISyntaxException | IOException | ScriptException e) {
        //            LOG.error("Failed to retrieve ScriptInventoryFactory : {}", e.getMessage());
        //            throw new ScriptedInventoryException("Failed to retrieve ScriptInventoryFactory.", e);
        //        }

        final String ext = Files.getFileExtension(file.getAbsolutePath());

        ScriptEngineManager manager = new ScriptEngineManager();
        final ScriptEngine engine = manager.getEngineByExtension(ext);
        if (engine == null) {
            throw new IllegalStateException("No engine found for extension: " + ext);
        }

        engine.eval(new FileReader(file));
        timestamp = file.lastModified();

        javax.script.SimpleBindings globals = (javax.script.SimpleBindings) engine.getBindings(ScriptContext.GLOBAL_SCOPE);
        javax.script.SimpleBindings engines = (javax.script.SimpleBindings) engine.getBindings(ScriptContext.ENGINE_SCOPE);

        LOG.info("GLOBAL Bindings: {}", globals);
        LOG.info("ENGINE Bindings: {}", engines);
        invocable = (Invocable) engine;
    }

    private ScriptedInventoryImpl(File script, ScriptEngineManager manager) throws IOException, ScriptException {
        if (!script.canRead()) {
            throw new IllegalStateException("Cannot read script at '" + script + "'.");
        }

        final String ext = Files.getFileExtension(script.getAbsolutePath());

        final ScriptEngine engine = manager.getEngineByExtension(ext);
        if (engine == null) {
            throw new IllegalStateException("No engine found for extension: " + ext);
        }

        engine.eval(new FileReader(script));
        invocable = (Invocable) engine;
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
        return true;
    }

}
