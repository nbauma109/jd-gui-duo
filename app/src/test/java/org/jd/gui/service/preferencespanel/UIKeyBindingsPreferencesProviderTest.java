/*
 * Copyright (c) 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.preferencespanel;

import org.jd.gui.util.KeyBindings.Binding;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import javax.swing.KeyStroke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UIKeyBindingsPreferencesProviderTest {

    @Test
    void loadPreferences_readsConfiguredShortcut() {
        UIKeyBindingsPreferencesProvider provider = new UIKeyBindingsPreferencesProvider();
        Map<String, String> preferences = new HashMap<>();
        preferences.put(Binding.OPEN_FILE.getPreferenceKey(), "shift pressed F2");
        preferences.put(Binding.CLOSE.getPreferenceKey(), "");

        provider.loadPreferences(preferences);

        assertEquals(KeyStroke.getKeyStroke("shift pressed F2"), provider.getAssignedKeyStroke(Binding.OPEN_FILE));
        assertNull(provider.getAssignedKeyStroke(Binding.CLOSE));
    }

    @Test
    void savePreferences_writesAssignedShortcuts() {
        UIKeyBindingsPreferencesProvider provider = new UIKeyBindingsPreferencesProvider();
        provider.loadPreferences(new HashMap<>());
        provider.restoreDefaults();

        Map<String, String> preferences = new HashMap<>();
        provider.savePreferences(preferences);

        assertEquals(provider.getAssignedKeyStroke(Binding.OPEN_FILE).toString(), preferences.get(Binding.OPEN_FILE.getPreferenceKey()));
    }

    @Test
    void arePreferencesValid_detectsDuplicates() {
        UIKeyBindingsPreferencesProvider provider = new UIKeyBindingsPreferencesProvider();
        Map<String, String> preferences = new HashMap<>();
        preferences.put(Binding.OPEN_FILE.getPreferenceKey(), "shift pressed F2");
        preferences.put(Binding.CLOSE.getPreferenceKey(), "shift pressed F2");

        provider.loadPreferences(preferences);

        assertFalse(provider.arePreferencesValid());
    }

    @Test
    void restoreDefaults_returnsToValidState() {
        UIKeyBindingsPreferencesProvider provider = new UIKeyBindingsPreferencesProvider();
        Map<String, String> preferences = new HashMap<>();
        preferences.put(Binding.OPEN_FILE.getPreferenceKey(), "shift pressed F2");
        preferences.put(Binding.CLOSE.getPreferenceKey(), "shift pressed F2");

        provider.loadPreferences(preferences);
        provider.restoreDefaults();

        assertTrue(provider.arePreferencesValid());
    }
}
