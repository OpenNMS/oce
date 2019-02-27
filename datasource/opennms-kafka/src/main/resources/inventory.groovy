
import org.opennms.oce.datasource.api.InventoryObject
import org.opennms.oce.datasource.common.ImmutableInventoryObject
import org.opennms.oce.datasource.common.inventory.ManagedObjectType;
import static org.opennms.oce.datasource.common.inventory.ManagedObjectType.*;

import org.opennms.oce.datasource.common.inventory.TypeToInventory;
import org.opennms.oce.datasource.opennms.EnrichedAlarm
import org.opennms.oce.datasource.opennms.InventoryFromAlarm
import org.opennms.oce.datasource.opennms.OpennmsMapper
import org.opennms.oce.datasource.opennms.proto.InventoryModelProtos
import org.opennms.oce.datasource.opennms.proto.OpennmsModelProtos

import com.google.common.base.Strings;

def  enrichAlarm(OpennmsModelProtos.Alarm alarm) {
           if (alarm == null) {
            return null;
        }

        String managedObjectInstance = null;
        String managedObjectType = null;

        final InventoryModelProtos.InventoryObjects.Builder iosBuilder = InventoryModelProtos.InventoryObjects.newBuilder();
        final InventoryModelProtos.InventoryObjects ios;
        if (!Strings.isNullOrEmpty(alarm.getManagedObjectType()) &&
                !Strings.isNullOrEmpty(alarm.getManagedObjectInstance())) {
            final InventoryFromAlarm inventoryFromAlarm = getInventoryFromAlarm(alarm);
            for (InventoryModelProtos.InventoryObject io : inventoryFromAlarm.getInventory()) {
                iosBuilder.addInventoryObject(io);
            }
            ios = iosBuilder.build();
            if (inventoryFromAlarm.getManagedObjectInstance() != null && inventoryFromAlarm.getManagedObjectType() != null) {
                managedObjectInstance = inventoryFromAlarm.getManagedObjectInstance();
                managedObjectType = inventoryFromAlarm.getManagedObjectType();
            } else if (ios.getInventoryObjectCount() > 0) {
                final InventoryModelProtos.InventoryObject io = ios.getInventoryObject(0);
                managedObjectInstance = io.getId();
                managedObjectType = io.getType();
            }
        } else {
            ios = iosBuilder.build();
        }

        if ((managedObjectInstance == null  || managedObjectType == null) && alarm.hasNodeCriteria()) {
            final String nodeCriteria = OpennmsMapper.toNodeCriteria(alarm.getNodeCriteria());
            managedObjectType = ManagedObjectType.Node.getName();
            managedObjectInstance = nodeCriteria;
        }

        return new EnrichedAlarm(alarm, ios, managedObjectType, managedObjectInstance);
}

def getInventoryFromAlarm(OpennmsModelProtos.Alarm alarm) {
        final List<InventoryModelProtos.InventoryObject> ios = new ArrayList<>();
        final ManagedObjectType type;
        try  {
            type = ManagedObjectType.fromName(alarm.getManagedObjectType());
        } catch (NoSuchElementException nse) {
            // TODO LOG.warn("Found unsupported type: {} with id: {}. Skipping.", alarm.getManagedObjectType(), alarm.getManagedObjectInstance());
            return new InventoryFromAlarm(ios);
        }

        final String nodeCriteria = OpennmsMapper.toNodeCriteria(alarm.getNodeCriteria());
        String managedObjectInstance = null;
        String managedObjectType = null;
        switch(type) {
            case Node:
                // Nothing to do here
                break;
            case SnmpInterfaceLink:
                ios.add(OpennmsMapper.fromInventory(TypeToInventory
                        .getSnmpInterfaceLink(alarm.getManagedObjectInstance())));
            break;
            case EntPhysicalEntity:
                ios.add(OpennmsMapper.fromInventory(TypeToInventory
                        .getEntPhysicalEntity(alarm.getManagedObjectInstance(), nodeCriteria)));
                break;
            case BgpPeer:
                ios.add(OpennmsMapper.fromInventory(TypeToInventory
                        .getBgpPeer(alarm.getManagedObjectInstance(), nodeCriteria)));
                break;
            case VpnTunnel:
                ios.add(OpennmsMapper.fromInventory(TypeToInventory
                        .getVpnTunnel(alarm.getManagedObjectInstance(), nodeCriteria)));
                break;
            default:
                managedObjectType = type.getName();
                // Scope the object id by node
                managedObjectInstance = String.format("%s:%s", nodeCriteria, alarm.getManagedObjectInstance());
        }
        return new InventoryFromAlarm(ios, managedObjectInstance, managedObjectType);
}

def toInventoryObject(OpennmsModelProtos.SnmpInterface snmpInterface, InventoryModelProtos.InventoryObject parent) {
    return InventoryModelProtos.InventoryObject.newBuilder()
            .setType(ManagedObjectType.SnmpInterface.getName())
            .setId(parent.getId() + ":" + snmpInterface.getIfIndex())
            .setFriendlyName(snmpInterface.getIfDescr())
            .setParentType(parent.getType())
            .setParentId(parent.getId())
            .build();
}

def toInventoryObjects(OpennmsModelProtos.Node node) {
    final List<InventoryModelProtos.InventoryObject> inventory = new ArrayList<>();
    
            InventoryModelProtos.InventoryObject nodeObj = InventoryModelProtos.InventoryObject.newBuilder()
                    .setType(ManagedObjectType.Node.getName())
                    .setId(OpennmsMapper.toNodeCriteria(node))
                    .setFriendlyName(node.getLabel())
                    .build();
            inventory.add(nodeObj);
    
            // Attach the SNMP interfaces directly to the node
            node.getSnmpInterfaceList().stream()
                    .map{iff -> toInventoryObject(iff, nodeObj)}
                    .forEach{i -> inventory.add(i)};
    
            // TODO: Use the hardware inventory data if available
    
            return inventory;
}

def toNodeCriteria(OpennmsModelProtos.Alarm alarm) {
// FIXME
}

def toNodeCriteria(Node node) {
// FIXME
}

def toNodeCriteria(String foreignSource, String foreignId, int id) {
// FIXME 
}
