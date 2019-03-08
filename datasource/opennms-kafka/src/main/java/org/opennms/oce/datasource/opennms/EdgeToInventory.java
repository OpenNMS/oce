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

import org.opennms.oce.datasource.common.ScriptedInventoryException;
import org.opennms.oce.datasource.opennms.proto.InventoryModelProtos;
import org.opennms.oce.datasource.opennms.proto.OpennmsModelProtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

public class EdgeToInventory {

    private static final Logger LOG = LoggerFactory.getLogger(EdgeToInventory.class);

	private final ScriptedInventoryService inventoryService;
    
    // Note: only port is supported as a target right now
        switch (edge.getTargetCase()) {
            case TARGETPORT:
                targetIfIndex = edge.getTargetPort().getIfIndex();
                targetNodeCriteria = OpennmsMapper.toNodeCriteria(edge.getTargetPort().getNodeCriteria());
                break;
            case TARGETSEGMENT:
                // Segment support needs to be added when segments are available
            default:
                throw new UnsupportedOperationException("Unsupported target type + " + edge.getTargetCase());
        }

        String protocol = edge.getRef().getProtocol().name();
        String sourceNodeCriteria = OpennmsMapper.toNodeCriteria(edge.getSource().getNodeCriteria());

        // Create a link object by setting the peers to the source and target
        ioBuilder.setType(ManagedObjectType.SnmpInterfaceLink.getName())
                // The Id for this link will incorporate the protocol so that if multiple protocols describe a link 
                // between the same endpoints they will create multiple links (one for each protocol)
                .setId(getIdForEdge(edge))
                .setFriendlyName(String.format("SNMP Interface Link Between %d on %s and %d on %s discovered with " +
                                "protocol %s", edge.getSource().getIfIndex(), sourceNodeCriteria, targetIfIndex,
                        targetNodeCriteria, protocol))
                .addPeer(InventoryModelProtos.InventoryObjectPeerRef.newBuilder()
                        .setEndpoint(InventoryModelProtos.InventoryObjectPeerEndpoint.A)
                        .setId(String.format("%s:%d", sourceNodeCriteria,
                                edge.getSource().getIfIndex()))
                        .setType(ManagedObjectType.SnmpInterface.getName())
                        .build())
                .addPeer(InventoryModelProtos.InventoryObjectPeerRef.newBuilder()
                        .setEndpoint(InventoryModelProtos.InventoryObjectPeerEndpoint.Z)
                        .setId(String.format("%s:%d", targetNodeCriteria,
                                targetIfIndex))
                        .setType(ManagedObjectType.SnmpInterface.getName())
                        .build())
                .build();

    public EdgeToInventory(ScriptedInventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    public InventoryModelProtos.InventoryObjects toInventoryObjects(OpennmsModelProtos.TopologyEdge edge) {
        try {
            return inventoryService.edgeToInventory(edge);
        } catch (ScriptedInventoryException e) {
            LOG.error("Failed to get Inventory for Edge: {} : {}", edge, e.getLocalizedMessage());
            throw new RuntimeException(e);
        }
    }

    @VisibleForTesting
    static String getIdForEdge(OpennmsModelProtos.TopologyEdge edge) {
        return String.format("%s:%s:%d:%s:%d", edge.getRef().getProtocol(),
                OpennmsMapper.toNodeCriteria(edge.getSource().getNodeCriteria()), edge.getSource().getIfIndex(),
                OpennmsMapper.toNodeCriteria(edge.getTargetPort().getNodeCriteria()), edge.getTargetPort().getIfIndex());
    }
}
