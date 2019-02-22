
import org.opennms.oce.datasource.api.InventoryObject
import org.opennms.oce.datasource.common.ImmutableInventoryObject
import org.opennms.oce.datasource.common.inventory.ManagedObjectType;
import static org.opennms.oce.datasource.common.inventory.ManagedObjectType.*;

import org.opennms.integration.api.v1.model.Alarm
import org.opennms.integration.api.v1.model.Node
import org.opennms.integration.api.v1.model.SnmpInterface
import org.opennms.oce.datasource.common.inventory.TypeToInventory;

import com.google.common.base.Strings;


// @Slf4j

def nodeToInventory(node) {
    List<InventoryObject> inventoryObjects = new ArrayList<>();

    InventoryObject inventoryObject = ImmutableInventoryObject.newBuilder()
            .setType(ManagedObjectType.Node.getName())
            .setId(toNodeCriteria(node))
            .setFriendlyName(node.getLabel())
            .build();
    inventoryObjects.add(inventoryObject);

    // Attach the SNMP interfaces directly to the node
    node.getSnmpInterfaces().stream()
            .map{snmpInterface -> toInventoryObject(snmpInterface, inventoryObject)}
            .forEach{io -> inventoryObjects.add(io)};

    return inventoryObjects;
}

def alarmToInventory(alarm) {
    // Only derive inventory if the alarm has an MO type and instance
    if (Strings.isNullOrEmpty(alarm.getManagedObjectType()) ||
    Strings.isNullOrEmpty(alarm.getManagedObjectInstance())) {
        return Collections.emptyList();
    }

    // log.warn("ALARM TO INVENTORY:  type: {} with id: {}.", alarm.getManagedObjectType(), alarm.getManagedObjectInstance());

    ManagedObjectType type;

    try {
        type = ManagedObjectType.fromName(alarm.getManagedObjectType());
    } catch (NoSuchElementException nse) {
        LOG.warn("Found unsupported type: {} with id: {}. Skipping.", alarm.getManagedObjectType(),
                alarm.getManagedObjectInstance());
        return Collections.emptyList();
    }

    switch (type) {
        case SnmpInterfaceLink:
            return Collections.singletonList(TypeToInventory.getSnmpInterfaceLink(
            alarm.getManagedObjectInstance()));
        case EntPhysicalEntity:
            return Collections.singletonList(TypeToInventory.getEntPhysicalEntity(
            alarm.getManagedObjectInstance(), toNodeCriteria(alarm)));
        case BgpPeer:
            return Collections.singletonList(TypeToInventory.getBgpPeer(alarm.getManagedObjectInstance(),
            toNodeCriteria(alarm)));
        case VpnTunnel:
            return Collections.singletonList(TypeToInventory.getVpnTunnel(alarm.getManagedObjectInstance(),
            toNodeCriteria(alarm)));
        default:
            return Collections.emptyList();
    }
}

def toInventoryObject(SnmpInterface snmpInterface, InventoryObject parent) {
    return ImmutableInventoryObject.newBuilder()
            .setType(ManagedObjectType.SnmpInterface.getName())
            .setId(parent.getId() + ":" + snmpInterface.getIfIndex())
            .setFriendlyName(snmpInterface.getIfDescr())
            .setParentType(parent.getType())
            .setParentId(parent.getId())
            .build();
}

def toNodeCriteria(Alarm alarm) {
    Node node = alarm.getNode();

    if (node != null) {
        return toNodeCriteria(node);
    }

    return toNodeCriteria(null, null, alarm.getId());
}

def toNodeCriteria(Node node) {
    return toNodeCriteria(node.getForeignSource(), node.getForeignId(), node.getId());
}

def toNodeCriteria(String foreignSource, String foreignId, int id) {
    if (!Strings.isNullOrEmpty(foreignSource) && !Strings.isNullOrEmpty(foreignId)) {
        return foreignSource + ":" + foreignId;
    } else {
        return Long.valueOf(id).toString();
    }
}
