# Installation Guide

## Overview

This guide walks through the steps necessary to get OCE up and running with OpenNMS Horizon.

We'll focus on getting OCE deployed in a Sentinel container using the OpenNMS Kafka datasource.

## Prequisites

* RHEL/CentOS 7.x or greater
* Java 8
* An instance OpenNMS Horizon 24.0.0
* A Kafka broker (or cluster) running Kafka 0.11.x or greater

## OpenNMS Configuration

### Enable Syslogd

Enable Syslogd in `$OPENNMS_HOME/etc/service-configuration.xml`.

Update Syslogd to use the `org.opennms.netmgt.syslogd.RadixTreeParser` in `$OPENNMS_HOME/etc/etc/syslogd-configuration.xml`.

> We'll use syslog messages to validate the system later

### Enable and configure the Kafka Producer (for alarms)

Configure the Kafka Producer to point to your Kafka broker:
```
echo 'bootstrap.servers=127.0.0.1:9092' > "$OPENNMS_HOME/etc/org.opennms.features.kafka.producer.client.cfg"
```

Disable incremental alarm suppression:
```
echo 'suppressIncrementalAlarms=false' >> "$OPENNMS_HOME/etc/org.opennms.features.kafka.producer.cfg"
```

Add `opennms-kafka-producer` to the `featuresBoot` property in the `$OPENNMS_HOME/etc/org.apache.karaf.features.cfg`.

### Enable consumption of events from Kafka

Enable the Sink API consumer for Eventd:
```
echo 'org.opennms.netmgt.eventd.sink.enable=true' > "$OPENNMS_HOME/etc/opennms.properties.d/event-sink.properties"
```

Use the Kafka strategy for the Sink API:
```
echo 'org.opennms.core.ipc.sink.strategy=kafka
org.opennms.core.ipc.sink.kafka.bootstrap.servers=127.0.0.1:9092' >> "$OPENNMS_HOME/etc/opennms.properties.d/kafka.properties"
```
> Since we're using the Kafka strategy for the Sink API, any Minions configured on the system must also be configured to use Kafka.

### Configure plugin support

Edit `$OPENNMS_HOME/etc/org.apache.karaf.kar.cfg` and set:

* `noAutoRefreshBundles=true`
* `noAutoStartBundles=true`

> This won't be necessary once [HZN-1436](https://issues.opennms.org/browse/HZN-1436) is complete.

### Install the OCE plugin

Download and install the OCE plugin RPM on the OpenNMS server:

```
yum install opennms-oce-plugin-1.0.0-*.noarch.rpm
```

### Enable the OCE plugin

From the OpenNMS Karaf shell:
```
feature:install opennms-oce-plugin
```

Add `opennms-oce-plugin` to the `featuresBoot` property in the `$OPENNMS_HOME/etc/org.apache.karaf.features.cfg`.

> The feature should be made immediately available after installing the package.

## Sentinel Configuration

### Configure plugin support

Repeat the same instructions as above, altering the files in `$SENTINEL_HOME` instead of `$OPENNMS_HOME`.

### Install the OCE plugin

Download and install the OCE plugin RPM on the Sentinel server:
```
yum install sentinel-oce-plugin-1.0.0-*.noarch.rpm
```

### Configure OCE

Configure the Kafka Stream (consumer):
```
echo 'bootstrap.servers=127.0.0.1:9092
commit.interval.ms=5000' > "$SENTINEL_HOME/etc/org.opennms.oce.datasource.opennms.kafka.streams.cfg"
```

Configure the Kafka producer:
```
echo 'bootstrap.servers=127.0.0.1:9092' > "$SENTINEL_HOME/etc/org.opennms.oce.datasource.opennms.kafka.producer.cfg"
```

> The consumer is used to read alarms & inventory, whereas the producer is used to send events. 

Enable debug logging, from the Sentinel Karaf shell:
```
log:set DEBUG org.opennms.oce
```

### Start OCE 

From the Sentinel Karaf shell:
```
feature:install oce-datasource-opennms-kafka oce-engine-cluster oce-processor-standalone oce-driver-main oce-features-graph-shell
```

Add these features to the `featuresBoot` property in the `$SENTINEL_HOME/etc/org.apache.karaf.features.cfg`:
```
    oce-datasource-opennms-kafka, \
    oce-engine-cluster, \
    oce-processor-standalone, \
    oce-driver-main, \
    oce-features-graph-shell
```

## Validation

Once OpenNMS & Sentinel are setup using the notes above, we can simulate a few events to validate that correlation is functional.

Stop OpenNMS & Sentinel.

Delete `$OPENNMS_HOME/data` and `$SENTINEL_HOME/data`.

Start OpenNMS.

Provision a node for the localhost in OpenNMS:
```
$OPENNMS_HOME/bin/provision.pl requisition add oce
$OPENNMS_HOME/bin/provision.pl node add oce localhost localhost
$OPENNMS_HOME/bin/provision.pl interface add oce localhost 127.0.0.1
$OPENNMS_HOME/bin/provision.pl requisition import oce
```

> We assume that the localhost is running an SNMP agent.

Find the ifDescr for an interface on the node we just provisioned:
```
export IFDESCR="ens33"
```

Now trigger a Syslog message:
```
echo "<189>: $(date +"%Y %b %d %H:%m:%S %Z"): %ETHPORT-5-IF_DOWN_LINK_FAILURE: Interface $IFDESCR is down (Link failure)" | nc -v -u localhost 10514
```

> If the Syslog message is not being recognized and formatted to a proper event ensure that the `opennms-oce-plugin` feature is running.

From the OpenNMS Karaf shell validate that the alarms were pushed to the Kafka topic:
```
kafka-producer:list-alarms
```

Now start Sentinel.

From the Sentinel Karaf shell verify that the engine is loaded:
```
graph:list
```

Now trigger events that would cause a situation:
```
TODO: Another syslog message
```

Verify that the situation is created in OpenNMS.


### Topology

Now that we've validated the system, we can view the resulting graph generated by the cluster engine to help get some insights into the correlation.

From the Sentinel Karaf shell:
```
graph:write-graphml cluster /tmp/oce.cluster.grapml
```

Import the graph:
```
curl -X POST -H "Content-Type: application/xml" -u admin:admin -d@/tmp/oce.cluster.grapml 'http://localhost:8980/opennms/rest/graphml/OCE'
```

View the OCE graph in the Topology UI.

## TODO

* Make the OCE RPM binaries available online
* opennms-oce-plugin is not installed at start in OpenNMS - even when added to the featuresBoot:w
* syslog message example not working
* features do not start on sentinel
* stuck waiting for the oce-inventory topic -- `./bin/kafka-topics.sh --zookeeper 127.0.0.1 --create --topic oce-inventory --partitions 16 --replication-factor 1`

