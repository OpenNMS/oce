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

package org.opennms.oce.datasource.common.inventory;


import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.Objects;

public enum ManagedObjectType {
    Node("node"),
    SnmpInterface("snmp-interface"),
    /**
     * A link between two interfaces or an interface and a node.
     */
    SnmpInterfaceLink("snmp-interface-link"),
    BgpPeer("bgp-peer"),
    VpnTunnel("vpn-tunnel"),
    MplsL3Vrf("mpls-l3-vrf"),
    EntPhysicalEntity("ent-physical-entity"),
    OspfRouter("ospf-router"),
    MplsTunnel("mpls-tunnel"),
    MplsLdpSession("mpls-ldp-session"),
    /**
     * A L2 segment or a bridge device.
     */
    BridgeSegment("bridge-segment"),
    /**
     * A link between two bridges or a segment and a port.
     */
    BridgeLink("bridge-link"),
    /**
     * A link directly between two nodes.
     */
    NodeLink("node-link");

    private final String name;

    ManagedObjectType(String name) {
        this.name = Objects.requireNonNull(name);
    }

    public String getName() {
        return name;
    }

    public static ManagedObjectType fromName(String name) {
        return Arrays.stream(ManagedObjectType.values())
                .filter(mot -> Objects.equals(name, mot.getName()))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("No type found with name: " + name));
    }
}
