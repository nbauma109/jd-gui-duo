/*******************************************************************************
 *
 * © 2025 Nicolas Baumann (@nbauma109)
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
package org.jd.gui.service.preferencespanel.secure;

import org.junit.jupiter.api.Test;

import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurePreferencesTest {

    private static final char[] MASTER_PASSWORD = "s3cr3tP@ssword!".toCharArray();

    @Test
    void encryptAndDecrypt_roundtrip_recoversPlaintext() throws GeneralSecurityException {
        String plaintext = "my-sensitive-value";

        String ciphertext = SecurePreferences.encrypt(MASTER_PASSWORD, plaintext);
        String decrypted = SecurePreferences.decrypt(MASTER_PASSWORD, ciphertext);

        assertEquals(plaintext, decrypted);
    }

    @Test
    void encrypt_producesDifferentCiphertextsForSamePlaintext() throws GeneralSecurityException {
        String plaintext = "repeated-value";

        String first = SecurePreferences.encrypt(MASTER_PASSWORD, plaintext);
        String second = SecurePreferences.encrypt(MASTER_PASSWORD, plaintext);

        // Each call uses a fresh random salt and IV, so outputs must differ
        assertNotEquals(first, second);
    }

    @Test
    void decrypt_withWrongPassword_throwsGeneralSecurityException() throws GeneralSecurityException {
        String plaintext = "secret";
        String ciphertext = SecurePreferences.encrypt(MASTER_PASSWORD, plaintext);
        char[] wrongPassword = "wr0ngP@ss!".toCharArray();

        assertThrows(GeneralSecurityException.class,
                () -> SecurePreferences.decrypt(wrongPassword, ciphertext));
    }

    @Test
    void decrypt_withTamperedCiphertext_throwsGeneralSecurityException() throws GeneralSecurityException {
        String plaintext = "tamper-test";
        String ciphertext = SecurePreferences.encrypt(MASTER_PASSWORD, plaintext);

        // Flip a byte near the end of the base64 string
        char[] chars = ciphertext.toCharArray();
        int idx = chars.length - 5;
        chars[idx] = (chars[idx] == 'A') ? 'B' : 'A';
        String tampered = new String(chars);

        assertThrows(GeneralSecurityException.class,
                () -> SecurePreferences.decrypt(MASTER_PASSWORD, tampered));
    }

    @Test
    void encryptAndDecrypt_handlesEmptyString() throws GeneralSecurityException {
        String ciphertext = SecurePreferences.encrypt(MASTER_PASSWORD, "");
        String decrypted = SecurePreferences.decrypt(MASTER_PASSWORD, ciphertext);

        assertEquals("", decrypted);
    }

    @Test
    void encryptAndDecrypt_handlesUnicodeText() throws GeneralSecurityException {
        String unicode = "caf\u00e9 \u4e2d\u6587 \ud83d\ude00";

        String ciphertext = SecurePreferences.encrypt(MASTER_PASSWORD, unicode);
        String decrypted = SecurePreferences.decrypt(MASTER_PASSWORD, ciphertext);

        assertEquals(unicode, decrypted);
    }

    @Test
    void encryptAndDecrypt_handlesLongPlaintext() throws GeneralSecurityException {
        String longText = "x".repeat(10_000);

        String ciphertext = SecurePreferences.encrypt(MASTER_PASSWORD, longText);
        String decrypted = SecurePreferences.decrypt(MASTER_PASSWORD, ciphertext);

        assertEquals(longText, decrypted);
    }

    @Test
    void hasNonEmpty_returnsTrueWhenValuePresent() {
        Map<String, String> prefs = new HashMap<>();
        prefs.put("key", "value");

        assertTrue(SecurePreferences.hasNonEmpty(prefs, "key"));
    }

    @Test
    void hasNonEmpty_returnsFalseWhenValueIsEmpty() {
        Map<String, String> prefs = new HashMap<>();
        prefs.put("key", "");

        assertFalse(SecurePreferences.hasNonEmpty(prefs, "key"));
    }

    @Test
    void hasNonEmpty_returnsFalseWhenKeyAbsent() {
        Map<String, String> prefs = new HashMap<>();

        assertFalse(SecurePreferences.hasNonEmpty(prefs, "missing"));
    }

    @Test
    void hasNonEmpty_returnsFalseWhenValueIsNull() {
        Map<String, String> prefs = new HashMap<>();
        prefs.put("key", null);

        assertFalse(SecurePreferences.hasNonEmpty(prefs, "key"));
    }
}
