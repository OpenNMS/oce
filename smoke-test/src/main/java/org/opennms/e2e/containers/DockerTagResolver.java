/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2019 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2019 The OpenNMS Group, Inc.
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

package org.opennms.e2e.containers;

import java.io.IOException;
import java.util.Optional;
import java.util.Properties;

public class DockerTagResolver {
    private static String TAGS_FILE = "docker_tags";
    private static String FIXED_TAGS_FILE = "docker_tags_fixed";

    public static String getTag(String name) {
        String tag;

        try {
            tag = getTagFromFile(TAGS_FILE, name).orElse(getTagFromFile(FIXED_TAGS_FILE, name).orElse(null));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (tag == null) {
            throw new RuntimeException("Could not find tag for " + name);
        }

        return tag;
    }

    private static Optional<String> getTagFromFile(String file, String name) throws IOException {
        Properties prop = new Properties();
        prop.load(Thread.currentThread().getContextClassLoader().getResourceAsStream(file));
        String tag = prop.getProperty(name);
        return tag == null ? Optional.empty() : Optional.of(tag);
    }
}
