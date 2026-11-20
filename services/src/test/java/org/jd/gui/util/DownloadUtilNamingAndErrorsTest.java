/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.util;

import com.sun.net.httpserver.HttpServer;

import org.jd.gui.util.maven.central.helper.NexusConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** How downloaded files are named, what happens on errors and redirects, and when the Nexus credentials are sent. */
class DownloadUtilNamingAndErrorsTest {

    private HttpServer server;
    private final List<String> authorizations = new ArrayList<>();
    private final List<File> downloaded = new ArrayList<>();
    private int port;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            synchronized (authorizations) {
                authorizations.add(path + " -> " + exchange.getRequestHeaders().getFirst("Authorization"));
            }
            if (path.startsWith("/missing")) {
                exchange.sendResponseHeaders(404, -1);
            } else if (path.startsWith("/moved")) {
                exchange.getResponseHeaders().add("Location", "/target-of-the-redirect");
                exchange.sendResponseHeaders(302, -1);
            } else {
                byte[] body = ("content of " + path).getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterEach
    void stop() throws IOException {
        server.stop(0);
        for (File file : downloaded) {
            Files.deleteIfExists(file.toPath());
        }
    }

    private File download(URI uri, NexusConfig config) throws IOException {
        File file = DownloadUtil.downloadToTemp(uri, null, config);
        downloaded.add(file);
        return file;
    }

    // ------------------------------------------------------------------ naming

    @ParameterizedTest(name = "{0} is saved as {1}")
    @CsvSource(delimiter = '|', value = {
            "https://search.example/remotecontent?filepath=org%2Fdemo%2Flib%2F1.0%2Flib-1.0.jar | lib-1.0.jar",
            "https://search.example/remotecontent?filepath=plain.jar | plain.jar",
            "https://nexus.example/download?name=lib&version=1.0&extension=jar | lib-1.0.jar",
            "https://nexus.example/download?name=lib&version=1.0&extension=jar&classifier=sources | lib-1.0-sources.jar",
            "https://nexus.example/download?name=my%20lib&version=1&extension=jar | my lib-1.jar",
            "https://nexus.example/content?r=releases&a=lib&v=2.0&p=jar | lib-2.0.jar",
            "https://nexus.example/content?r=releases&a=lib&v=2.0&p=jar&c=javadoc | lib-2.0-javadoc.jar",
            "https://repo.example/org/demo/lib-3.0.jar | lib-3.0.jar",
            "https://nexus.example/repo/lib-4.0.jar?name=lib&version=4.0 | lib-4.0.jar",
            "https://repo.example/file.jar?a&b= | file.jar"})
    void theFileNameIsDerivedFromTheAddress(String uri, String expected) {
        assertThat(DownloadUtil.resolveFileName(URI.create(uri))).isEqualTo(expected);
    }

    @Test
    void anAddressWithoutAFileNameHasNone() {
        assertThat(DownloadUtil.resolveFileName(URI.create("https://repo.example/"))).isNull();
        assertThat(DownloadUtil.resolveFileName(URI.create("https://repo.example"))).isNull();
    }

    // ------------------------------------------------------------------ errors and redirects

    @Test
    void aMissingAddressIsRefused() {
        assertThatThrownBy(() -> DownloadUtil.downloadToTemp(null, null, (NexusConfig) null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DownloadUtil.downloadToTemp(URI.create("http://localhost/x"), (org.jd.gui.api.API) null, (java.awt.Component) null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anHttpErrorIsReported() {
        assertThatThrownBy(() -> download(URI.create("http://localhost:" + port + "/missing/lib-1.jar"), null))
                .isInstanceOf(IOException.class).hasMessageContaining("HTTP 404");
    }

    @Test
    void aRedirectIsFollowed() throws IOException {
        File file = download(URI.create("http://localhost:" + port + "/moved/redirected-1.jar"), null);

        assertThat(file.getName()).isEqualTo("redirected-1.jar");
        assertThat(Files.readString(file.toPath())).isEqualTo("content of /target-of-the-redirect");
    }

    @Test
    void anAddressWithoutAFileNameIsSavedAsDownloadBin() throws IOException {
        File file = download(URI.create("http://localhost:" + port + "/"), null);

        assertThat(file.getName()).isEqualTo("download.bin");
        assertThat(Files.readString(file.toPath())).isEqualTo("content of /");
    }

    // ------------------------------------------------------------------ the scope of the Nexus credentials

    private boolean credentialsAreSent(String baseUrl, String user, char[] password, String path) throws IOException {
        synchronized (authorizations) {
            authorizations.clear();
        }
        download(URI.create("http://localhost:" + port + path), new NexusConfig(baseUrl, user, password));
        synchronized (authorizations) {
            return authorizations.stream().noneMatch(entry -> entry.endsWith("-> null"));
        }
    }

    @Test
    void credentialsAreSentOnlyToTheConfiguredNexus() throws IOException {
        char[] secret = "secret".toCharArray();
        String host = "http://localhost:" + port;

        assertThat(credentialsAreSent(host, "alice", secret, "/scope-a-1.jar")).as("the root of the server").isTrue();
        assertThat(credentialsAreSent(host + "/nexus/", "alice", secret, "/nexus/scope-b-1.jar")).as("under the base path, trailing slash").isTrue();
        assertThat(credentialsAreSent(host + "/nexus", "alice", secret, "/nexus/deep/er/scope-c-1.jar")).as("deeper under the base path").isTrue();
        assertThat(credentialsAreSent("HTTP://LOCALHOST:" + port + "/nexus", "alice", secret, "/nexus/scope-d-1.jar")).as("scheme and host ignore case").isTrue();

        assertThat(credentialsAreSent(host + "/nexus", "alice", secret, "/nexus2/scope-e-1.jar")).as("a sibling path with the same prefix").isFalse();
        assertThat(credentialsAreSent(host + "/nexus", "alice", secret, "/other/scope-f-1.jar")).as("another path").isFalse();
        assertThat(credentialsAreSent("http://localhost:" + (port + 1), "alice", secret, "/scope-g-1.jar")).as("another port").isFalse();
        assertThat(credentialsAreSent("https://localhost:" + port, "alice", secret, "/scope-h-1.jar")).as("another scheme").isFalse();
        assertThat(credentialsAreSent("http://127.0.0.1:" + port, "alice", secret, "/scope-i-1.jar")).as("another host").isFalse();
    }

    @Test
    void credentialsAreNotSentWhenTheyAreIncompleteOrTheBaseAddressIsUnusable() throws IOException {
        String host = "http://localhost:" + port;

        assertThat(credentialsAreSent(host, null, "secret".toCharArray(), "/inc-a-1.jar")).as("no user").isFalse();
        assertThat(credentialsAreSent(host, "alice", null, "/inc-b-1.jar")).as("no password").isFalse();
        assertThat(credentialsAreSent(host, "alice", new char[0], "/inc-c-1.jar")).as("empty password").isFalse();
        assertThat(credentialsAreSent("not a valid address", "alice", "secret".toCharArray(), "/inc-d-1.jar")).as("unusable base address").isFalse();
        assertThat(credentialsAreSent(null, "alice", "secret".toCharArray(), "/inc-e-1.jar")).as("no base address").isFalse();
    }
}
