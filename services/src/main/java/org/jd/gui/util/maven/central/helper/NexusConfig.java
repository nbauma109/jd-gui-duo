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

/**
 * We carry minimal connection information for a Nexus server.
 * We keep credentials optional and do not perform any storage here.
 */
public final class NexusConfig {
    public final String baseUrl;   // Example: https://nexus.example.com
    public final String username;  // Optional
    public final char[] password;  // Optional

    public NexusConfig(String baseUrl, String username, char[] password) {
        this.baseUrl = trimToNull(baseUrl);
        this.username = trimToNull(username);
        this.password = password;
    }

    public boolean isPresent() { return baseUrl != null; }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
