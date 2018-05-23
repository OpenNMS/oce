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
import java.util.Objects;

import org.opennms.oce.engine.api.entities.ObjectEntry;
import org.opennms.oce.engine.api.entities.PeerRef;
import org.opennms.oce.engine.api.entities.RelativeRef;

public class InventoryObjectEntry implements ObjectEntry {
    protected List<PeerRef> peerRef;
    protected List<RelativeRef> relativeRef;
    protected String id;
    protected String type;
    protected String subtype;
    protected String friendlyName;
    protected String parentType;
    protected String parentId;

    public InventoryObjectEntry() {}

    public InventoryObjectEntry(String type, String id, String subtype, String parentType, String parentId) {
        this.type = Objects.requireNonNull(type);
        this.id = Objects.requireNonNull(id);
        this.subtype = subtype;
        this.parentType = parentType;
        this.parentId = parentId;
    }

    @Override
    public void setPeerRef(List<PeerRef> peerRef) {
        this.peerRef = peerRef;
    }

    @Override
    public List<PeerRef> getPeerRef() {
        if (peerRef == null) {
            peerRef = new ArrayList<>();
        }
        return this.peerRef;
    }

    @Override
    public void setRelativeRef(List<RelativeRef> relativeRef) {
        this.relativeRef = relativeRef;
    }

    @Override
    public List<RelativeRef> getRelativeRef() {
        if (relativeRef == null) {
            relativeRef = new ArrayList<>();
        }
        return this.relativeRef;
    }

    @Override
    public String getId() {

        return id;
    }

    @Override
    public void setId(String value) {

        this.id = value;
    }

    @Override
    public String getType() {

        return type;
    }

    @Override
    public void setType(String value) {

        this.type = value;
    }

    @Override
    public String getSubtype() {

        return subtype;
    }

    @Override
    public void setSubtype(String value) {

        this.subtype = value;
    }

    @Override
    public String getFriendlyName() {

        return friendlyName;
    }

    @Override
    public void setFriendlyName(String value) {

        this.friendlyName = value;
    }

    @Override
    public String getParentType() {

        return parentType;
    }

    @Override
    public void setParentType(String value) {

        this.parentType = value;
    }

    @Override
    public String getParentId() {

        return parentId;
    }

    @Override
    public void setParentId(String value) {

        this.parentId = value;
    }
}
