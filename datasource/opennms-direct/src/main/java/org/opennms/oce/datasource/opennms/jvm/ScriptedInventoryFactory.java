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
import java.util.List;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.opennms.integration.api.v1.model.Alarm;
import org.opennms.oce.datasource.api.InventoryObject;

import com.google.common.io.Files;

/**
 * Uses an external script, executed via JSR-223, to generate a
 * {@link CollectionSet} from some given object using the
 * {@link CollectionSetBuilder}.
 *
 * @author jwhite
 */
public class ScriptedInventoryFactory {

    private final Invocable invocable;

    public ScriptedInventoryFactory(File script) throws IOException, ScriptException {
        this(script, new ScriptEngineManager());
    }

    /*
     * TODO
        public ScriptedInventoryFactory(File script, BundleContext bundleContext) throws IOException, ScriptException {
        this(script, new OSGiScriptEngineManager(bundleContext));
    }*/

    public ScriptedInventoryFactory(File script, ScriptEngineManager manager) throws IOException, ScriptException {
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

    @SuppressWarnings("unchecked")
    public List<InventoryObject> createInventoryObjects(Alarm alarm) throws ScriptException, NoSuchMethodException {
        return (List<InventoryObject>) invocable.invokeFunction("alarmToInventory", alarm);
    }

}
