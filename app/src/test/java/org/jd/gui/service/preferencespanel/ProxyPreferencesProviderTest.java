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

/** The proxy settings panel: what it loads, saves and accepts. */
class ProxyPreferencesProviderTest {

    private static final Color ERROR = new Color(250, 0, 0);

    @AfterEach
    void lock() {
        PanelSupport.lock();
    }

    private static ProxyPreferencesProvider panel() {
        ProxyPreferencesProvider panel = new ProxyPreferencesProvider();
        panel.init(ERROR);
        return panel;
    }

    private static JTextField host(ProxyPreferencesProvider panel) { return PanelSupport.textFields(panel).get(0); }
    private static JTextField port(ProxyPreferencesProvider panel) { return PanelSupport.textFields(panel).get(1); }
    private static JTextField user(ProxyPreferencesProvider panel) { return PanelSupport.textFields(panel).get(2); }

    @Test
    void thePanelIsTitledAndHasItsFields() {
        ProxyPreferencesProvider panel = panel();

        assertThat(panel.getPreferencesGroupTitle()).isEqualTo("Proxy");
        assertThat(panel.getPreferencesPanelTitle()).isEqualTo("Proxy for internet artifact search with maven.org");
        assertThat(PanelSupport.textFields(panel)).hasSize(3);
        assertThat(PanelSupport.passwordFields(panel)).hasSize(1);
        assertThat(panel.useCompactDisplay()).isFalse();
    }

    @Test
    void savedHostAndPortAreLoadedBackTrimmed() {
        ProxyPreferencesProvider panel = panel();
        Map<String, String> preferences = new HashMap<>();
        preferences.put(ProxyPreferencesProvider.PROXY_HOST, "proxy.example.com");
        preferences.put(ProxyPreferencesProvider.PROXY_PORT, " 3128 ");

        panel.loadPreferences(preferences);

        assertThat(host(panel).getText()).isEqualTo("proxy.example.com");
        assertThat(port(panel).getText()).isEqualTo("3128");
        assertThat(panel.arePreferencesValid()).isTrue();
    }

    @Test
    void withoutCredentialsTheHostAndThePortAreSavedAndTheStoredSecretsAreCleared() {
        ProxyPreferencesProvider panel = panel();
        host(panel).setText("  proxy.example.com ");
        port(panel).setText("8080");
        Map<String, String> preferences = new HashMap<>();
        preferences.put(ProxyPreferencesProvider.PROXY_USER_ENC, "old");
        preferences.put(ProxyPreferencesProvider.PROXY_PASS_ENC, "old");

        panel.savePreferences(preferences);

        assertThat(preferences).containsEntry(ProxyPreferencesProvider.PROXY_HOST, "proxy.example.com").containsEntry(ProxyPreferencesProvider.PROXY_PORT, "8080")
                .containsEntry(ProxyPreferencesProvider.PROXY_USER_ENC, "").containsEntry(ProxyPreferencesProvider.PROXY_PASS_ENC, "");
    }

    @Test
    void credentialsAreSavedEncryptedAndLoadedBackWithTheMasterPassword() throws Exception {
        PanelSupport.unlock("master-password");
        ProxyPreferencesProvider panel = panel();
        host(panel).setText("proxy.example.com");
        user(panel).setText("alice");
        PanelSupport.passwordFields(panel).get(0).setText("s3cret");
        Map<String, String> preferences = new HashMap<>();

        panel.savePreferences(preferences);

        assertThat(preferences).containsEntry(ProxyPreferencesProvider.VAULT_PRESENT, "true");
        assertThat(preferences.get(ProxyPreferencesProvider.PROXY_USER_ENC)).isNotBlank().doesNotContain("alice");
        assertThat(preferences.get(ProxyPreferencesProvider.PROXY_PASS_ENC)).isNotBlank().doesNotContain("s3cret");

        ProxyPreferencesProvider reloaded = panel();
        reloaded.loadPreferences(preferences);
        assertThat(user(reloaded).getText()).isEqualTo("alice");
        assertThat(new String(PanelSupport.passwordFields(reloaded).get(0).getPassword())).isEqualTo("s3cret");
        assertThat(host(reloaded).getText()).isEqualTo("proxy.example.com");
    }

    @Test
    void aWrongMasterPasswordLeavesTheCredentialsBlank() throws Exception {
        PanelSupport.unlock("master-password");
        ProxyPreferencesProvider panel = panel();
        host(panel).setText("proxy.example.com");
        user(panel).setText("alice");
        PanelSupport.passwordFields(panel).get(0).setText("s3cret");
        Map<String, String> preferences = new HashMap<>();
        panel.savePreferences(preferences);

        PanelSupport.lock();
        PanelSupport.unlock("another-password");
        ProxyPreferencesProvider reloaded = panel();
        reloaded.loadPreferences(preferences);

        assertThat(user(reloaded).getText()).isEmpty();
        assertThat(PanelSupport.passwordFields(reloaded).get(0).getPassword()).isEmpty();
        assertThat(host(reloaded).getText()).isEqualTo("proxy.example.com"); // the host is not a secret
    }

    @ParameterizedTest(name = "host \"{0}\" with port \"{1}\" is {2}")
    @CsvSource(value = {"proxy.example.com, 3128, valid", "localhost, '', valid", "my-proxy_1.internal, 1, valid", "proxy, 65535, valid", "'', '', valid",
            "bad host, 3128, invalid", "proxy.example.com, 0, invalid", "proxy.example.com, 65536, invalid", "proxy.example.com, abc, invalid", "proxy.example.com, -5, invalid",
            "'', 3128, invalid", "proxy/path, 80, invalid"})
    void hostAndPortMustBeUsable(String host, String port, String verdict) {
        ProxyPreferencesProvider panel = panel();
        host(panel).setText(host);
        port(panel).setText(port);

        boolean valid = panel.arePreferencesValid();

        assertThat(valid).isEqualTo("valid".equals(verdict));
        // a field that is refused is painted with the error color
        boolean hostRefused = !host.isEmpty() && !host.matches("[a-zA-Z0-9._-]+") || host.isEmpty() && !port.isEmpty();
        assertThat(host(panel).getBackground().equals(ERROR)).isEqualTo(hostRefused);
    }

    @Test
    void aTooLongHostIsRefused() {
        ProxyPreferencesProvider panel = panel();
        host(panel).setText("a".repeat(254));

        assertThat(panel.arePreferencesValid()).isFalse();
        host(panel).setText("a".repeat(253));
        assertThat(panel.arePreferencesValid()).isTrue();
    }

    @Test
    void aUserNameNeedsAPassword() {
        ProxyPreferencesProvider panel = panel();
        host(panel).setText("proxy.example.com");
        user(panel).setText("alice");

        assertThat(panel.arePreferencesValid()).isFalse();
        assertThat(PanelSupport.passwordFields(panel).get(0).getBackground()).isEqualTo(ERROR);

        PanelSupport.passwordFields(panel).get(0).setText("pw");
        assertThat(panel.arePreferencesValid()).isTrue();
        assertThat(PanelSupport.passwordFields(panel).get(0).getBackground()).isNotEqualTo(ERROR);
    }

    @Test
    void changesAreReportedToTheListener() {
        ProxyPreferencesProvider panel = panel();
        List<PreferencesPanel> notified = new ArrayList<>();
        panel.addPreferencesChangeListener(notified::add);

        host(panel).setText("proxy.example.com"); // a text change
        PanelSupport.click(PanelSupport.button(panel, "Clear")); // a click

        assertThat(notified).isNotEmpty().allSatisfy(source -> assertThat(source).isSameAs(panel));
        assertThat(notified).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void clearEmptiesEveryField() {
        ProxyPreferencesProvider panel = panel();
        host(panel).setText("proxy.example.com");
        port(panel).setText("3128");
        user(panel).setText("alice");
        PanelSupport.passwordFields(panel).get(0).setText("pw");

        PanelSupport.click(PanelSupport.button(panel, "Clear"));

        assertThat(PanelSupport.textFields(panel)).extracting(JTextField::getText).containsOnly("");
        assertThat(PanelSupport.passwordFields(panel).get(0).getPassword()).isEmpty();
        assertThat(panel.arePreferencesValid()).isTrue();
    }

    @Test
    void restoreDefaultsAndAMissingErrorColorAreHandled() {
        ProxyPreferencesProvider panel = new ProxyPreferencesProvider();
        panel.init(null); // keeps the default error color
        host(panel).setText("proxy.example.com");

        panel.restoreDefaults();

        assertThat(host(panel).getText()).isEmpty();
        panel.loadPreferences(new HashMap<>());
        assertThat(port(panel).getText()).isEmpty();
    }
}
