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
package org.jd.gui.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

class AtomicCharArrayVaultTest {

    @Test
    void snapshot_returnsNullWhenVaultIsEmpty() {
        AtomicCharArrayVault vault = new AtomicCharArrayVault();
        assertNull(vault.snapshot());
    }

    @Test
    void length_returnsZeroWhenVaultIsEmpty() {
        AtomicCharArrayVault vault = new AtomicCharArrayVault();
        assertEquals(0, vault.length());
    }

    @Test
    void set_storesSecret_andSnapshotReturnsDefensiveCopy() {
        AtomicCharArrayVault vault = new AtomicCharArrayVault();
        char[] secret = {'p', 'a', 's', 's'};

        vault.set(secret);

        char[] snapshot = vault.snapshot();
        assertNotNull(snapshot);
        assertArrayEquals(secret, snapshot);
        // Must be a defensive copy, not the same reference
        assertNotSame(secret, snapshot);
    }

    @Test
    void set_doesNotShareInternalArray() {
        AtomicCharArrayVault vault = new AtomicCharArrayVault();
        char[] original = {'a', 'b', 'c'};
        vault.set(original);

        // Mutate the original; the vault must not be affected
        original[0] = 'X';
        char[] snapshot = vault.snapshot();
        assertEquals('a', snapshot[0]);
    }

    @Test
    void snapshot_returnsIndependentCopyEachTime() {
        AtomicCharArrayVault vault = new AtomicCharArrayVault();
        vault.set(new char[]{'x'});

        char[] first = vault.snapshot();
        char[] second = vault.snapshot();
        assertNotSame(first, second);
        assertArrayEquals(first, second);
    }

    @Test
    void length_returnsSecretLength() {
        AtomicCharArrayVault vault = new AtomicCharArrayVault();
        vault.set(new char[]{'a', 'b', 'c', 'd'});
        assertEquals(4, vault.length());
    }

    @Test
    void clear_wipesSecretAndReturnsNullSnapshot() {
        AtomicCharArrayVault vault = new AtomicCharArrayVault();
        vault.set(new char[]{'s', 'e', 'c', 'r', 'e', 't'});
        vault.clear();

        assertNull(vault.snapshot());
        assertEquals(0, vault.length());
    }

    @Test
    void set_withNullArgument_clearsVault() {
        AtomicCharArrayVault vault = new AtomicCharArrayVault();
        vault.set(new char[]{'p'});
        vault.set(null);

        assertNull(vault.snapshot());
    }

    @Test
    void set_withEmptyArray_clearsVault() {
        AtomicCharArrayVault vault = new AtomicCharArrayVault();
        vault.set(new char[]{'p'});
        vault.set(new char[0]);

        assertNull(vault.snapshot());
    }

    @Test
    void set_replacesExistingSecret() {
        AtomicCharArrayVault vault = new AtomicCharArrayVault();
        vault.set(new char[]{'o', 'l', 'd'});
        vault.set(new char[]{'n', 'e', 'w'});

        assertArrayEquals(new char[]{'n', 'e', 'w'}, vault.snapshot());
    }

    @Test
    void clear_onEmptyVault_isNoOp() {
        AtomicCharArrayVault vault = new AtomicCharArrayVault();
        // Should not throw
        vault.clear();
        assertNull(vault.snapshot());
    }
}
