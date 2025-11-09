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
import org.jd.gui.service.preferencespanel.NexusPreferencesProvider;
import org.jd.gui.service.preferencespanel.secure.SecurePreferences;

import java.awt.Component;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Map;

/**
 * NexusConfigHelper
 *
 * We reconstruct a NexusConfig from preferences storage.
 * We read the Nexus base URL in clear text. We decrypt user and password
 * only if needed. We prompt the user for the master password if decryption
 * is required. We never persist clear text credentials here.
 *
 * A null return value means no usable Nexus configuration was found.
 */
public final class NexusConfigHelper {

    private NexusConfigHelper() {
        // Utility class: no instances
    }

    public static NexusConfig fromPreferences(Map<String, String> prefs, Component component) {

        final String url = trimToNull(prefs.get(NexusPreferencesProvider.NEXUS_URL));

        // Encrypted values (possibly empty)
        final String userEnc = trimToNull(prefs.get(NexusPreferencesProvider.NEXUS_USER_ENC));
        final String passEnc = trimToNull(prefs.get(NexusPreferencesProvider.NEXUS_PASS_ENC));

        String username = null;
        char[] password = null;

        // We only bother with the master password if there is something to decrypt
        if (userEnc != null || passEnc != null) {
            final char[] master = SecureSession.get().requireForLoad(component);

            if (master == null || master.length == 0) {
                // Optional stricter behaviour:
                // If encrypted values exist but the user refuses to unlock them,
                // we can consider the configuration unusable.
                // return null;
            }

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
                            char[] tmp = passPlain.toCharArray();
                            // We copy into our own char array in case callers want to wipe it later
                            password = Arrays.copyOf(tmp, tmp.length);
                            Arrays.fill(tmp, '\0');
                        }
                    }
                } catch (GeneralSecurityException ignored) {
                    // Any decryption error is treated as "no credentials"
                    username = null;
                    password = null;
                } finally {
                    Arrays.fill(master, '\0');
                }
            }
        }

        if (url == null) {
            return null;
        }

        return new NexusConfig(url, username, password);
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}