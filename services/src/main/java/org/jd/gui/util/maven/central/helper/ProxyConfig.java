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
package org.jd.gui.util.maven.central.helper;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

/**
 * Immutable proxy configuration holder. A null reference means no proxy.
 * We keep this simple and self-contained for ease of use.
 */
public final class ProxyConfig {
    public final String host;
    public final Integer port;     // Nullable to allow host-only proxies if needed
    public final String username;  // Optional
    public final char[] password;  // Optional, we will not persist this here

    public ProxyConfig(String host, Integer port, String username, char[] password) {
        this.host = (host == null || host.trim().isEmpty()) ? null : host.trim();
        this.port = port;
        this.username = (username == null || username.trim().isEmpty()) ? null : username.trim();
        this.password = password; // We assume the caller manages secure wiping if desired
    }

    public boolean isValid() {
        return host != null && (port == null || (port >= 1 && port <= 65535));
    }

    public Proxy toJavaNetProxy() {
        if (!isValid()) {
            return Proxy.NO_PROXY;
        }
        int p = (port == null) ? 80 : port;
        return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, p));
    }

    public String basicAuthHeaderValue() {
        if (username == null || password == null || password.length == 0) {
            return null;
        }
        String creds = username + ":" + new String(password);
        String encoded = Base64.getEncoder().encodeToString(creds.getBytes(StandardCharsets.ISO_8859_1));
        // We do not keep the concatenated string around longer than needed
        Arrays.fill(creds.toCharArray(), '\0');
        return "Basic " + encoded;
    }
}