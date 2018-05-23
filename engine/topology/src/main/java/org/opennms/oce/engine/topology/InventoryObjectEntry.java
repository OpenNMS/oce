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

import java.util.ArrayList;
import java.util.List;

public class InventoryObjectEntry {
    protected List<InventoryPeerRef> peerRef;
    protected List<InventoryRelativeRef> relativeRef;
    protected String id;
    protected String type;
    protected String subtype;
    protected String friendlyName;
    protected String parentType;
    protected String parentId;

    public List<InventoryPeerRef> getPeerRef() {
        if (peerRef == null) {
            peerRef = new ArrayList<InventoryPeerRef>();
        }
        return this.peerRef;
    }

    public List<InventoryRelativeRef> getRelativeRef() {
        if (relativeRef == null) {
            relativeRef = new ArrayList<InventoryRelativeRef>();
        }
        return this.relativeRef;
    }

    public String getId() {

        return id;
    }

    public void setId(String value) {

        this.id = value;
    }

    public String getType() {

        return type;
    }

    public void setType(String value) {

        this.type = value;
    }

    public String getSubtype() {

        return subtype;
    }

    public void setSubtype(String value) {

        this.subtype = value;
    }

    public String getFriendlyName() {

        return friendlyName;
    }

    public void setFriendlyName(String value) {

        this.friendlyName = value;
    }

    public String getParentType() {

        return parentType;
    }

    public void setParentType(String value) {

        this.parentType = value;
    }

    public String getParentId() {

        return parentId;
    }

    public void setParentId(String value) {

        this.parentId = value;
    }
}
