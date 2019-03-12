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

package org.opennms.e2e.containers.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

import org.apache.commons.io.FileUtils;

public class Overlay {
    public static Path setupOverlay(String overlayDir, Class clazz) {
        Path exportedResources;

        // Hack for macOS since the default tmpdir in /var cannot be used for docker binds
        if (System.getProperty("os.name").toLowerCase().startsWith("mac os")) {
            System.setProperty("java.io.tmpdir", "/tmp");
        }

        try {
            exportedResources = exportResources(overlayDir, clazz);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return exportedResources;
    }

    private static Path exportResources(String sourceDir, Class clazz) throws IOException {
        Path exportDir = Files.createTempDirectory("");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                FileUtils.deleteDirectory(exportDir.toFile());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }));
        String sourcePath = Paths.get("/", Objects.requireNonNull(sourceDir)).toString();
        File[] filesToExport = new File(clazz.getResource(sourcePath).getPath()).listFiles();

        if (filesToExport != null) {
            for (File f : filesToExport) {
                copyFiles(f, exportDir.toString(), "", clazz);
            }
        }

        return exportDir;
    }

    private static void copyFiles(File source, String destination, String pathPrefix, Class clazz) throws IOException {
        if (source.isFile()) {
            InputStream fileToCopy = clazz.getResourceAsStream(
                    Paths.get("/", pathPrefix, source.getParentFile().getName(), source.getName()).toString());

            if (!Files.exists(Paths.get(destination))) {
                new File(destination).mkdirs();
            }

            Files.copy(fileToCopy, Paths.get(destination, source.getName()),
                    StandardCopyOption.REPLACE_EXISTING);
        } else {
            if (source.isDirectory()) {
                File[] filesInDir = source.listFiles();

                if (filesInDir != null) {
                    for (File f : filesInDir) {
                        copyFiles(f, Paths.get(destination, source.getName()).toString(), Paths.get(pathPrefix,
                                source.getParentFile().getName()).toString(), clazz);
                    }
                }
            }
        }
    }
}
