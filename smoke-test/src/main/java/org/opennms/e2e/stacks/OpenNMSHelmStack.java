///*
// * Copyright 2016, The OpenNMS Group
// *
// * Licensed under the Apache License, Version 2.0 (the "License"); you may
// * not use this file except in compliance with the License. You may obtain
// * a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//package org.opennms.e2e.stacks;
//
//import static java.util.concurrent.TimeUnit.MINUTES;
//import static java.util.concurrent.TimeUnit.SECONDS;
//import static org.awaitility.Awaitility.await;
//import static org.hamcrest.Matchers.greaterThanOrEqualTo;
//import static org.hamcrest.Matchers.notNullValue;
//import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
//
//import java.io.File;
//import java.io.IOException;
//import java.io.InputStream;
//import java.io.PrintStream;
//import java.net.InetSocketAddress;
//import java.net.MalformedURLException;
//import java.net.URL;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.nio.file.Paths;
//import java.nio.file.StandardCopyOption;
//import java.util.Arrays;
//import java.util.List;
//import java.util.Map;
//import java.util.Objects;
//import java.util.concurrent.TimeUnit;
//import java.util.function.Consumer;
//import java.util.function.Function;
//
//import org.apache.commons.io.FileUtils;
//import org.opennms.e2e.containers.DockerTagResolver;
//import org.opennms.e2e.grafana.GrafanaRestClient;
//import org.opennms.e2e.opennms.OpenNMSRestClient;
//import org.opennms.gizmo.docker.GizmoDockerStack;
//import org.opennms.gizmo.docker.GizmoDockerStacker;
//import org.opennms.gizmo.docker.stacks.EmptyDockerStack;
//import org.opennms.gizmo.utils.HttpUtils;
//import org.opennms.gizmo.utils.SshClient;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import com.google.common.collect.ImmutableMap;
//import com.google.common.collect.Lists;
//import com.spotify.docker.client.messages.ContainerConfig;
//import com.spotify.docker.client.messages.HostConfig;
//
//public class OpenNMSHelmStack extends EmptyDockerStack {
//    private static final Logger LOG = LoggerFactory.getLogger(OpenNMSHelmStack.class);
//    private static String OPENNMS = "OPENNMS";
//    private static String HELM = "HELM";
//    GizmoDockerStacker stacker;
//
//    @Override
//    public Map<String, Function<GizmoDockerStacker, ContainerConfig>> getContainersByAlias() {
//        return ImmutableMap.of(OPENNMS, (stacker) -> ContainerConfig.builder()
//                        .image(DockerTagResolver.getTag("opennms"))
//                        .exposedPorts("8980/tcp")
//                        .env("POSTGRES_HOST=db",
//                                "POSTGRES_PORT=5432",
//                                "POSTGRES_USER=" + PostgreSQLStack.USERNAME,
//                                "POSTGRES_PASSWORD=" + PostgreSQLStack.PASSWORD,
//                                "OPENNMS_DBNAME=opennms",
//                                "OPENNMS_DBUSER=opennms",
//                                "OPENNMS_DBPASS=opennms",
//                                "KARAF_FEATURES=opennms-kafka-producer")
//                        .hostConfig(HostConfig.builder()
//                                .publishAllPorts(true)
//                                .autoRemove(true)
//                                .binds(setupOverlay("opennms-overlay") + ":/opt/opennms-overlay")
//                                .links(String.format("%s:db",
//                                        stacker.getContainerInfo(PostgreSQLStack.POSTGRES).name()),
//                                        String.format("%s:kafka",
//                                                stacker.getContainerInfo(KafkaStack.KAFKA).name()))
//                                .build())
//                        .cmd("-s")
//                        .build(),
//                HELM, (stacker) -> ContainerConfig.builder()
//                        .image(DockerTagResolver.getTag("helm"))
//                        .exposedPorts("3000/tcp")
//                        .hostConfig(HostConfig.builder()
//                                .publishAllPorts(true)
//                                .autoRemove(true)
//                                .links(String.format("%s:opennms", stacker.getContainerInfo(OPENNMS).name()))
//                                .build())
//                        .build()
//        );
//    }
//
//    Path setupOverlay(String overlayDir) {
//        Path exportedResources = null;
//
//        // Hack for macOS since the default tmpdir in /var cannot be used for docker binds
//        if (System.getProperty("os.name").toLowerCase().startsWith("mac os")) {
//            System.setProperty("java.io.tmpdir", "/tmp");
//        }
//
//        try {
//            exportedResources = exportResources(overlayDir);
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//
//        return exportedResources;
//    }
//
//    private Path exportResources(String sourceDir) throws IOException {
//        Path exportDir = Files.createTempDirectory("");
//        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
//            try {
//                FileUtils.deleteDirectory(exportDir.toFile());
//            } catch (IOException e) {
//                throw new RuntimeException(e);
//            }
//        }));
//        String sourcePath = Paths.get("/", Objects.requireNonNull(sourceDir)).toString();
//        File[] filesToExport = new File(getClass().getResource(sourcePath).getPath()).listFiles();
//
//        if (filesToExport != null) {
//            for (File f : filesToExport) {
//                copyFiles(f, exportDir.toString(), "");
//            }
//        }
//
//        return exportDir;
//    }
//
//    private void copyFiles(File source, String destination, String pathPrefix) throws IOException {
//        if (source.isFile()) {
//            InputStream fileToCopy = getClass().getResourceAsStream(
//                    Paths.get("/", pathPrefix, source.getParentFile().getName(), source.getName()).toString());
//
//            if (!Files.exists(Paths.get(destination))) {
//                new File(destination).mkdirs();
//            }
//
//            Files.copy(fileToCopy, Paths.get(destination, source.getName()),
//                    StandardCopyOption.REPLACE_EXISTING);
//        } else {
//            if (source.isDirectory()) {
//                File[] filesInDir = source.listFiles();
//
//                if (filesInDir != null) {
//                    for (File f : filesInDir) {
//                        copyFiles(f, Paths.get(destination, source.getName()).toString(), Paths.get(pathPrefix,
//                                source.getParentFile().getName()).toString());
//                    }
//                }
//            }
//        }
//    }
//
//    @Override
//    public List<GizmoDockerStack> getDependencies() {
//        return Arrays.asList(new PostgreSQLStack(), new KafkaStack());
//    }
//
//    @Override
//    public void beforeStack(GizmoDockerStacker stacker) {
//        this.stacker = stacker;
//    }
//
//    @Override
//    public List<Consumer<GizmoDockerStacker>> getWaitingRules() {
//        return Lists.newArrayList((stacker) -> {
//            LOG.info("Waiting for OpenNMS...");
//            final OpenNMSRestClient nmsRestClient = getOpenNMSRestClient();
//            await().atMost(5, MINUTES)
//                    .pollInterval(5, SECONDS).pollDelay(0, SECONDS)
//                    .ignoreExceptions()
//                    .until(nmsRestClient::getDisplayVersion, notNullValue());
//
//            waitForBundleActive("org.opennms.features.kafka", stacker.getServiceAddress(OPENNMS, 8101));
//            LOG.info("OpenNMS is ready");
//        }, (stacker) -> {
//            LOG.info("Waiting for Helm...");
//            final GrafanaRestClient grafanaRestClient = getGrafanaRestClient();
//            await().atMost(2, MINUTES)
//                    .pollInterval(5, SECONDS).pollDelay(0, SECONDS)
//                    .ignoreExceptions()
//                    .until(grafanaRestClient::getDataSources, hasSize(greaterThanOrEqualTo(0)));
//            LOG.info("Helm is ready");
//        });
//    }
//
//    public static String runKarafCommands(InetSocketAddress serviceAddress, String... commands) throws Exception {
//        try (final SshClient sshClient = new SshClient(serviceAddress, "admin", "admin")) {
//            PrintStream pipe = sshClient.openShell();
//
//            for (String s : commands) {
//                LOG.debug("Running Karaf command {}", s);
//                pipe.println(s);
//            }
//
//            // Logout of the shell if a logout command was not provided
//            if (!commands[commands.length - 1].equals("logout")) {
//                pipe.println("logout");
//            }
//
//            await()
//                    .atMost(10, SECONDS)
//                    .until(sshClient.isShellClosedCallable());
//
//            return sshClient.getStdout();
//        }
//    }
//
//    static void waitForBundleActive(String bundleName, InetSocketAddress serviceAddress) {
//        LOG.debug("Checking for active bundle with prefix {}", bundleName);
//
//        await()
//                .atMost(5, TimeUnit.MINUTES)
//                .pollInterval(5, TimeUnit.SECONDS)
//                .ignoreExceptions()
//                .until(() -> {
//                    String[] output = runKarafCommands(serviceAddress, "bundle:list -s").split("\n");
//
//                    return Arrays.stream(output).anyMatch(row -> row.contains(bundleName) &&
//                            row.contains("Active"));
//                });
//    }
//
//    public URL getOpenNMSUrl() {
//        InetSocketAddress addr = stacker.getServiceAddress(OpenNMSHelmStack.OPENNMS, 8980);
//        try {
//            return new URL(String.format("http://%s:%d/opennms", addr.getHostString(), addr.getPort()));
//        } catch (MalformedURLException e) {
//            throw new RuntimeException(e);
//        }
//    }
//
//    public URL getHelmUrl() {
//        return HttpUtils.toHttpUrl(stacker.getServiceAddress(OpenNMSHelmStack.HELM, 3000));
//    }
//
//    public InetSocketAddress getOCESSHAddress(String alias) {
//        return stacker.getServiceAddress(alias, 8301);
//    }
//
//    GrafanaRestClient getGrafanaRestClient() {
//        return new GrafanaRestClient(getHelmUrl());
//    }
//
//    OpenNMSRestClient getOpenNMSRestClient() {
//        return new OpenNMSRestClient(getOpenNMSUrl());
//    }
//}
