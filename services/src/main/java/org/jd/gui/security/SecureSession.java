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
package org.jd.gui.security;

import org.jd.gui.service.preferencespanel.secure.MasterPasswordDialog;

import javax.swing.SwingUtilities;
import java.awt.Component;
import java.util.Arrays;

/**
 * SecureSession
 *
 * We keep a single, shared in-memory master password for the whole application session.
 * We expose helper methods that prompt at most once per need, so multiple panels do not
 * ask the user repeatedly. We never persist the master password.
 */
public final class SecureSession {

    private static final SecureSession INSTANCE = new SecureSession();

    private final AtomicCharArrayVault vault = new AtomicCharArrayVault();

    // We use a small guard to prevent concurrent, duplicate prompts.
    private final Object promptLock = new Object();
    private volatile boolean promptInProgress = false;

    private SecureSession() {}

    public static SecureSession get() {
        return INSTANCE;
    }

    /**
     * Returns a snapshot of the current master password for decryption.
     * If not set, we prompt once to UNLOCK. If the user cancels, we return null.
     */
    public char[] requireForLoad(Component parent) {
        char[] cur = vault.snapshot();
        if (cur != null && cur.length > 0) {
            return cur;
        }
        synchronized (promptLock) {
            if (promptInProgress) {
                return null; // Another prompt is already happening; caller can retry later if needed.
            }
            promptInProgress = true;
            try {
                MasterPasswordDialog dlg = new MasterPasswordDialog(
                        SwingUtilities.getWindowAncestor(parent),
                        MasterPasswordDialog.Mode.UNLOCK
                );
                dlg.setVisible(true);
                char[] pwd = dlg.getPassword();
                if (pwd == null || pwd.length == 0) {
                    return null;
                }
                vault.set(pwd);
                Arrays.fill(pwd, '\0');
                return vault.snapshot();
            } finally {
                promptInProgress = false;
            }
        }
    }

    /**
     * Returns a snapshot of the current master password for encryption.
     * If not set, we prompt once to SETUP. If the user cancels, we return null.
     */
    public char[] requireForSave(Component parent) {
        char[] cur = vault.snapshot();
        if (cur != null && cur.length > 0) {
            return cur;
        }
        synchronized (promptLock) {
            if (promptInProgress) {
                return null;
            }
            promptInProgress = true;
            try {
                MasterPasswordDialog dlg = new MasterPasswordDialog(
                        SwingUtilities.getWindowAncestor(parent),
                        MasterPasswordDialog.Mode.SETUP
                );
                dlg.setVisible(true);
                char[] pwd = dlg.getPassword();
                if (pwd == null || pwd.length == 0) {
                    return null;
                }
                vault.set(pwd);
                Arrays.fill(pwd, '\0');
                return vault.snapshot();
            } finally {
                promptInProgress = false;
            }
        }
    }

    /**
     * Clears the in-memory master password.
     */
    public void clear() {
        vault.clear();
    }
}
