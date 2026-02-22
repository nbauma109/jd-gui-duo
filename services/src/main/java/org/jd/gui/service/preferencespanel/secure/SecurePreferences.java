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
package org.jd.gui.service.preferencespanel.secure;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

/**
 * SecurePreferences
 *
 * This helper encapsulates key derivation and authenticated encryption so that
 * we can persist secrets in the preferences map without storing cleartext.
 *
 * Design choices:
 * - Key derivation: PBKDF2WithHmacSHA256 with a per-vault salt and a high iteration count.
 * - Encryption: AES-GCM with a fresh random initialization vector per ciphertext.
 * - Format: base64 of a compact binary envelope: [saltLen(2)][salt][ivLen(2)][iv][ctLen(4)][ciphertext]
 *   We include the salt in each blob so that values remain portable across master password changes if desired.
 *
 * Thread safety: stateless methods, safe for concurrent use.
 */
public final class SecurePreferences {

    // We keep these parameters conservative and documented so we can tune them in the future.
    private static final int SALT_BYTES = 16;
    private static final int IV_BYTES = 12; // Standard for AES-GCM
    private static final int TAG_BITS = 128;
    private static final int KEY_BITS = 256;
    private static final int PBKDF2_ITERATIONS = 200_000;

    private static final String KDF_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";

    private static final SecureRandom RNG = new SecureRandom();

    private SecurePreferences() {}

    /**
     * Derives a key from the master password and the provided salt.
     */
    private static SecretKeySpec deriveKey(char[] masterPassword, byte[] salt) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(masterPassword, salt, PBKDF2_ITERATIONS, KEY_BITS);
        SecretKeyFactory skf = SecretKeyFactory.getInstance(KDF_ALGORITHM);
        byte[] keyBytes = skf.generateSecret(spec).getEncoded();
        SecretKeySpec key = new SecretKeySpec(keyBytes, "AES");
        // We erase the raw key material as a best effort.
        zeroBytes(keyBytes);
        spec.clearPassword();
        return key;
    }

    /**
     * Encrypts a UTF-8 string with AES-GCM under a key derived from the given master password.
     * Returns a base64 envelope that contains salt, initialization vector, and ciphertext.
     */
    public static String encrypt(char[] masterPassword, String plaintext) throws GeneralSecurityException {
        byte[] salt = randomBytes(SALT_BYTES);
        SecretKeySpec key = deriveKey(masterPassword, salt);
        byte[] iv = randomBytes(IV_BYTES);

        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
        byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        byte[] envelope = packEnvelope(salt, iv, ct);
        // We erase sensitive arrays where reasonable.
        zeroBytes(iv);
        zeroBytes(ct);
        zeroBytes(key.getEncoded()); // getEncoded returns a copy for SecretKeySpec, safe to zero

        return Base64.getEncoder().encodeToString(envelope);
    }

    /**
     * Decrypts an envelope produced by encrypt.
     */
    public static String decrypt(char[] masterPassword, String base64Envelope) throws GeneralSecurityException {
        byte[] envelope = Base64.getDecoder().decode(base64Envelope);
        ByteBuffer bb = ByteBuffer.wrap(envelope).order(ByteOrder.BIG_ENDIAN);

        int saltLen = Short.toUnsignedInt(bb.getShort());
        byte[] salt = new byte[saltLen];
        bb.get(salt);

        int ivLen = Short.toUnsignedInt(bb.getShort());
        byte[] iv = new byte[ivLen];
        bb.get(iv);

        int ctLen = bb.getInt();
        byte[] ct = new byte[ctLen];
        bb.get(ct);

        SecretKeySpec key = deriveKey(masterPassword, salt);
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
        byte[] pt = cipher.doFinal(ct);

        String plaintext = new String(pt, StandardCharsets.UTF_8);

        // Best effort zeroing.
        zeroBytes(salt);
        zeroBytes(iv);
        zeroBytes(ct);
        zeroBytes(pt);
        zeroBytes(key.getEncoded());

        return plaintext;
    }

    /**
     * Helper that returns true if the given map contains a non-empty value for the key.
     */
    public static boolean hasNonEmpty(Map<String, String> prefs, String key) {
        String v = prefs.get(key);
        return v != null && !v.isEmpty();
    }

    private static byte[] packEnvelope(byte[] salt, byte[] iv, byte[] ct) {
        ByteBuffer bb = ByteBuffer.allocate(2 + salt.length + 2 + iv.length + 4 + ct.length)
                .order(ByteOrder.BIG_ENDIAN);
        bb.putShort((short) salt.length).put(salt);
        bb.putShort((short) iv.length).put(iv);
        bb.putInt(ct.length).put(ct);
        return bb.array();
    }

    private static byte[] randomBytes(int n) {
        byte[] out = new byte[n];
        RNG.nextBytes(out);
        return out;
    }

    private static void zeroBytes(byte[] a) {
        if (a == null) {
            return;
        }
        for (int i = 0; i < a.length; i++) {
            a[i] = 0;
        }
    }
}
