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

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AtomicCharArrayVault
 *
 * We maintain the master password as a single char[] owned by this vault.
 * We use an AtomicReference to swap the entire array atomically.
 * We never expose the internal array; we return defensive copies.
 * We explicitly wipe old arrays immediately after swap.
 */
public final class AtomicCharArrayVault {

    private final AtomicReference<char[]> holder = new AtomicReference<>(null);

    /**
     * Sets or replaces the current secret.
     * We copy the input so that our vault owns the memory and can wipe it later.
     * The caller should clear its own array after calling this method.
     */
    public void set(char[] secret) {
        if (secret == null || secret.length == 0) {
            clear();
            return;
        }
        char[] owned = Arrays.copyOf(secret, secret.length);
        char[] old = holder.getAndSet(owned);
        wipe(old);
    }

    /**
     * Returns a defensive snapshot of the current secret, or null if none is set.
     * The caller is responsible for clearing the returned array after use.
     */
    public char[] snapshot() {
        char[] cur = holder.get();
        return cur == null ? null : Arrays.copyOf(cur, cur.length);
    }

    /**
     * Clears the vault by swapping in null and wiping the previous array.
     */
    public void clear() {
        char[] old = holder.getAndSet(null);
        wipe(old);
    }

    /**
     * Returns the current length, or zero if unset.
     */
    public int length() {
        char[] cur = holder.get();
        return cur == null ? 0 : cur.length;
    }

    private static void wipe(char[] a) {
        if (a != null) {
            Arrays.fill(a, '\0');
        }
    }
}
