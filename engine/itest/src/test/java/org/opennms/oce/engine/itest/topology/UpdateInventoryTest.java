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

package org.opennms.oce.engine.itest.topology;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.IsNull.notNullValue;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opennms.oce.engine.api.entities.ObjectEntry;
import org.opennms.oce.engine.topology.InventoryModelManager;
import org.opennms.oce.engine.topology.InventoryObjectEntry;
import org.opennms.oce.engine.topology.InventoryPeerRef;
import org.opennms.oce.engine.topology.TopologyEngineFactory;
import org.opennms.oce.engine.topology.TopologyInventory;
import org.opennms.oce.model.api.Model;
import org.opennms.oce.model.api.ModelObject;

public class UpdateInventoryTest {

    TopologyEngineFactory topologyEngineFactory;
    InventoryModelManager inventoryManager;
    Model model;

    @Rule
    public ExpectedException exceptionGrabber = ExpectedException.none();

    @Before
    public void setUp() {
        topologyEngineFactory = new TopologyEngineFactory();
        inventoryManager = new InventoryModelManager();
        TopologyInventory inventory = new TopologyInventory();
        inventoryManager.loadInventory(inventory);
    }

    @Test
    public void canBeEmptyModelButHaveRoot() {
        model = inventoryManager.getModel();
        ModelObject root = model.getRoot();

        assertThat(root.getChildren(), hasSize(0));
        assertThat(root.getType(), is("Model"));
    }

    @Test
    public void canCleanModel() {
        model = inventoryManager.getModel();
        ModelObject root = model.getRoot();
        assertThat(root.getChildren(), hasSize(0));
        assertThat(root.getType(), is("Model"));

        inventoryManager.clean();
        inventoryManager.loadInventory(new TopologyInventory());

        model = inventoryManager.getModel();
        root = model.getRoot();

        assertThat(root.getChildren(), hasSize(0));
        assertThat(root.getType(), is("Model"));
    }

    @Test
    public void canLoadSimpleInventory() {
        inventoryManager.clean();

        TopologyInventory inventory = new TopologyInventory();
        ObjectEntry obj = new InventoryObjectEntry("Device", "n1", null, "Model", "model");
        inventory.addObject(obj);
        inventoryManager.loadInventory(inventory);

        model = inventoryManager.getModel();
        ModelObject root = model.getRoot();

        assertThat(root.getChildren(), hasSize(1));
        assertThat(root.getType(), is("Model"));
    }

    @Test
    public void canLoadDefaultInventory() {
        inventoryManager.clean();

        TopologyInventory inventory = new TopologyInventory();
        ObjectEntry objDevice = new InventoryObjectEntry("Device", "n1", null, "Model", "model");
        inventory.addObject(objDevice);
        ObjectEntry objCard = new InventoryObjectEntry("Card", "n1-c1", null, "Device", "n1");
        inventory.addObject(objCard);
        ObjectEntry objPort1 = new InventoryObjectEntry("Port", "n1-c1-p1", null, "Card", "n1-c1");
        inventory.addObject(objPort1);
        ObjectEntry objPort2 = new InventoryObjectEntry("Port", "n1-c1-p2", null, "Card", "n1-c1");
        inventory.addObject(objPort2);
        inventoryManager.loadInventory(inventory);

        model = inventoryManager.getModel();
        ModelObject root = model.getRoot();

        assertThat(root.getChildren(), hasSize(1));
        assertThat(root.getChildren(), hasSize(1));
        assertThat(root.getType(), is("Model"));
        assertThat(model.getObjectById("Device", "n1"), notNullValue());
        assertThat(model.getObjectById("Card", "n1-c1"), notNullValue());
        assertThat(model.getObjectById("Port", "n1-c1-p1"), notNullValue());
        assertThat(model.getObjectById("Port", "n1-c1-p2"), notNullValue());
    }

    @Test
    public void canLoadInventoryWithTopology_plus_Link() {
        inventoryManager.clean();

        TopologyInventory inventory = new TopologyInventory();

        ObjectEntry objDevice1 = new InventoryObjectEntry("Device", "n1", null, "Model", "model");
        inventory.addObject(objDevice1);
        ObjectEntry objDevice2 = new InventoryObjectEntry("Device", "n2", null, "Model", "model");
        inventory.addObject(objDevice2);

        ObjectEntry objCard1 = new InventoryObjectEntry("Card", "n1-c1", null, "Device", "n1");
        inventory.addObject(objCard1);
        ObjectEntry objCard2 = new InventoryObjectEntry("Card", "n1-c2", null, "Device", "n1");
        inventory.addObject(objCard2);
        ObjectEntry objCard3 = new InventoryObjectEntry("Card", "n2-c1", null, "Device", "n2");
        inventory.addObject(objCard3);

        ObjectEntry objPort1 = new InventoryObjectEntry("Port", "n1-c1-p1", null, "Card", "n1-c1");
        inventory.addObject(objPort1);
        ObjectEntry objPort2 = new InventoryObjectEntry("Port", "n1-c1-p2", null, "Card", "n1-c1");
        inventory.addObject(objPort2);
        ObjectEntry objPort3 = new InventoryObjectEntry("Port", "n1-c1-p3", null, "Card", "n1-c1");
        inventory.addObject(objPort3);
        ObjectEntry objPort4 = new InventoryObjectEntry("Port", "n1-c1-p4", null, "Card", "n1-c1");
        inventory.addObject(objPort4);

        ObjectEntry objPort11 = new InventoryObjectEntry("Port", "n1-c2-p1", null, "Card", "n1-c2");
        inventory.addObject(objPort11);
        ObjectEntry objPort12 = new InventoryObjectEntry("Port", "n1-c2-p2", null, "Card", "n1-c2");
        inventory.addObject(objPort12);

        ObjectEntry objPort21 = new InventoryObjectEntry("Port", "n2-c1-p1", null, "Card", "n2-c1");
        inventory.addObject(objPort21);
        ObjectEntry objPort22 = new InventoryObjectEntry("Port", "n2-c1-p2", null, "Card", "n2-c1");
        inventory.addObject(objPort22);

        ObjectEntry objLink = new InventoryObjectEntry("Link", "n1-c1-p1 <-> n2-c1-p1", null);
        objLink.setPeerRef(new InventoryPeerRef("PORT", "n1-c1-p1", "A"));
        objLink.setPeerRef(new InventoryPeerRef("PORT", "n2-c1-p1", "Z"));
        inventory.addObject(objLink);

        inventoryManager.loadInventory(inventory);

        model = inventoryManager.getModel();
        ModelObject root = model.getRoot();

        assertThat(root.getChildren(), hasSize(1));
        assertThat(root.getType(), is("Model"));

        assertThat(model.getObjectById("Device", "n1"), notNullValue());
        assertThat(model.getObjectById("Card", "n1-c1"), notNullValue());
        assertThat(model.getObjectById("Card", "n1-c2"), notNullValue());
        assertThat(model.getObjectById("Port", "n1-c1-p1"), notNullValue());
        assertThat(model.getObjectById("Port", "n1-c1-p2"), notNullValue());
        assertThat(model.getObjectById("Port", "n1-c1-p3"), notNullValue());
        assertThat(model.getObjectById("Port", "n1-c1-p4"), notNullValue());


    }
}
