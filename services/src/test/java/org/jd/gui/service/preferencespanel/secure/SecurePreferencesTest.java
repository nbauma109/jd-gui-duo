/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.preferencespanel.secure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The encryption of the credentials that are kept in the preferences. */
class SecurePreferencesTest {

    private static final char[] MASTER = "master-password".toCharArray();

    @ParameterizedTest(name = "\"{0}\" survives a round trip")
    @ValueSource(strings = {"", "alice", "p@ss w0rd with spaces", "ünïcödé ✓ 漢字", "a\nb\tc"})
    void whatIsEncryptedIsDecryptedWithTheSameMasterPassword(String plaintext) throws GeneralSecurityException {
        String envelope = SecurePreferences.encrypt(MASTER, plaintext);

        assertThat(SecurePreferences.decrypt(MASTER, envelope)).isEqualTo(plaintext);
    }

    @Test
    void aLongTextSurvivesARoundTrip() throws GeneralSecurityException {
        String longText = "0123456789".repeat(500);

        assertThat(SecurePreferences.decrypt(MASTER, SecurePreferences.encrypt(MASTER, longText))).isEqualTo(longText);
    }

    @Test
    void theEnvelopeIsDifferentEachTimeAndHidesTheText() throws GeneralSecurityException {
        String first = SecurePreferences.encrypt(MASTER, "secret");
        String second = SecurePreferences.encrypt(MASTER, "secret");

        assertThat(first).isNotEqualTo(second).doesNotContain("secret"); // a random salt and a random initialization vector
        assertThat(new String(Base64.getDecoder().decode(first), java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("secret");
    }

    @Test
    void aWrongMasterPasswordCannotDecrypt() throws GeneralSecurityException {
        String envelope = SecurePreferences.encrypt(MASTER, "secret");

        assertThatThrownBy(() -> SecurePreferences.decrypt("other-password".toCharArray(), envelope)).isInstanceOf(GeneralSecurityException.class);
    }

    @Test
    void aTamperedEnvelopeIsRejected() throws GeneralSecurityException {
        byte[] bytes = Base64.getDecoder().decode(SecurePreferences.encrypt(MASTER, "secret"));
        bytes[bytes.length - 1] ^= 0x01; // the authentication tag no longer matches
        String tampered = Base64.getEncoder().encodeToString(bytes);

        assertThatThrownBy(() -> SecurePreferences.decrypt(MASTER, tampered)).isInstanceOf(GeneralSecurityException.class);
    }

    @Test
    void anEnvelopeThatIsNotBase64IsRejected() {
        assertThatThrownBy(() -> SecurePreferences.decrypt(MASTER, "not base64 !!")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hasNonEmptyTellsWhetherAValueIsKept() {
        Map<String, String> preferences = new HashMap<>();
        preferences.put("filled", "value");
        preferences.put("empty", "");

        assertThat(SecurePreferences.hasNonEmpty(preferences, "filled")).isTrue();
        assertThat(SecurePreferences.hasNonEmpty(preferences, "empty")).isFalse();
        assertThat(SecurePreferences.hasNonEmpty(preferences, "missing")).isFalse();
    }
}
