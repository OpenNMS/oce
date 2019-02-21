

    def alarmToInventory(alarm) {
        // Only derive inventory if the alarm has an MO type and instance
        if (Strings.isNullOrEmpty(alarm.getManagedObjectType()) ||
                Strings.isNullOrEmpty(alarm.getManagedObjectInstance())) {
            return Collections.emptyList();
        }

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
    