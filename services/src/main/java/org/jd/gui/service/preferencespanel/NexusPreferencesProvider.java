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
import org.jd.gui.spi.SecuredPreferencesPanel;
import org.jd.gui.util.ImageUtil;

import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.JPanel;
import javax.swing.JComponent;
import javax.swing.JButton;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.Arrays;

/**
 * NexusPreferencesProvider
 *
 * This panel allows the user to configure authenticated Nexus repository access.
 *
 * Security design is identical to ProxyPreferencesProvider:
 * - We do not persist credentials in clear text.
 * - We encrypt credentials with a session-bound master password.
 * - We do not expose explicit master password controls in the interface.
 *
 * Validation rules:
 * - Base URL must be syntactically valid when any field is filled.
 * - If username is provided, password must also be provided.
 *   We highlight only the password field when missing.
 *
 * Intent:
 * - Maintain visual, behavioral, and architectural parity with the proxy panel.
 * - Keep the panel compact, similar to the original JD-GUI preference panels.
 */
public final class NexusPreferencesProvider extends JPanel implements SecuredPreferencesPanel, ActionListener, DocumentListener {

    private static final long serialVersionUID = 1L;

    // Preference keys
    public static final String NEXUS_URL = "NexusPreferencesProvider.nexus.url";
    public static final String NEXUS_USER_ENC = "NexusPreferencesProvider.nexus.user.enc";
    public static final String NEXUS_PASS_ENC = "NexusPreferencesProvider.nexus.pass.enc";
    public static final String VAULT_PRESENT = "NexusPreferencesProvider.vault.present";

    // User interface
    private JTextField urlField;
    private JTextField userField;
    private JPasswordField passField;
    private JButton clearButton;

    // State
    private Color errorBackground = new Color(255, 210, 210);
    private Color urlDefaultBackground;
    private Color passDefaultBackground;

    private transient PreferencesPanelChangeListener listener;

    public NexusPreferencesProvider() {
        super(new BorderLayout());

        urlField = new JTextField(28);
        urlField.getDocument().addDocumentListener(this);
        urlDefaultBackground = urlField.getBackground();

        userField = new JTextField(12);
        userField.getDocument().addDocumentListener(this);

        passField = new JPasswordField(12);
        passField.getDocument().addDocumentListener(this);
        passDefaultBackground = passField.getBackground();

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(null);

        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(2, 4, 2, 4);
        gc.anchor = GridBagConstraints.WEST;
        gc.fill = GridBagConstraints.HORIZONTAL;

        int col = 0;
        // Row 0: base url
        gc.gridx = col++;
        gc.gridy = 0;
        gc.weightx = 0;
        gc.fill = GridBagConstraints.NONE;
        form.add(new JLabel("Base URL:"), gc);
        gc.gridx = col++;
        gc.weightx = 1;
        gc.gridwidth = 4;
        gc.fill = GridBagConstraints.HORIZONTAL;
        form.add(urlField, gc);
        gc.gridwidth = 1;
        gc.weightx = 0;
        gc.fill = GridBagConstraints.NONE;

        // Row 1: credentials
        col = 0;
        gc.gridx = col++;
        gc.gridy = 1;
        form.add(new JLabel("Username:"), gc);
        gc.gridx = col++;
        form.add(userField, gc);
        gc.gridx = col++;
        form.add(new JLabel("Password:"), gc);
        gc.gridx = col++;
        form.add(passField, gc);

        add(form, BorderLayout.CENTER);

        // Buttons row (Clear)
        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
        clearButton = new JButton("Clear", ImageUtil.newImageIcon("/org/jd/gui/images/close_active.gif"));
        clearButton.addActionListener(this);
        buttonsPanel.add(clearButton);
        add(buttonsPanel, BorderLayout.SOUTH);
    }

    // PreferencesPanel
    @Override
    public String getPreferencesGroupTitle() {
        return "Nexus Sonatype";
    }

    @Override
    public String getPreferencesPanelTitle() {
        return "Intranet artifact search supersedes proxy-based internet search";
    }

    @Override
    public JComponent getPanel() {
        return this;
    }

    @Override
    public void init(Color errorBackgroundColor) {
        if (errorBackgroundColor != null) {
            this.errorBackground = errorBackgroundColor;
        }
    }

    @Override
    public boolean isActivated() {
        return true;
    }

    @Override
    public void loadPreferences(Map<String, String> preferences) {
        urlField.setText(nvl(preferences.get(NEXUS_URL)));

        boolean hasCipher = hasNonEmpty(preferences, NEXUS_USER_ENC) || hasNonEmpty(preferences, NEXUS_PASS_ENC);
        if (hasCipher) {
            char[] master = SecureSession.get().requireForLoad(this);
            if (master != null && master.length > 0) {
                try {
                    if (hasNonEmpty(preferences, NEXUS_USER_ENC)) {
                        userField.setText(SecurePreferences.decrypt(master, preferences.get(NEXUS_USER_ENC)));
                    }
                    if (hasNonEmpty(preferences, NEXUS_PASS_ENC)) {
                        passField.setText(SecurePreferences.decrypt(master, preferences.get(NEXUS_PASS_ENC)));
                    }
                } catch (GeneralSecurityException ex) {
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
        preferences.put(NEXUS_URL, urlField.getText().trim());

        boolean haveUser = hasText(userField.getText());
        boolean havePass = passField.getPassword().length > 0;

        if (haveUser || havePass) {
            char[] master = SecureSession.get().requireForSave(this);
            if (master != null && master.length > 0) {
                try {
                    preferences.put(NEXUS_USER_ENC, SecurePreferences.encrypt(master, nvl(userField.getText())));
                    preferences.put(NEXUS_PASS_ENC, SecurePreferences.encrypt(master, new String(passField.getPassword())));
                    preferences.put(VAULT_PRESENT, "true");
                } catch (GeneralSecurityException ex) {
                    // We do not write clear text on failure
                } finally {
                    zero(passField.getPassword());
                    Arrays.fill(master, '\0');
                }
            }
        } else {
            preferences.put(NEXUS_USER_ENC, "");
            preferences.put(NEXUS_PASS_ENC, "");
        }
    }

    @Override
    public boolean arePreferencesValid() {
        boolean anyFilled = hasText(urlField.getText())
                || hasText(userField.getText())
                || passField.getPassword().length > 0;

        if (!anyFilled) {
            urlField.setBackground(urlDefaultBackground);
            passField.setBackground(passDefaultBackground);
            return true;
        }

        boolean urlOk = !hasText(urlField.getText()) || validateUrl(urlField.getText());
        urlField.setBackground(urlOk ? urlDefaultBackground : errorBackground);

        // Rule: if username is filled, password must be filled. We highlight the password field only.
        boolean userProvided = hasText(userField.getText());
        boolean passProvided = passField.getPassword().length > 0;
        boolean passOk = (!userProvided || passProvided);
        passField.setBackground(passOk ? passDefaultBackground : errorBackground);

        return urlOk && passOk;
    }

    @Override
    public void addPreferencesChangeListener(PreferencesPanelChangeListener listener) {
        this.listener = listener;
    }

    // ActionListener
    @Override
    public void actionPerformed(ActionEvent e) {
        Object source = e.getSource();
        if (source == clearButton) {
            // Clear all fields when the clear button is pressed
            restoreDefaults();
        }

        if (listener != null) {
            listener.preferencesPanelChanged(this);
        }
    }

    @Override
    public void restoreDefaults() {
        urlField.setText("");
        userField.setText("");
        passField.setText("");
        arePreferencesValid();
    }

    // DocumentListener
    @Override
    public void insertUpdate(DocumentEvent e) {
        fireChange();
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        fireChange();
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
        fireChange();
    }

    private void fireChange() {
        arePreferencesValid();
        if (listener != null) {
            listener.preferencesPanelChanged(this);
        }
    }

    // Helpers
    private static boolean validateUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        return url.matches("https?://.+");
    }

    private static boolean hasNonEmpty(Map<String, String> m, String k) {
        String v = m.get(k);
        return v != null && !v.isEmpty();
    }

    private static boolean hasText(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    private static void zero(char[] a) {
        if (a == null) {
            return;
        }
        Arrays.fill(a, '\0');
    }

    @Override
    public boolean useCompactDisplay() {
        return false;
    }
}
