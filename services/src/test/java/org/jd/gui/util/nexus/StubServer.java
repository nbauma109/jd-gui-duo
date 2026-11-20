/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.util.nexus;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** A local HTTP server answering canned bodies by path, and remembering what it was asked. */
final class StubServer implements AutoCloseable {

    record Response(int status, String body) {
    }

    private final HttpServer server;
    private final Map<String, Response> responses = new ConcurrentHashMap<>();
    private final List<String> requests = new ArrayList<>();
    private volatile String lastAuthorization;

    StubServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String target = exchange.getRequestURI().toString();
            synchronized (requests) {
                requests.add(target);
            }
            lastAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
            Response response = responses.getOrDefault(exchange.getRequestURI().getPath(), new Response(404, "not found"));
            byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(response.status(), bytes.length == 0 ? -1 : bytes.length);
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

    StubServer answer(String path, int status, String body) {
        responses.put(path, new Response(status, body));
        return this;
    }

    List<String> requests() {
        synchronized (requests) {
            return new ArrayList<>(requests);
        }
    }

    String lastAuthorization() {
        return lastAuthorization;
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
