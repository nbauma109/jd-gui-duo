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
package org.jd.gui.util.central;

import org.jd.gui.util.maven.central.helper.ProxyConfig;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URI;
import java.net.URL;

public interface ProxyCapable {

    int CONNECT_TIMEOUT_MS = 8_000;
    int READ_TIMEOUT_MS = 60_000;

    default HttpURLConnection open(String urlString, String method, ProxyConfig proxyConfig) throws IOException {
        URL url = URI.create(urlString).toURL();

        HttpURLConnection conn;
        if (proxyConfig != null && proxyConfig.isValid()) {
            conn = (HttpURLConnection) url.openConnection(proxyConfig.toJavaNetProxy());
            String proxyAuth = proxyConfig.basicAuthHeaderValue();
            if (proxyAuth != null) {
                conn.setRequestProperty("Proxy-Authorization", proxyAuth);
            }
        } else {
            conn = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY);
        }

        conn.setRequestMethod(method);
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "JD-GUI-CentralSearch/1.0");

        return conn;
    }
}
