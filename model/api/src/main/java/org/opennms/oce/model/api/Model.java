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

package org.opennms.oce.model.api;

import java.util.Map;
import java.util.Set;

public interface Model {

    ModelObject getObjectById(String type, String id);

    /**
     * If type does not exist, return null
     */
    Map<String, ModelObject> getObjectsByIdForType(String type);

    Set<String> getTypes();

    /**
     * The root of the model tree.
     */
    ModelObject getRoot();

    int getSize();

    void printModel();

    void addObject(String type, String id, String parentType, String parentId);

    /**
     * There could be one or two peers (eg link with one port only, link with two ports)
     */
    void addPeerRelation(String type, String id, String typePeer1, String idPeer1, String typePeer2, String idPeer2);

    void addRelativeRelation(String type, String id, String relativeType, String relativeId);

    void removeObjectById(String type, String id);

    void removePeerRelation(String type, String id, String typePeer1, String idPeer1, String typePeer2, String idPeer2);

    void removeRelativeRelation(String type, String id, String relativeType, String relativeId);
}
