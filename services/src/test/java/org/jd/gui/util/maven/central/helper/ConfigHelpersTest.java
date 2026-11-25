/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.util.maven.central.helper;

import org.jd.gui.security.SecureSession;
import org.jd.gui.service.preferencespanel.NexusPreferencesProvider;
import org.jd.gui.service.preferencespanel.ProxyPreferencesProvider;
import org.jd.gui.service.preferencespanel.secure.SecurePreferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The configurations of the proxy and of the Nexus server, as they are read from the preferences. */
class ConfigHelpersTest {

    private static final String MASTER = "master-password";

    /** The master password is given to the session, so that it is not asked for. */
    @BeforeEach
    void unlock() throws ReflectiveOperationException {
        Field vaultField = SecureSession.class.getDeclaredField("vault");
        vaultField.setAccessible(true);
        Object vault = vaultField.get(SecureSession.get());
        Method set = vault.getClass().getDeclaredMethod("set", char[].class);
        set.setAccessible(true);
        set.invoke(vault, (Object) MASTER.toCharArray());
    }

    @AfterEach
    void lock() {
        SecureSession.get().clear();
    }

    private static String encrypted(String value) throws GeneralSecurityException {
        return SecurePreferences.encrypt(MASTER.toCharArray(), value);
    }

    private static Map<String, String> preferences(String... keysAndValues) {
        Map<String, String> preferences = new HashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            preferences.put(keysAndValues[i], keysAndValues[i + 1]);
        }
        return preferences;
    }

    @Test
    void theProxyHasItsHostItsPortAndItsDecryptedCredentials() throws GeneralSecurityException {
        ProxyConfig config = ProxyConfigHelper.fromPreferences(preferences(
                ProxyPreferencesProvider.PROXY_HOST, " proxy.example.com ",
                ProxyPreferencesProvider.PROXY_PORT, " 3128 ",
                ProxyPreferencesProvider.PROXY_USER_ENC, encrypted("alice"),
                ProxyPreferencesProvider.PROXY_PASS_ENC, encrypted("secret")), null);

        assertThat(config).usingRecursiveComparison().isEqualTo(new ProxyConfig("proxy.example.com", 3128, "alice", "secret".toCharArray()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "0", "65536", "port"})
    void aProxyPortThatIsNoPortIsLeftOut(String port) {
        ProxyConfig config = ProxyConfigHelper.fromPreferences(preferences(
                ProxyPreferencesProvider.PROXY_HOST, "proxy.example.com", ProxyPreferencesProvider.PROXY_PORT, port), null);

        assertThat(config).usingRecursiveComparison().isEqualTo(new ProxyConfig("proxy.example.com", null, null, null));
    }

    @Test
    void emptyCredentialsAndOnesThatCannotBeDecryptedAreLeftOut() throws GeneralSecurityException {
        ProxyConfig expected = new ProxyConfig("proxy.example.com", null, null, null);

        assertThat(ProxyConfigHelper.fromPreferences(preferences(ProxyPreferencesProvider.PROXY_HOST, "proxy.example.com",
                ProxyPreferencesProvider.PROXY_USER_ENC, encrypted(""), ProxyPreferencesProvider.PROXY_PASS_ENC, encrypted("")), null))
                .usingRecursiveComparison().isEqualTo(expected);
        assertThat(ProxyConfigHelper.fromPreferences(preferences(ProxyPreferencesProvider.PROXY_HOST, "proxy.example.com",
                ProxyPreferencesProvider.PROXY_USER_ENC, "not encrypted", ProxyPreferencesProvider.PROXY_PASS_ENC, "neither"), null))
                .usingRecursiveComparison().isEqualTo(expected);
    }

    /** Values that are no envelope: no base64, too short, and lengths that go past the end or are negative. */
    @ParameterizedTest
    @ValueSource(strings = {"not encrypted", "AA==", "ABA=", "AAAAAH////8=", "AAAAAP////8="})
    void aValueThatIsNoEncryptedValueIsRefusedLikeOneThatCannotBeDecrypted(String value) {
        char[] master = MASTER.toCharArray();

        assertThatThrownBy(() -> SecurePreferences.decrypt(master, value)).isInstanceOf(GeneralSecurityException.class);
        assertThat(NexusConfigHelper.fromPreferences(preferences(NexusPreferencesProvider.NEXUS_URL, "https://nexus.example.com",
                NexusPreferencesProvider.NEXUS_PASS_ENC, value), null))
                .usingRecursiveComparison().isEqualTo(new NexusConfig("https://nexus.example.com", null, null));
    }

    @Test
    void withoutAHostThereIsNoProxy() throws GeneralSecurityException {
        assertThat(ProxyConfigHelper.fromPreferences(preferences(), null)).isNull();
        assertThat(ProxyConfigHelper.fromPreferences(preferences(ProxyPreferencesProvider.PROXY_HOST, " ",
                ProxyPreferencesProvider.PROXY_USER_ENC, encrypted("alice")), null)).isNull();
    }

    @Test
    void theNexusServerHasItsAddressAndItsDecryptedCredentials() throws GeneralSecurityException {
        NexusConfig config = NexusConfigHelper.fromPreferences(preferences(
                NexusPreferencesProvider.NEXUS_URL, " https://nexus.example.com ",
                NexusPreferencesProvider.NEXUS_USER_ENC, encrypted("bob"),
                NexusPreferencesProvider.NEXUS_PASS_ENC, encrypted("secret")), null);

        assertThat(config).usingRecursiveComparison().isEqualTo(new NexusConfig("https://nexus.example.com", "bob", "secret".toCharArray()));
    }

    @Test
    void theNexusServerCanBeUsedWithoutCredentials() throws GeneralSecurityException {
        NexusConfig expected = new NexusConfig("https://nexus.example.com", null, null);

        assertThat(NexusConfigHelper.fromPreferences(preferences(NexusPreferencesProvider.NEXUS_URL, "https://nexus.example.com"), null))
                .usingRecursiveComparison().isEqualTo(expected);
        assertThat(NexusConfigHelper.fromPreferences(preferences(NexusPreferencesProvider.NEXUS_URL, "https://nexus.example.com",
                NexusPreferencesProvider.NEXUS_USER_ENC, encrypted(""), NexusPreferencesProvider.NEXUS_PASS_ENC, encrypted("")), null))
                .usingRecursiveComparison().isEqualTo(expected);
        assertThat(NexusConfigHelper.fromPreferences(preferences(NexusPreferencesProvider.NEXUS_URL, "https://nexus.example.com",
                NexusPreferencesProvider.NEXUS_USER_ENC, "not encrypted"), null))
                .usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void withoutAnAddressThereIsNoNexusServer() throws GeneralSecurityException {
        assertThat(NexusConfigHelper.fromPreferences(preferences(NexusPreferencesProvider.NEXUS_USER_ENC, encrypted("bob")), null)).isNull();
    }
}
