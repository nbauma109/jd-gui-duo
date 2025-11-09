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

import org.jd.gui.security.SecureSession;
import org.jd.gui.service.preferencespanel.ProxyPreferencesProvider;
import org.jd.gui.service.preferencespanel.secure.SecurePreferences;

import java.awt.Component;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Map;

/**
 * ProxyConfigHelper
 *
 * We reconstruct a ProxyConfig from preferences storage.
 * We read host and port in clear text. We decrypt user and password
 * only if needed. We prompt the user for the master password if decryption
 * is required. We never persist clear text credentials here.
 *
 * A null return value means no usable proxy configuration was found.
 */
public final class ProxyConfigHelper {

    private ProxyConfigHelper() {
        // Utility class: no instances
    }

    public static ProxyConfig fromPreferences(Map<String,String> prefs, Component component) {

        // Host and port are plain text
        final String host = trimToNull(prefs.get(ProxyPreferencesProvider.PROXY_HOST));
        final String portText = trimToNull(prefs.get(ProxyPreferencesProvider.PROXY_PORT));

        Integer port = null;
        if (portText != null) {
            try {
                final int p = Integer.parseInt(portText);
                if (p >= 1 && p <= 65535) {
                    port = p;
                }
            } catch (NumberFormatException ignored) {
            }
        }

        // Encrypted values (possibly empty)
        final String userEnc = trimToNull(prefs.get(ProxyPreferencesProvider.PROXY_USER_ENC));
        final String passEnc = trimToNull(prefs.get(ProxyPreferencesProvider.PROXY_PASS_ENC));

        String username = null;
        char[] password = null;

        if (userEnc != null || passEnc != null) {
            final char[] master = SecureSession.get().requireForLoad(component);
            if (master != null && master.length > 0) {
                try {
                    if (userEnc != null) {
                        username = SecurePreferences.decrypt(master, userEnc);
                        if (username != null && username.isEmpty()) {
                            username = null;
                        }
                    }
                    if (passEnc != null) {
                        final String passPlain = SecurePreferences.decrypt(master, passEnc);
                        if (passPlain != null && !passPlain.isEmpty()) {
                            password = passPlain.toCharArray();
                        }
                    }
                } catch (GeneralSecurityException ignored) {
                    username = null;
                    password = null;
                } finally {
                    Arrays.fill(master, '\0');
                }
            }
        }

        // If no host, no proxy
        if (host == null) {
            return null;
        }

        return new ProxyConfig(host, port, username, password);
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
