package org.opennms.oce.model.shell;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.opennms.oce.model.api.Model;
import org.opennms.oce.model.api.ModelBuilder;
import org.opennms.oce.model.api.ModelObject;
import org.opennms.oce.model.impl.ModelObjectImpl;

public class GraphTest {
	
	@Test
	public void test() {
		ModelBuilder modelBuilder = mock(ModelBuilder.class);
		Model model = mock(Model.class);
		
		ModelObject modelObject = new ModelObjectImpl((ModelObject)null, "model");
		ModelObject eswitch = new ModelObjectImpl(modelObject, "switch");
		ModelObject card1 = new ModelObjectImpl(eswitch, "card");
		ModelObject card2 = new ModelObjectImpl(eswitch, "card");
		ModelObject port1 = new ModelObjectImpl(card1, "port");
		ModelObject port2 = new ModelObjectImpl(card1, "port");
		ModelObject prot3 = new ModelObjectImpl(card2, "port");
		ModelObject prot4 = new ModelObjectImpl(card2, "port");
		
		when(modelBuilder.buildModel()).thenReturn(model);
		when(model.getRoot()).thenReturn(modelObject);

		GenerateGraph generator = new GenerateGraph(modelBuilder);
		
		System.out.println(generator.generateGraph());
		// fail("Not yet implemented");
	}

}
