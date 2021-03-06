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

package org.opennms.oce.features.graph.shell;

import java.io.File;

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.opennms.oce.features.graph.common.GraphMLConverterBuilder;
import org.opennms.oce.features.graph.common.GraphProviderLocator;
import org.opennms.oce.features.graph.common.OsgiGraphProviderLocator;
import org.opennms.oce.features.graph.graphml.GraphML;
import org.opennms.oce.features.graph.graphml.GraphMLWriter;
import org.opennms.oce.features.graph.graphml.InvalidGraphException;
import org.osgi.framework.BundleContext;

@Command(scope = "oce", name = "export-graph", description = "Export an engine's graph to GraphML\n" +
        "Use oce:list-graphs to enumerate the available providers.")
@Service
public class ToGraphML implements Action {

    @Reference
    private BundleContext bundleContext;

    @Argument(index=0, required = true)
    private String graphProviderName;

    @Argument(index=1, required = true)
    private String outputFileName;

    @Option(name = "--includeAlarms", aliases = "-a", description = "Include alarms in the GraphML output.")
    private boolean includeAlarms = true;

    @Option(name = "--filterEmptyNodes", aliases = "-f", description = "Remove Inventory Objects that have no alarms and only one edge from the GraphML output.")
    private boolean filterEmptyNodes = true;

    @Override
    public Object execute() throws InvalidGraphException {
        if ("empty".equalsIgnoreCase(graphProviderName)) {
            final GraphML graphML = new GraphML();
            GraphMLWriter.write(graphML, new File(outputFileName));
            return null;
        }

        final GraphProviderLocator graphProviderLocator = new OsgiGraphProviderLocator(bundleContext);
        final GraphML graphML = graphProviderLocator.withGraphProvider(graphProviderName,
                graphProvider -> graphProvider.withReadOnlyGraph(g -> {
                    return new GraphMLConverterBuilder()
                            .withGraph(g)
                            .withIncludeAlarms(includeAlarms)
                            .withFilterEnptyNodes(filterEmptyNodes)
                            .build().toGraphML();
                }));
        if (graphML == null) {
            System.out.printf("No graph provider named '%s' was found.\n", graphProviderName);
            return null;
        } else {
            GraphMLWriter.write(graphML, new File(outputFileName));
        }

        return null;
    }

}
