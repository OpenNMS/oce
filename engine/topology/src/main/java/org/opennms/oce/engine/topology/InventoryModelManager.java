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

package org.opennms.oce.engine.topology;

import java.util.Map;
import java.util.stream.Collectors;

import org.opennms.oce.engine.api.*;
import org.opennms.oce.engine.api.entities.ObjectEntry;
import org.opennms.oce.engine.api.entities.PeerRef;
import org.opennms.oce.engine.api.entities.RelativeRef;
import org.opennms.oce.model.api.Model;
import org.opennms.oce.model.impl.ModelImpl;
import org.opennms.oce.model.impl.ModelObjectImpl;
import org.opennms.oce.model.impl.ModelObjectKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InventoryModelManager {
    private Inventory inventory;
    private Model model;

    private static final Logger LOG = LoggerFactory.getLogger(InventoryModelManager.class);

    public static final String MODEL_ROOT_TYPE = "Model";
    public static final String MODEL_ROOT_ID = "model";

    public InventoryModelManager() {
        inventory = new TopologyInventory();
    }
    /**
     * Initial load of inventory.
     */
    public InventoryModelManager(Inventory inventory) {
        this();
        loadInventory(inventory);
    }

    public void loadInventory(Inventory inventory) {
        this.inventory.appendInventory(inventory);

        if(model != null) {
            //If model exists but there is no root:
            if (model.getRoot() == null) {
                throw new IllegalStateException("Inventory must contain a single object of type '"
                        + MODEL_ROOT_TYPE + "' with id '" + MODEL_ROOT_ID + "'");
            }
            append(inventory);
            return;
        }

        // Create the initial model objects and index them by type/id
        // NOTE: This will throw a IllegalStateException if a duplicate key is found
        final Map<ModelObjectKey, ModelObjectImpl> mosByKey = inventory.getObjectEntryList().stream()
                .collect(Collectors.toMap(ioe -> ModelObjectKey.key(ioe.getType(), ioe.getId()), InventoryModelManager::toModelObject));

        // Create the root
        final ObjectEntry rootEntry = new InventoryObjectEntry();
        rootEntry.setType(MODEL_ROOT_TYPE);
        rootEntry.setId(MODEL_ROOT_ID);

        mosByKey.put(ModelObjectKey.key(MODEL_ROOT_TYPE, MODEL_ROOT_ID), new ModelObjectImpl(rootEntry.getType(), rootEntry.getId()));

        // Now build out the relationships
        inventory.getObjectEntryList().forEach(ioe -> {
            final ModelObjectKey key = ModelObjectKey.key(ioe.getType(), ioe.getId());
            final ModelObjectImpl mo = mosByKey.get(key);
            if (mo == null) {
                // Should not happen
                throw new IllegalStateException("Cannot find an MO with key: " + key);
            }

            if (MODEL_ROOT_TYPE.equals(mo.getType())) {
                // This is the root element, nothing else to do here
                return;
            }

            // Setup the parent
            final ModelObjectKey parentKey = ModelObjectKey.key(ioe.getParentType(), ioe.getParentId());
            final ModelObjectImpl parentMo = mosByKey.get(parentKey);
            if (parentMo == null) {
                throw new IllegalStateException("Cannot find parent MO with key: " + parentKey + " on MO with key: " + key);
            }
            mo.setParent(parentMo);
            parentMo.addChild(mo);

            // Setup the peers
            for (PeerRef peerRef : ioe.getPeerRef()) {
                final ModelObjectKey peerKey = ModelObjectKey.key(peerRef.getType(), peerRef.getId());
                final ModelObjectImpl peerMo = mosByKey.get(peerKey);
                if (peerMo == null) {
                    throw new IllegalStateException("Cannot find peer MO with key: " + peerKey + " on MO with key: " + key);
                }
                mo.addPeer(peerMo);
                peerMo.addPeer(mo);
            }

            // Setup the relatives
            for (RelativeRef relativeRef : ioe.getRelativeRef()) {
                final ModelObjectKey relativeKey = ModelObjectKey.key(relativeRef.getType(), relativeRef.getId());
                final ModelObjectImpl relativeMo = mosByKey.get(relativeKey);
                if (relativeMo == null) {
                    throw new IllegalStateException("Cannot find relative MO with key: " + relativeRef + " on MO with key: " + key);
                }
                mo.addUncle(relativeMo);
                relativeMo.addNephew(mo);
            }
        });

        // Create the root
        final ModelObjectImpl rootMo = mosByKey.get(ModelObjectKey.key(MODEL_ROOT_TYPE, MODEL_ROOT_ID));
        // Create a new model instance
        model = new ModelImpl(rootMo);
    }

    public Model getModel() {
        return model;
    }


    private void append(Inventory inventory) {

    }

    private static ModelObjectImpl toModelObject(ObjectEntry ioe) {
        final ModelObjectImpl mo = new ModelObjectImpl(ioe.getType(), ioe.getId());
        mo.setFriendlyName(ioe.getFriendlyName());
        return mo;
    }

    public void clean() {
        model = null;
        inventory = new TopologyInventory();
    }
}
