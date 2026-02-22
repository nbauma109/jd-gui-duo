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
package org.jd.gui.util.nexus;

import org.apache.commons.io.IOUtils;
import org.jd.gui.util.maven.central.helper.NexusConfig;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * We provide common HTTP helpers shared by version-specific clients.
 * We never use proxies and we set modest timeouts.
 */
abstract class AbstractNexusClient {

    protected final NexusConfig config;

    protected AbstractNexusClient(NexusConfig config) {
        this.config = config;
    }

    protected String get(String absoluteUrl, int connectTimeoutMs, int readTimeoutMs) throws java.io.IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(absoluteUrl).toURL().openConnection(Proxy.NO_PROXY);
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(connectTimeoutMs);
        conn.setReadTimeout(readTimeoutMs);
        conn.setRequestProperty("Accept", "*/*");
        conn.setRequestProperty("User-Agent", "JD-GUI-NexusSearch/1.0");
        applyBasicAuthIfAny(conn);
        int code = conn.getResponseCode();
        InputStream stream = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        String body = IOUtils.toString(stream, StandardCharsets.UTF_8);
        if (code >= 200 && code < 300) {
            return body;
        }
        throw new java.io.IOException("HTTP " + code + " from " + absoluteUrl + (body == null ? "" : (": " + body)));
    }

    protected String buildUrl(String base, String pathAndQuery) {
        String b = trimTrailingSlash(base);
        return b + pathAndQuery;
    }

    protected static String enc(String s) {
        try { return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8.name()); }
        catch (Exception e) { return s; }
    }

    protected static String trimTrailingSlash(String base) {
        if (base == null) {
            return null;
        }
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private void applyBasicAuthIfAny(HttpURLConnection conn) {
        if (config.username != null && config.password != null && config.password.length > 0) {
            String creds = config.username + ":" + new String(config.password);
            String enc = Base64.getEncoder().encodeToString(creds.getBytes(StandardCharsets.ISO_8859_1));
            conn.setRequestProperty("Authorization", "Basic " + enc);
        }
    }
}
