package org.opennms.oce.engine.topology.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;
import org.opennms.oce.driver.test.MockInventory;

public class AlarmPropagationTest {

    private ModelImpl testModel;

    @Before
    public void setUp() {
        ModelBuilderImpl modelBuilder = new ModelBuilderImpl();
        testModel = ModelBuilderImpl.buildModel(MockInventory.SAMPLE_NETWORK);
    }

    @Test
    public void canPropagateState() {
        ModelObjectImpl ModelObjectImpl = testModel.getRoot();
        ModelObjectImpl eswitch = testModel.getObjectById("Device", "n1");
        ModelObjectImpl card1 = testModel.getObjectById("Card", "n1-c1");
        ModelObjectImpl card2 = testModel.getObjectById("Card", "n1-c2");
        ModelObjectImpl port1 = testModel.getObjectById("Port", "n1-c1-p1");
        ModelObjectImpl port2 = testModel.getObjectById("Port", "n1-c1-p2");
        ModelObjectImpl port3 = testModel.getObjectById("Port", "n1-c2-p1");
        ModelObjectImpl port4 = testModel.getObjectById("Port", "n1-c2-p2");
        ModelObjectImpl link = testModel.getObjectById("Link", "n1-c1-p1___n2-c1-p1");

        // Verify initial State
        assertThat(port1.getServiceState(), is(ServiceState.IN));
        // ... all ModelObjectImpls

        assertEquals(port1.getOperationalState(), OperationalState.NORMAL);
        // ...

        assertEquals(card1.getChildGroup("Port").getMembers().size(), 2);
        assertEquals(card1.getChildGroup("Port").getNumberNormalState(), 2);
        assertEquals(card1.getChildGroup("Port").getNumberNonServiceAffecting(), 0);
        assertEquals(card1.getChildGroup("Port").getNumberServiceAffecting(), 0);
        assertEquals(link.getPeerGroup("Port").getNumberServiceAffecting(), 0);

        // Propagate an SA alarm on the first port....
        port1.setOperationalState(OperationalState.SA);
        assertEquals(card1.getChildGroup("Port").getNumberNormalState(), 1);
        assertEquals(card1.getChildGroup("Port").getNumberNonServiceAffecting(), 0);
        assertEquals(card1.getChildGroup("Port").getNumberServiceAffecting(), 1);
        assertEquals(link.getPeerGroup("Port").getNumberServiceAffecting(), 1);

        // Ensure state clears properly
        port1.setOperationalState(OperationalState.NORMAL);
        assertEquals(card1.getChildGroup("Port").getNumberNormalState(), 2);
        assertEquals(card1.getChildGroup("Port").getNumberNonServiceAffecting(), 0);
        assertEquals(card1.getChildGroup("Port").getNumberServiceAffecting(), 0);
        assertEquals(link.getPeerGroup("Port").getNumberServiceAffecting(), 0);

        // and NSA....
        port1.setOperationalState(OperationalState.NSA);
        assertEquals(card1.getChildGroup("Port").getNumberNormalState(), 1);
        assertEquals(card1.getChildGroup("Port").getNumberNonServiceAffecting(), 1);
        assertEquals(card1.getChildGroup("Port").getNumberServiceAffecting(), 0);
        assertEquals(link.getPeerGroup("Port").getNumberNonServiceAffecting(), 1);

        // Propagate an SA alarm on the first port....
        port1.setOperationalState(OperationalState.SA);
        assertEquals(card1.getChildGroup("Port").getNumberNormalState(), 1);
        assertEquals(card1.getChildGroup("Port").getNumberNonServiceAffecting(), 0);
        assertEquals(card1.getChildGroup("Port").getNumberServiceAffecting(), 1);

        // TODO - update the model and add tests for multiple groups - ie. uncles and peers
        // e.g. uncle - BGPSession over a link

        // We need to track that the Status Count change direction

    }

}