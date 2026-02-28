/*******************************************************************************
 * Copyright (C) 2008-2025 Emmanuel Dupuy and other contributors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package org.jd.gui.util;

import com.sun.net.httpserver.HttpServer;
import org.jd.gui.util.maven.central.helper.NexusConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DownloadUtilTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    @Test
    void downloadToTemp_appliesNexusAuthorizationWhenUriMatchesConfiguredBaseUrl() throws Exception {
        String user = "alice";
        char[] password = "secret".toCharArray();
        String expectedAuth = "Basic " + Base64.getEncoder()
                .encodeToString((user + ":" + new String(password)).getBytes(StandardCharsets.ISO_8859_1));

        AtomicBoolean authSeen = new AtomicBoolean(false);
        String path = "/nexus/service/rest/v1/search/assets/download";

        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext(path, exchange -> {
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            if (!expectedAuth.equals(auth)) {
                exchange.sendResponseHeaders(401, -1);
                exchange.close();
                return;
            }

            authSeen.set(true);
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        int port = server.getAddress().getPort();
        URI uri = URI.create("http://localhost:" + port + path + "?name=auth-ok.jar");
        NexusConfig nexusConfig = new NexusConfig("http://localhost:" + port + "/nexus", user, password);

        File downloaded = null;
        try {
            downloaded = DownloadUtil.downloadToTemp(uri, null, nexusConfig);
            assertTrue(authSeen.get(), "Authorization header should be sent for matching Nexus download URL");
            assertEquals("ok", Files.readString(downloaded.toPath(), StandardCharsets.UTF_8));
        } finally {
            if (downloaded != null) {
                Files.deleteIfExists(downloaded.toPath());
            }
        }
    }

    @Test
    void downloadToTemp_doesNotApplyNexusAuthorizationWhenUriDoesNotMatchConfiguredBaseUrl() throws Exception {
        AtomicBoolean authSeen = new AtomicBoolean(false);
        String path = "/repository/download/no-auth.jar";

        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext(path, exchange -> {
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            if (auth != null) {
                authSeen.set(true);
            }

            byte[] body = "plain".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        int port = server.getAddress().getPort();
        URI uri = URI.create("http://localhost:" + port + path);
        NexusConfig nexusConfig = new NexusConfig("http://localhost:" + port + "/nexus", "alice", "secret".toCharArray());

        File downloaded = null;
        try {
            downloaded = DownloadUtil.downloadToTemp(uri, null, nexusConfig);
            assertFalse(authSeen.get(), "Authorization header must not be sent to non-Nexus paths");
            assertEquals("plain", Files.readString(downloaded.toPath(), StandardCharsets.UTF_8));
        } finally {
            if (downloaded != null) {
                Files.deleteIfExists(downloaded.toPath());
            }
        }
    }
}
