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

package org.opennms.oce.datasource.opennms.jvm;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.opennms.integration.api.v1.model.EventParameter;
import org.opennms.integration.api.v1.model.InMemoryEvent;
import org.opennms.integration.api.v1.model.Node;
import org.opennms.integration.api.v1.model.beans.InMemoryEventBean;
import org.opennms.oce.datasource.api.Alarm;
import org.opennms.oce.datasource.api.AlarmFeedback;
import org.opennms.oce.datasource.api.FeedbackType;
import org.opennms.oce.datasource.api.InventoryObject;
import org.opennms.oce.datasource.api.Severity;
import org.opennms.oce.datasource.api.Situation;
import org.opennms.oce.datasource.common.ImmutableAlarm;
import org.opennms.oce.datasource.common.ImmutableAlarmFeedback;
import org.opennms.oce.datasource.common.ImmutableSituation;
import org.opennms.oce.datasource.common.inventory.ManagedObjectType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Enums;
import com.google.common.base.Strings;

public class Mappers {
    private static final Logger LOG = LoggerFactory.getLogger(Mappers.class);
    public static final String SITUATION_UEI = "uei.opennms.org/alarms/situation";
    public static final String SITUATION_ID_PARM_NAME = "situationId";

    public static Alarm toAlarm(org.opennms.integration.api.v1.model.Alarm alarm) {
        ImmutableAlarm.Builder alarmBuilder = ImmutableAlarm.newBuilder();
        alarmBuilder
                .setId(alarm.getReductionKey())
                .setTime(alarm.getLastEventTime().getTime())
                .setSeverity(toSeverity(alarm.getSeverity()))
                .setInventoryObjectId(alarm.getManagedObjectInstance())
                .setInventoryObjectType(alarm.getManagedObjectType())
                .setSummary(alarm.getLogMessage())
                .setDescription(alarm.getDescription())
                .setNodeId(alarm.getNode() != null ? alarm.getNode().getId().longValue() : null);
        overrideTypeAndInstance(alarmBuilder, alarm);

        return alarmBuilder.build();
    }

    /**
     * Overrides the inventory type and instance for an alarm if they aren't already scoped.
     *
     * @param alarmBuilder the alarm builder to update
     * @param alarm the original alarm to derive from
     */
    private static void overrideTypeAndInstance(ImmutableAlarm.Builder alarmBuilder,
                                                org.opennms.integration.api.v1.model.Alarm alarm) {
        if (!Strings.isNullOrEmpty(alarm.getManagedObjectType()) &&
                !Strings.isNullOrEmpty(alarm.getManagedObjectInstance())) {
            ManagedObjectType type;

            try {
                type = ManagedObjectType.fromName(alarm.getManagedObjectType());
            } catch (NoSuchElementException nse) {
                LOG.warn("Found unsupported type: {} with id: {}. Skipping.", alarm.getManagedObjectType(),
                        alarm.getManagedObjectInstance());
                return;
            }

            Set<ManagedObjectType> alreadyScoped = new HashSet<>(Arrays.asList(
                    ManagedObjectType.Node,
                    ManagedObjectType.SnmpInterfaceLink,
                    ManagedObjectType.EntPhysicalEntity,
                    ManagedObjectType.BgpPeer,
                    ManagedObjectType.VpnTunnel
            ));

            if (!alreadyScoped.contains(type)) {
                alarmBuilder.setInventoryObjectType(type.getName());
                alarmBuilder.setInventoryObjectId(String.format("%s:%s", InventoryFactory.toNodeCriteria(alarm.getNode()),
                        alarm.getManagedObjectInstance()));
            }
        }

        if ((alarm.getManagedObjectType() == null || alarm.getManagedObjectInstance() == null) &&
                alarm.getNode() != null) {
            alarmBuilder.setInventoryObjectType(ManagedObjectType.Node.getName());
            alarmBuilder.setInventoryObjectId(alarm.getNode().getId().toString());
        }
    }

    public static Situation toSituation(org.opennms.integration.api.v1.model.Alarm alarm) {
        final String situationId;
        final Optional<String> situationIdFromAlarm = getSituationIdFromAlarm(alarm);
        if (situationIdFromAlarm.isPresent()) {
            situationId = situationIdFromAlarm.get();
        } else {
            LOG.warn("Could not find situationId on alarm: {}. Using the alarm id instead.");
            situationId = Integer.toString(alarm.getId());
        }

        return ImmutableSituation.newBuilder()
                .setId(situationId)
                .setCreationTime(alarm.getFirstEventTime().toInstant().toEpochMilli())
                .setSeverity(toSeverity(alarm.getSeverity()))
                .setAlarms(alarm.getRelatedAlarms().stream().map(Mappers::toAlarm).collect(Collectors.toSet()))
                .build();
    }

    private static Optional<String> getSituationIdFromAlarm(org.opennms.integration.api.v1.model.Alarm alarm) {
        final List<EventParameter> parms = alarm.getLastEvent().getParametersByName(SITUATION_ID_PARM_NAME);
        if (parms == null) {
            // No parameter with that name
            return Optional.empty();
        }
        return parms.stream()
                .map(EventParameter::getValue)
                .findFirst();
    }

    public static InMemoryEvent toEvent(Situation situation) {
        final InMemoryEventBean event = new InMemoryEventBean(SITUATION_UEI, "oce");

        // Use the max severity as the situation severity
        final Severity maxSeverity = Severity.fromValue(situation.getAlarms().stream()
                .mapToInt(a -> a.getSeverity() != null ? a.getSeverity().getValue() : Severity.INDETERMINATE.getValue())
                .max()
                .orElseGet(Severity.INDETERMINATE::getValue));
        event.setSeverity(toSeverity(maxSeverity));

        // Relay the situation id
        event.addParameter(SITUATION_ID_PARM_NAME, situation.getId());

        // Use the log message and description from the first (earliest) alarm
        final Alarm earliestAlarm = situation.getAlarms().stream()
                .min(Comparator.comparing(Alarm::getTime))
                .orElse(null);
        if (earliestAlarm != null) {
            event.addParameter("situationLogMsg", earliestAlarm.getSummary());

            String description = earliestAlarm.getDescription();
            if (situation.getDiagnosticText() != null) {
                description += "\n<p>OCE Diagnostic: " + situation.getDiagnosticText() + "</p>";
            }
            event.addParameter("situationDescr", description);
        }

        // Set the related reduction keys
        final AtomicInteger alarmIndex = new AtomicInteger(0);
        situation.getAlarms().stream()
                .map(Alarm::getId)
                // Append a unique index to each related-reductionKey parameter since the underlying event builder
                // does not support many parameters with the same name. Alarmd will associate the related alarm
                // provided that the parameter name *starts with* 'related-reductionKey'
                .forEach(reductionKey -> event.addParameter("related-reductionKey" + alarmIndex.incrementAndGet(),
                        reductionKey));

        return event;
    }

    public static org.opennms.integration.api.v1.model.Severity toSeverity(Severity severity) {
        if (severity == null) {
            return null;
        }
        switch(severity) {
            case CRITICAL:
                return org.opennms.integration.api.v1.model.Severity.CRITICAL;
            case MAJOR:
                return org.opennms.integration.api.v1.model.Severity.MAJOR;
            case MINOR:
                return org.opennms.integration.api.v1.model.Severity.MINOR;
            case WARNING:
                return org.opennms.integration.api.v1.model.Severity.WARNING;
            case NORMAL:
                return org.opennms.integration.api.v1.model.Severity.NORMAL;
            case CLEARED:
                return org.opennms.integration.api.v1.model.Severity.CLEARED;
        }
        return org.opennms.integration.api.v1.model.Severity.INDETERMINATE;
    }

    public static Severity toSeverity(org.opennms.integration.api.v1.model.Severity severity) {
        if (severity == null) {
            return null;
        }
        switch(severity) {
            case CLEARED:
                return Severity.CLEARED;
            case NORMAL:
                return Severity.NORMAL;
            case WARNING:
                return Severity.WARNING;
            case MINOR:
                return Severity.MINOR;
            case MAJOR:
                return Severity.MAJOR;
            case CRITICAL:
                return Severity.CRITICAL;
        }
        return Severity.INDETERMINATE;
    }

    public static List<InventoryObject> toInventory(Node node) {
        return InventoryFactory.createInventoryObjects(node);
    }

    public static List<InventoryObject> toInventory(org.opennms.integration.api.v1.model.Alarm alarm) {
        return InventoryFactory.createInventoryObjects(alarm);
    }

    public static AlarmFeedback toAlarmFeedback(org.opennms.integration.api.v1.model.AlarmFeedback alarmFeedback) {
        return ImmutableAlarmFeedback.newBuilder()
                .setSituationKey(alarmFeedback.getSituationKey())
                .setSituationFingerprint(alarmFeedback.getSituationFingerprint())
                .setAlarmKey(alarmFeedback.getAlarmKey())
                .setReason(alarmFeedback.getReason())
                .setFeedbackType(Enums.getIfPresent(FeedbackType.class, alarmFeedback.getFeedbackType().toString()).get())
                .setUser(alarmFeedback.getUser())
                .setTimestamp(alarmFeedback.getTimestamp())
                .build();
    }
}
