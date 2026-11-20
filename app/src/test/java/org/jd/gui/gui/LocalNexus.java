/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.gui;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** A Nexus 3 look-alike on the loopback interface that answers every search with one fixed component. */
final class LocalNexus implements AutoCloseable {

    private static final String COMPONENTS = """
            {"items": [{"id": "c1", "repository": "maven-releases", "format": "maven2", "group": "org.demo", "name": "demo-lib", "version": "1.2.3",
              "assets": [
                {"repository": "maven-releases", "lastModified": "2024-05-06T07:08:09.000+00:00",
                 "maven2": {"groupId": "org.demo", "artifactId": "demo-lib", "version": "1.2.3", "extension": "jar"}},
                {"repository": "maven-releases", "lastModified": "2024-05-06T07:08:09.000+00:00",
                 "maven2": {"groupId": "org.demo", "artifactId": "demo-lib", "version": "1.2.3", "classifier": "sources", "extension": "jar"}}
              ]}], "continuationToken": null}
            """;

    private static final String ASSETS = """
            {"items": [{"repository": "maven-releases", "lastModified": "2024-05-06T07:08:09.000+00:00",
              "maven2": {"groupId": "org.demo", "artifactId": "demo-lib", "version": "1.2.3", "extension": "jar"}}]}
            """;

    private final HttpServer server;
    private final List<String> requests = new ArrayList<>();

    LocalNexus() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String uri = exchange.getRequestURI().toString();
            synchronized (requests) {
                requests.add(uri);
            }
            String path = exchange.getRequestURI().getPath();
            int status = 200;
            String body = "";
            boolean again;
            synchronized (requests) {
                again = requests.stream().filter(uri::equals).count() > 1;
            }
            if (again) {
                // the client pages through the results: after the first page there is nothing more
                body = "{\"items\": []}";
            } else if (path.equals("/service/rest/v1/search/assets")) {
                body = ASSETS;
            } else if (path.startsWith("/service/rest/v1/search")) {
                body = COMPONENTS;
            } else if (!path.equals("/service/rest/v1/status")) {
                status = 404;
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
        server.start();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    List<String> requests() {
        synchronized (requests) {
            return new ArrayList<>(requests);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
