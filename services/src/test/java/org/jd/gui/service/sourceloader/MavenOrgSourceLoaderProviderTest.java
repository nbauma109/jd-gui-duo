/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.sourceloader;

import com.sun.net.httpserver.HttpServer;
import org.jd.gui.api.API;
import org.jd.gui.api.model.Container;
import org.jd.gui.service.preferencespanel.MavenOrgSourceLoaderPreferencesProvider;
import org.jd.gui.service.preferencespanel.NexusPreferencesProvider;
import org.jd.gui.util.nexus.model.NexusArtifact;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Finding the sources of a jar through a Nexus server (a local stub), and the filters on the package names. */
class MavenOrgSourceLoaderProviderTest {

    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final String SOURCE = "package org.demo;\npublic class Calc { int one() { return 1; } }\n";

    @TempDir
    Path dir;

    private HttpServer server;
    private final List<String> requests = new ArrayList<>();
    private byte[] sourcesJar;
    private boolean knowsTheJar = true;
    private String artifactId;

    @BeforeEach
    void startServer() throws IOException {
        artifactId = "demo-lib-" + COUNTER.incrementAndGet();
        sourcesJar = zip("org/demo/Calc.java", SOURCE, "org/demo/Other.java", "package org.demo; class Other {}");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String query = String.valueOf(exchange.getRequestURI().getRawQuery());
            synchronized (requests) {
                requests.add(path + "?" + query);
            }
            byte[] body;
            if (path.endsWith("/service/local/status")) {
                body = "<status><version>2.14</version></status>".getBytes(StandardCharsets.UTF_8);
            } else if (path.endsWith("/artifact/maven/content")) {
                body = sourcesJar;
            } else if (path.endsWith("/lucene/search")) {
                body = search(query).getBytes(StandardCharsets.UTF_8);
            } else {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/";
    }

    private String search(String query) {
        if (!knowsTheJar) {
            return "<searchNGResponse><data></data></searchNGResponse>";
        }
        String link = query.contains("c=sources")
                ? "<artifactLink><extension>jar</extension><classifier>sources</classifier></artifactLink>"
                : "<artifactLink><extension>jar</extension></artifactLink>";
        return "<searchNGResponse><data><artifact><groupId>org.demo</groupId><artifactId>" + artifactId
                + "</artifactId><version>1.0</version><artifactHits><artifactHit><repositoryId>releases</repositoryId>"
                + "<artifactLinks>" + link + "</artifactLinks></artifactHit></artifactHits></artifact></data></searchNGResponse>";
    }

    private static byte[] zip(String... namesAndContents) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            for (int i = 0; i < namesAndContents.length; i += 2) {
                out.putNextEntry(new ZipEntry(namesAndContents[i]));
                out.write(namesAndContents[i + 1].getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private record FakeEntry(String path, URI uri, Container container, Container.Entry parent) implements Container.Entry {
        public boolean isDirectory() { return false; }
        public String getPath() { return path; }
        public Map<Container.EntryPath, Container.Entry> getChildren() { return Map.of(); }
        public Container getContainer() { return container; }
        public Container.Entry getParent() { return parent; }
        public URI getUri() { return uri; }
        public long length() { return 0; }
        public long compressedLength() { return 0; }
        public InputStream getInputStream() { return new ByteArrayInputStream(new byte[0]); }
    }

    private Map<String, String> preferences = new HashMap<>();

    private API api() {
        return (API) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {API.class},
                (proxy, method, args) -> "getPreferences".equals(method.getName()) ? preferences : null);
    }

    private File realJar;

    /** A class of the jar, with the jar entry (a file) as the parent of the container's root. */
    private FakeEntry classEntry(String path) throws IOException {
        realJar = dir.resolve(artifactId + ".jar").toFile();
        Files.write(realJar.toPath(), zip(path, "not really a class"));
        FakeEntry jarEntry = new FakeEntry("", realJar.toURI(), null, null);
        Container[] holder = new Container[1];
        FakeEntry root = new FakeEntry("", URI.create("jar:" + realJar.toURI() + "!/"), null, jarEntry);
        holder[0] = new Container() {
            public String getType() { return "jar"; }
            public Entry getRoot() { return root; }
        };
        return new FakeEntry(path, URI.create("jar:" + realJar.toURI() + "!/" + path), holder[0], root);
    }

    private void useLocalNexus() {
        preferences.put(NexusPreferencesProvider.NEXUS_URL, baseUrl());
    }

    @Test
    void filtersAcceptOrRejectPackagesByTheFirstMatchingPrefix() {
        MavenOrgSourceLoaderProvider provider = new MavenOrgSourceLoaderProvider();
        String filters = "+org +com.google -com +java";

        assertThat(provider.accepted(filters, "org/demo/Calc.class")).isTrue();
        assertThat(provider.accepted(filters, "com/google/Foo.class")).isTrue();
        assertThat(provider.accepted(filters, "com/acme/Foo.class")).isFalse();
        assertThat(provider.accepted(filters, "java/lang/String.class")).isTrue();
        assertThat(provider.accepted(filters, "other/Foo.class")).isFalse();
        assertThat(provider.accepted("+org/ x -", "org/Foo.class")).isTrue();
        assertThat(provider.accepted("", "org/Foo.class")).isFalse();
    }

    @Test
    void nothingIsLoadedWhenTheLoaderIsTurnedOffOrThePackageIsFilteredOut() throws IOException {
        useLocalNexus();
        FakeEntry entry = classEntry("org/demo/Calc.class");
        MavenOrgSourceLoaderProvider provider = new MavenOrgSourceLoaderProvider();

        preferences.put(MavenOrgSourceLoaderPreferencesProvider.ACTIVATED, "false");
        assertThat(provider.loadSource(api(), entry)).isNull();
        assertThat(provider.getSource(api(), entry)).isNull();
        assertThat(provider.loadSourceFile(api(), entry.parent().getParent())).isNull();

        preferences.put(MavenOrgSourceLoaderPreferencesProvider.ACTIVATED, "true");
        preferences.put(MavenOrgSourceLoaderPreferencesProvider.FILTERS, "-org");
        assertThat(provider.loadSource(api(), entry)).isNull();
        assertThat(provider.getSource(api(), entry)).isNull();
        assertThat(requests).isEmpty();
    }

    @Test
    void theSourcesAreFoundThroughTheJarsSha1AndThenKeptInTheCache() throws IOException {
        useLocalNexus();
        FakeEntry entry = classEntry("org/demo/Calc.class");
        MavenOrgSourceLoaderProvider provider = new MavenOrgSourceLoaderProvider();

        assertThat(provider.getSource(api(), entry)).as("nothing is downloaded by getSource").isNull();
        assertThat(provider.loadSource(api(), entry)).isEqualTo(SOURCE);
        assertThat(requests).anyMatch(r -> r.contains("sha1=")).anyMatch(r -> r.contains("c=sources"));

        int before = requests.size();
        assertThat(provider.getSource(api(), entry)).isEqualTo(SOURCE);
        assertThat(provider.loadSource(api(), entry)).isEqualTo(SOURCE);
        assertThat(requests).hasSize(before);
    }

    @Test
    void theSourceJarFileIsAvailableAndAClassWithoutSourcesGivesNothing() throws IOException {
        useLocalNexus();
        FakeEntry entry = classEntry("org/demo/Calc.class");
        MavenOrgSourceLoaderProvider provider = new MavenOrgSourceLoaderProvider();

        File sources = provider.loadSourceFile(api(), entry.parent().getParent());

        assertThat(sources).isNotNull().exists();
        assertThat(provider.searchSource(classEntry("org/demo/Missing.class"), sources)).isNull();
        assertThat(provider.searchSource(entry, null)).isNull();
        assertThat(provider.searchSource(entry, new File(dir.toFile(), "no-such.jar"))).isNull();
    }

    @Test
    void aJarUnknownToTheServerFailsOnceAndIsNotAskedAgain() throws IOException {
        useLocalNexus();
        knowsTheJar = false;
        FakeEntry entry = classEntry("org/demo/Calc.class");
        MavenOrgSourceLoaderProvider provider = new MavenOrgSourceLoaderProvider();

        assertThat(provider.loadSource(api(), entry)).isNull();
        int after = requests.size();
        assertThat(after).isPositive();
        assertThat(provider.loadSource(api(), entry)).isNull();
        assertThat(requests).hasSize(after);
    }

    @Test
    void theArtifactOfAJarIsFoundWithOrWithoutItsSources() throws Exception {
        useLocalNexus();

        NexusArtifact binary = MavenOrgSourceLoaderProvider.buildArtifactFromURI(api(), "abc", false);
        NexusArtifact sources = MavenOrgSourceLoaderProvider.buildArtifactFromURI(api(), "abc", true);

        assertThat(binary.classifier()).isNull();
        assertThat(sources.classifier()).isEqualTo("sources");
        assertThat(sources.artifactLink()).contains("c=sources");

        knowsTheJar = false;
        assertThat(MavenOrgSourceLoaderProvider.buildArtifactFromURI(api(), "abc", true)).isNull();
    }
}
