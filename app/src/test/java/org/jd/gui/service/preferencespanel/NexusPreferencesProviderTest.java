/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.preferencespanel;

import org.jd.gui.spi.PreferencesPanel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JTextField;

import static org.assertj.core.api.Assertions.assertThat;

/** The Nexus settings panel: what it loads, saves and accepts. */
class NexusPreferencesProviderTest {

    private static final Color ERROR = new Color(250, 0, 0);

    @AfterEach
    void lock() {
        PanelSupport.lock();
    }

    private static NexusPreferencesProvider panel() {
        NexusPreferencesProvider panel = new NexusPreferencesProvider();
        panel.init(ERROR);
        return panel;
    }

    private static JTextField url(NexusPreferencesProvider panel) { return PanelSupport.textFields(panel).get(0); }
    private static JTextField user(NexusPreferencesProvider panel) { return PanelSupport.textFields(panel).get(1); }

    @Test
    void thePanelIsTitledAndHasItsFields() {
        NexusPreferencesProvider panel = panel();

        assertThat(panel.getPreferencesGroupTitle()).isEqualTo("Nexus Sonatype");
        assertThat(panel.getPreferencesPanelTitle()).isEqualTo("Intranet artifact search supersedes proxy-based internet search");
        assertThat(PanelSupport.textFields(panel)).hasSize(2);
        assertThat(PanelSupport.passwordFields(panel)).hasSize(1);
        assertThat(panel.useCompactDisplay()).isFalse();
    }

    @Test
    void theSavedAddressIsLoadedBack() {
        NexusPreferencesProvider panel = panel();
        Map<String, String> preferences = new HashMap<>();
        preferences.put(NexusPreferencesProvider.NEXUS_URL, "https://nexus.example.com/repo");

        panel.loadPreferences(preferences);

        assertThat(url(panel).getText()).isEqualTo("https://nexus.example.com/repo");
        assertThat(panel.arePreferencesValid()).isTrue();
    }

    @Test
    void withoutCredentialsTheAddressIsSavedTrimmedAndTheStoredSecretsAreCleared() {
        NexusPreferencesProvider panel = panel();
        url(panel).setText("  http://nexus.example.com  ");
        Map<String, String> preferences = new HashMap<>();
        preferences.put(NexusPreferencesProvider.NEXUS_USER_ENC, "old");
        preferences.put(NexusPreferencesProvider.NEXUS_PASS_ENC, "old");

        panel.savePreferences(preferences);

        assertThat(preferences).containsEntry(NexusPreferencesProvider.NEXUS_URL, "http://nexus.example.com")
                .containsEntry(NexusPreferencesProvider.NEXUS_USER_ENC, "").containsEntry(NexusPreferencesProvider.NEXUS_PASS_ENC, "");
    }

    @Test
    void credentialsAreSavedEncryptedAndLoadedBackWithTheMasterPassword() throws Exception {
        PanelSupport.unlock("master-password");
        NexusPreferencesProvider panel = panel();
        url(panel).setText("https://nexus.example.com");
        user(panel).setText("alice");
        PanelSupport.passwordFields(panel).get(0).setText("s3cret");
        Map<String, String> preferences = new HashMap<>();

        panel.savePreferences(preferences);

        assertThat(preferences).containsEntry(NexusPreferencesProvider.VAULT_PRESENT, "true");
        assertThat(preferences.get(NexusPreferencesProvider.NEXUS_USER_ENC)).isNotBlank().doesNotContain("alice");
        assertThat(preferences.get(NexusPreferencesProvider.NEXUS_PASS_ENC)).isNotBlank().doesNotContain("s3cret");

        NexusPreferencesProvider reloaded = panel();
        reloaded.loadPreferences(preferences);
        assertThat(user(reloaded).getText()).isEqualTo("alice");
        assertThat(new String(PanelSupport.passwordFields(reloaded).get(0).getPassword())).isEqualTo("s3cret");
    }

    @Test
    void aWrongMasterPasswordLeavesTheCredentialsBlank() throws Exception {
        PanelSupport.unlock("master-password");
        NexusPreferencesProvider panel = panel();
        url(panel).setText("https://nexus.example.com");
        user(panel).setText("alice");
        PanelSupport.passwordFields(panel).get(0).setText("s3cret");
        Map<String, String> preferences = new HashMap<>();
        panel.savePreferences(preferences);

        PanelSupport.lock();
        PanelSupport.unlock("another-password");
        NexusPreferencesProvider reloaded = panel();
        reloaded.loadPreferences(preferences);

        assertThat(user(reloaded).getText()).isEmpty();
        assertThat(PanelSupport.passwordFields(reloaded).get(0).getPassword()).isEmpty();
        assertThat(url(reloaded).getText()).isEqualTo("https://nexus.example.com");
    }

    @ParameterizedTest(name = "\"{0}\" is {1}")
    @CsvSource(value = {"http://nexus.example.com, valid", "https://nexus.example.com:8443/repo, valid", "'', valid", "ftp://nexus.example.com, invalid",
            "nexus.example.com, invalid", "http://, invalid", "just text, invalid"})
    void theAddressMustBeAnHttpAddress(String address, String verdict) {
        NexusPreferencesProvider panel = panel();
        url(panel).setText(address);

        boolean valid = panel.arePreferencesValid();

        assertThat(valid).isEqualTo("valid".equals(verdict));
        assertThat(url(panel).getBackground().equals(ERROR)).isEqualTo(!valid); // a refused address is painted with the error color
    }

    @Test
    void aUserNameNeedsAPassword() {
        NexusPreferencesProvider panel = panel();
        url(panel).setText("https://nexus.example.com");
        user(panel).setText("alice");

        assertThat(panel.arePreferencesValid()).isFalse();
        assertThat(PanelSupport.passwordFields(panel).get(0).getBackground()).isEqualTo(ERROR);

        PanelSupport.passwordFields(panel).get(0).setText("pw");
        assertThat(panel.arePreferencesValid()).isTrue();
    }

    @Test
    void changesAreReportedAndClearEmptiesTheFields() {
        NexusPreferencesProvider panel = panel();
        List<PreferencesPanel> notified = new ArrayList<>();
        panel.addPreferencesChangeListener(notified::add);
        url(panel).setText("https://nexus.example.com");
        user(panel).setText("alice");
        PanelSupport.passwordFields(panel).get(0).setText("pw");
        notified.clear();

        PanelSupport.click(PanelSupport.button(panel, "Clear"));

        assertThat(PanelSupport.textFields(panel)).extracting(JTextField::getText).containsOnly("");
        assertThat(PanelSupport.passwordFields(panel).get(0).getPassword()).isEmpty();
        assertThat(notified).isNotEmpty().allSatisfy(source -> assertThat(source).isSameAs(panel));
    }

    @Test
    void restoreDefaultsAndAMissingErrorColorAreHandled() {
        NexusPreferencesProvider panel = new NexusPreferencesProvider();
        panel.init(null);
        url(panel).setText("https://nexus.example.com");

        panel.restoreDefaults();

        assertThat(url(panel).getText()).isEmpty();
        panel.loadPreferences(new HashMap<>());
        assertThat(url(panel).getText()).isEmpty();
    }
}
