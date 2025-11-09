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

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.jd.gui.api.API;
import org.jd.gui.util.maven.central.helper.NexusConfig;
import org.jd.gui.util.maven.central.helper.NexusConfigHelper;
import org.jd.gui.util.maven.central.helper.ProxyConfig;
import org.jd.gui.util.maven.central.helper.ProxyConfigHelper;

import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.net.UnknownHostException;
/**
 * We provide helpers to download artifacts to the system temporary directory,
 * using a filename inferred from the URI and its query parameters.
 *
 * Proxy logic:
 *   - If a Nexus configuration is present, we consider it intranet and never use a proxy.
 *   - If no Nexus configuration is present and a proxy host is configured, we use that proxy.
 */
public final class DownloadUtil {

    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 30_000;

    private DownloadUtil() {
        // Utility class, no instances
    }

    /**
     * We download the given URI into the system temporary directory,
     * using a filename derived from the URI and its parameters.
     *
     * Proxy policy:
     *   - We inspect preferences via API.getPreferences().
     *   - If NexusConfigHelper returns a non-null NexusConfig, we never use a proxy.
     *   - Otherwise, if ProxyConfigHelper returns a valid ProxyConfig, we route through it.
     *
     * @param uri      the remote resource URI
     * @param api      the JD-GUI API, used to access preferences
     * @param parent   optional parent component for password dialogs (may be null)
     * @return the downloaded temporary file
     */
    public static File downloadToTemp(URI uri, API api, Component parent) throws IOException {
        if (uri == null) {
            throw new IllegalArgumentException("URI must not be null");
        }
        if (api == null) {
            throw new IllegalArgumentException("API must not be null");
        }

        Map<String, String> prefs = api.getPreferences();

        NexusConfig nexusConfig = NexusConfigHelper.fromPreferences(prefs, parent);
        ProxyConfig proxyConfig = null;
        if (nexusConfig == null) {
            proxyConfig = ProxyConfigHelper.fromPreferences(prefs, parent);
            if (proxyConfig != null && !proxyConfig.isValid()) {
                proxyConfig = null;
            }
        }

        return downloadToTemp(uri, proxyConfig);
    }

    /**
     * We download using an optional explicit proxy configuration.
     */
    public static File downloadToTemp(URI uri, ProxyConfig proxyConfig) throws IOException {
        if (uri == null) {
            throw new IllegalArgumentException("URI must not be null");
        }
        if (!isSafeRemoteUri(uri)) {
            throw new IOException("Refusing to download from potentially unsafe or private address: " + uri);
        }

        String fileName = resolveFileName(uri);
        if (StringUtils.isBlank(fileName)) {
            fileName = "download.bin";
        }

        Path destPath = Path.of(System.getProperty("java.io.tmpdir"), fileName);
        File destFile = destPath.toFile();

        URL url = uri.toURL();

        Proxy proxy = Proxy.NO_PROXY;
        String proxyAuth = null;
        if (proxyConfig != null && proxyConfig.isValid()) {
            proxy = proxyConfig.toJavaNetProxy();
            proxyAuth = proxyConfig.basicAuthHeaderValue();
        }

        HttpURLConnection conn = (HttpURLConnection) url.openConnection(proxy);
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setInstanceFollowRedirects(true);
        if (proxyAuth != null) {
            conn.setRequestProperty("Proxy-Authorization", proxyAuth);
        }

        int code = conn.getResponseCode();
        if (code >= 400) {
            InputStream err = conn.getErrorStream();
            if (err != null) {
                err.close();
            }
            throw new IOException("HTTP " + code + " while downloading " + uri);
        }

        try (InputStream in = conn.getInputStream()) {
            FileUtils.copyInputStreamToFile(in, destFile);
        }

        return destFile;
    }

    /**
     * We derive a reasonable filename from the URI.
     *
     * Rules:
     *   1) If there is a "filepath" parameter, we treat it as an encoded path and
     *      take the final segment (search.maven.org remotecontent).
     *   2) If there are name/version/extension (and optional classifier) parameters
     *      from Nexus 3 asset download, we build name-version[-classifier].extension.
     *   3) If there are a/v/p (and optional c) parameters from Nexus 2 maven/content,
     *      we build a-v[-c].p.
     *   4) Otherwise, we fall back to the last segment of the URI path.
     */
    public static String resolveFileName(URI uri) {

        Map<String, String> params = parseQuery(uri.getRawQuery());

        // Case 1: search.maven.org remotecontent with "filepath" parameter
        String filepath = params.get("filepath");
        if (StringUtils.isNotBlank(filepath)) {
            String decoded = urlDecode(filepath);
            int slash = decoded.lastIndexOf('/');
            return (slash >= 0 ? decoded.substring(slash + 1) : decoded);
        }

        // Case 2: Nexus 3 asset download
        String name = params.get("name");
        String version = params.get("version");
        String extension = params.get("extension");
        String classifier = params.get("classifier");

        if (StringUtils.isNotBlank(name)
                && StringUtils.isNotBlank(version)
                && StringUtils.isNotBlank(extension)) {

            StringBuilder sb = new StringBuilder();
            sb.append(name).append('-').append(version);
            if (StringUtils.isNotBlank(classifier)) {
                sb.append('-').append(classifier);
            }
            sb.append('.').append(extension);
            return sb.toString();
        }

        // Case 3: Nexus 2 maven/content
        String a = params.get("a");
        String v = params.get("v");
        String p = params.get("p");
        String c = params.get("c");

        if (StringUtils.isNotBlank(a)
                && StringUtils.isNotBlank(v)
                && StringUtils.isNotBlank(p)) {

            StringBuilder sb = new StringBuilder();
            sb.append(a).append('-').append(v);
            if (StringUtils.isNotBlank(c)) {
                sb.append('-').append(c);
            }
            sb.append('.').append(p);
            return sb.toString();
        }

        // Fallback: last segment of the URI path
        String path = uri.getPath();
        if (StringUtils.isNotBlank(path)) {
            int slash = path.lastIndexOf('/');
            String candidate = (slash >= 0 ? path.substring(slash + 1) : path);
            if (StringUtils.isNotBlank(candidate)) {
                return candidate;
            }
        }

        return null;
    }

    /**
     * We parse a query string into a Map<String,String> of decoded parameters.
     * We keep the first occurrence of each parameter name.
     */
    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> params = new LinkedHashMap<>();
        if (StringUtils.isBlank(rawQuery)) {
            return params;
        }

        String[] pairs = rawQuery.split("&");
        for (String pair : pairs) {
            if (pair.isEmpty()) {
                continue;
            }
            int idx = pair.indexOf('=');
            String rawKey = (idx >= 0 ? pair.substring(0, idx) : pair);
            String rawValue = (idx >= 0 ? pair.substring(idx + 1) : "");
            String key = urlDecode(rawKey);
            String value = urlDecode(rawValue);

            // Using computeIfAbsent instead of containsKey
            params.computeIfAbsent(key, k -> value);
        }
        return params;
    }

    private static String urlDecode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }
    /**
     * Prevent SSRF by blocking local and private addresses.
     * Allows only URIs with valid host that do not resolve to loopback or private/local addresses.
     */
    private static boolean isSafeRemoteUri(URI uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme();
        if (scheme == null) return false;
        if (!(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            return false;
        }
        String host = uri.getHost();
        if (host == null || host.isEmpty()) return false;
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress addr : addresses) {
                if (addr.isAnyLocalAddress() || addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isLinkLocalAddress() || addr.isMulticastAddress()) {
                    return false;
                }
                // IPv6 localhost (::1)
                if ("0:0:0:0:0:0:0:1".equalsIgnoreCase(addr.getHostAddress())) {
                    return false;
                }
            }
        } catch (UnknownHostException e) {
            return false;
        }
        // Optionally: allow-list domains here
        return true;
    }
}
