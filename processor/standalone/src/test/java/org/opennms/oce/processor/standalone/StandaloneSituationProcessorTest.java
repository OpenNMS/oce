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

package org.opennms.oce.processor.standalone;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.opennms.oce.datasource.api.Situation;
import org.opennms.oce.datasource.api.SituationDatasource;
import org.opennms.oce.processor.api.SituationProcessor;

/**
 * Tests for {@link StandaloneSituationProcessor}.
 */
public class StandaloneSituationProcessorTest {
    /**
     * Tests that a situation accepted by the processor is forwarded via the situation data source.
     */
    @Test
    public void testAccept() throws Exception {
        SituationDatasource mockSituationDataSource = mock(SituationDatasource.class);
        SituationProcessor situationProcessor = new StandaloneSituationProcessor(mockSituationDataSource);

        Situation mockSituation = mock(Situation.class);
        String id = "test.id";
        when(mockSituation.getId()).thenReturn(id);

        ArgumentCaptor<Situation> argumentCaptor = ArgumentCaptor.forClass(Situation.class);
        doNothing().when(mockSituationDataSource).forwardSituation(argumentCaptor.capture());

        situationProcessor.accept(mockSituation);
        verify(mockSituationDataSource, times(1)).forwardSituation(any(Situation.class));
        assertEquals(id, argumentCaptor.getValue().getId());
    }
}
