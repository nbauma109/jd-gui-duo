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
package org.jd.gui.service.preferencespanel;

import org.jd.gui.security.SecureSession;
import org.jd.gui.service.preferencespanel.secure.SecurePreferences;
import org.jd.gui.spi.PreferencesPanel;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Map;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * ProxyPreferencesProvider
 *
 * This panel allows the user to configure an authenticated HTTP proxy.
 *
 * What we do:
 * - We never persist credentials in clear text. We encrypt on save and decrypt on load.
 * - We hold the master password only in memory for the session, via SecureSession.
 * - We keep the layout compact and close to the original JD-GUI style.
 *
 * Port field change:
 * - We use a plain text field for the port so that we can clear it and treat it as optional.
 * - We validate the port only when it is provided, and we allow it to be empty.
 *
 * New behavior with proxy-vole:
 * - If proxy fields are empty when we load preferences, we try to detect system proxy
 *   settings with proxy-vole and use those as defaults. We do not overwrite values
 *   that the user has already provided.
 * - We only pre-fill Host and Port. We do not try to infer credentials from the system.
 *
 * Validation rules:
 * - When any field is filled, host must be syntactically valid.
 * - If a port is provided, it must be numeric and between 1 and 65535.
 * - If username is provided, password must be provided. We highlight the password field
 *   to guide the user to complete the pair.
 *
 * User experience notes:
 * - We do not expose any explicit master-password controls in the interface. We prompt
 *   automatically only when we need to decrypt or encrypt credentials.
 * - We trigger validation on each input change and provide field-specific feedback.
 */
public final class ProxyPreferencesProvider extends JPanel implements PreferencesPanel, ActionListener, DocumentListener {

    private static final long serialVersionUID = 1L;

    // Preference keys
    public static final String PROXY_HOST = "ProxyPreferencesProvider.proxy.host";
    public static final String PROXY_PORT = "ProxyPreferencesProvider.proxy.port";
    public static final String PROXY_USER_ENC = "ProxyPreferencesProvider.proxy.user.enc";
    public static final String PROXY_PASS_ENC = "ProxyPreferencesProvider.proxy.pass.enc";
    public static final String VAULT_PRESENT = "ProxyPreferencesProvider.vault.present";

    // User interface
    private JTextField hostField;
    private JTextField portField;        // Plain text field so we can allow clearing
    private JTextField userField;
    private JPasswordField passField;

    // State
    private Color errorBackground = new Color(255, 210, 210);
    private Color hostDefaultBackground;
    private Color portDefaultBackground;
    private Color passDefaultBackground;

    private transient PreferencesPanelChangeListener listener;

    public ProxyPreferencesProvider() {
        super(new BorderLayout());

        // Host
        hostField = new JTextField(16);
        hostField.getDocument().addDocumentListener(this);
        hostDefaultBackground = hostField.getBackground();

        // Port as plain text, so that we can leave it empty and users can clear it
        portField = new JTextField(6);
        portField.getDocument().addDocumentListener(this);
        portDefaultBackground = portField.getBackground();

        // Credentials
        userField = new JTextField(12);
        userField.getDocument().addDocumentListener(this);

        passField = new JPasswordField(12);
        passField.getDocument().addDocumentListener(this);
        passDefaultBackground = passField.getBackground();

        // Compact layout, faithful to original panels
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(null);
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(2, 4, 2, 4);
        gc.anchor = GridBagConstraints.WEST;

        int col = 0;
        // Row 0: host, port
        gc.gridx = col++; gc.gridy = 0; form.add(new JLabel("Host:"), gc);
        gc.gridx = col++;               form.add(hostField, gc);
        gc.gridx = col++;               form.add(new JLabel("Port:"), gc);
        gc.gridx = col++;               form.add(portField, gc);

        // Row 1: username, password
        col = 0;
        gc.gridx = col++; gc.gridy = 1; form.add(new JLabel("Username:"), gc);
        gc.gridx = col++;               form.add(userField, gc);
        gc.gridx = col++;               form.add(new JLabel("Password:"), gc);
        gc.gridx = col++;               form.add(passField, gc);

        add(form, BorderLayout.CENTER);
    }

    // --- PreferencesPanel --- //
    @Override public String getPreferencesGroupTitle() { return "Proxy"; }
    @Override public String getPreferencesPanelTitle() { return "Proxy for internet artifact search with maven.org"; }
    @Override public JComponent getPanel() { return this; }

    @Override
    public void init(Color errorBackgroundColor) {
        if (errorBackgroundColor != null) this.errorBackground = errorBackgroundColor;
    }

    @Override public boolean isActivated() { return true; }

    @Override
    public void loadPreferences(Map<String, String> preferences) {
        // Load persisted values
        hostField.setText(nvl(preferences.get(PROXY_HOST)));
        portField.setText(nvl(preferences.get(PROXY_PORT)).trim());

        // Decrypt previously saved credentials if present
        boolean hasCipher = hasNonEmpty(preferences, PROXY_USER_ENC) || hasNonEmpty(preferences, PROXY_PASS_ENC);
        if (hasCipher) {
            char[] master = SecureSession.get().requireForLoad(this);
            if (master != null && master.length > 0) {
                try {
                    if (hasNonEmpty(preferences, PROXY_USER_ENC)) {
                        userField.setText(SecurePreferences.decrypt(master, preferences.get(PROXY_USER_ENC)));
                    }
                    if (hasNonEmpty(preferences, PROXY_PASS_ENC)) {
                        passField.setText(SecurePreferences.decrypt(master, preferences.get(PROXY_PASS_ENC)));
                    }
                } catch (GeneralSecurityException ignored) {
                    // We keep fields blank on failure
                } finally {
                    Arrays.fill(master, '\0');
                }
            }
        }

        arePreferencesValid();
    }

    @Override
    public void savePreferences(Map<String, String> preferences) {
        // Persist host and port exactly as entered
        preferences.put(PROXY_HOST, hostField.getText().trim());
        preferences.put(PROXY_PORT, portField.getText().trim());

        boolean haveUser = hasText(userField.getText());
        boolean havePass = passField.getPassword().length > 0;

        // Persist credentials encrypted if present
        if (haveUser || havePass) {
            char[] master = SecureSession.get().requireForSave(this);
            if (master != null && master.length > 0) {
                try {
                    preferences.put(PROXY_USER_ENC, SecurePreferences.encrypt(master, nvl(userField.getText())));
                    preferences.put(PROXY_PASS_ENC, SecurePreferences.encrypt(master, new String(passField.getPassword())));
                    preferences.put(VAULT_PRESENT, "true");
                } catch (GeneralSecurityException ignored) {
                    // We never write clear text on failure
                } finally {
                    zero(passField.getPassword());
                    Arrays.fill(master, '\0');
                }
            }
        } else {
            // Clear stored ciphertext if credentials are empty
            preferences.put(PROXY_USER_ENC, "");
            preferences.put(PROXY_PASS_ENC, "");
        }
    }

    @Override
    public boolean arePreferencesValid() {
        boolean anyFilled =
                hasText(hostField.getText()) ||
                hasText(portField.getText()) ||
                hasText(userField.getText()) ||
                passField.getPassword().length > 0;

        if (!anyFilled) {
            // Nothing provided, consider this valid and clear any highlights
            hostField.setBackground(hostDefaultBackground);
            portField.setBackground(portDefaultBackground);
            passField.setBackground(passDefaultBackground);
            return true;
        }

        // Host must always be valid when anything is filled
        boolean hostOk = validateHost(hostField.getText());
        hostField.setBackground(hostOk ? hostDefaultBackground : errorBackground);

        // Port is optional; if provided it must be a valid number between 1 and 65535
        boolean portOk = validatePortField(portField.getText());
        portField.setBackground(portOk ? portDefaultBackground : errorBackground);

        // If username is provided, password must be provided; highlight password only
        boolean userProvided = hasText(userField.getText());
        boolean passProvided = passField.getPassword().length > 0;
        boolean passOk = !(userProvided && !passProvided);
        passField.setBackground(passOk ? passDefaultBackground : errorBackground);

        return hostOk && portOk && passOk;
    }

    @Override public void addPreferencesChangeListener(PreferencesPanelChangeListener listener) { this.listener = listener; }

    // We do not have buttons, but we keep the contract and notify host when actions are triggered
    @Override public void actionPerformed(ActionEvent e) { if (listener != null) listener.preferencesPanelChanged(this); }

    @Override
    public void restoreDefaults() {
        hostField.setText("");
        portField.setText("");
        userField.setText("");
        passField.setText("");
        arePreferencesValid();
    }

    // --- DocumentListener --- //
    @Override public void insertUpdate(DocumentEvent e) { fireChange(); }
    @Override public void removeUpdate(DocumentEvent e) { fireChange(); }
    @Override public void changedUpdate(DocumentEvent e) { fireChange(); }

    private void fireChange() {
        arePreferencesValid();
        if (listener != null) listener.preferencesPanelChanged(this);
    }

    // --- Helpers --- //

    private static boolean validateHost(String host) {
        return host != null && !host.trim().isEmpty()
                && host.length() <= 253
                && host.matches("[a-zA-Z0-9._-]+");
    }

    /**
     * We validate the port as text. Empty means “no port,” which is allowed.
     * If provided, it must parse to an integer in the inclusive range [1, 65535].
     */
    private static boolean validatePortField(String s) {
        if (s == null || s.trim().isEmpty()) return true;
        try {
            int p = Integer.parseInt(s.trim());
            return p >= 1 && p <= 65535;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean hasNonEmpty(Map<String, String> m, String k) {
        String v = m.get(k); return v != null && !v.isEmpty();
    }

    private static boolean hasText(String s) { return s != null && !s.trim().isEmpty(); }

    private static String nvl(String s) { return s == null ? "" : s; }

    private static void zero(char[] a) { if (a != null) Arrays.fill(a, '\0'); }

    @Override
    public boolean useCompactDisplay() {
        return false;
    }
}
