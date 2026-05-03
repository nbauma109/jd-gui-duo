/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.util;

import org.jd.gui.util.KeyBindings.Binding;
import org.junit.jupiter.api.Test;

import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;

import javax.swing.KeyStroke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class KeyBindingsTest {

    @Test
    void getConfiguredKeyStroke_usesDefaultWhenPreferenceMissing() {
        KeyStroke keyStroke = KeyBindings.getConfiguredKeyStroke(new HashMap<>(), Binding.OPEN_FILE);

        assertEquals(KeyStroke.getKeyStroke(KeyEvent.VK_O, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()), keyStroke);
    }

    @Test
    void getConfiguredKeyStroke_usesCustomShortcutWhenPresent() {
        Map<String, String> preferences = new HashMap<>();
        preferences.put(Binding.OPEN_FILE.getPreferenceKey(), "shift pressed F2");

        assertEquals(KeyStroke.getKeyStroke(KeyEvent.VK_F2, KeyEvent.SHIFT_DOWN_MASK), KeyBindings.getConfiguredKeyStroke(preferences, Binding.OPEN_FILE));
    }

    @Test
    void getConfiguredKeyStroke_returnsNullForBlankPreference() {
        Map<String, String> preferences = new HashMap<>();
        preferences.put(Binding.OPEN_FILE.getPreferenceKey(), "");

        assertNull(KeyBindings.getConfiguredKeyStroke(preferences, Binding.OPEN_FILE));
    }

    @Test
    void toDisplayText_formatsHumanReadableShortcut() {
        assertEquals("Ctrl+Shift+F2", KeyBindings.toDisplayText(KeyStroke.getKeyStroke(KeyEvent.VK_F2, KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK)));
    }

    @Test
    void toDisplayText_usesCommandSymbolForMetaKey() {
        assertEquals("Ctrl+\u2318+C", KeyBindings.toDisplayText(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK | InputEvent.META_DOWN_MASK)));
    }
}
