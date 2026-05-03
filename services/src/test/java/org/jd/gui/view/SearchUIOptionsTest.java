/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.view;

import org.jd.gui.util.KeyBindings.Binding;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import javax.swing.JTextField;
import javax.swing.KeyStroke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchUIOptionsTest {

    @Test
    void bindSearchNavigationKeyStrokes_usesConfiguredBindings() {
        JTextField field = new JTextField();
        Map<String, String> preferences = new HashMap<>();
        preferences.put(Binding.FIND_NEXT.getPreferenceKey(), "pressed F3");
        preferences.put(Binding.FIND_PREVIOUS.getPreferenceKey(), "shift pressed F3");

        SearchUIOptions.bindSearchNavigationKeyStrokes(field, preferences, () -> {}, () -> {});

        assertEquals("searchForward", field.getInputMap().get(KeyStroke.getKeyStroke("pressed F3")));
        assertEquals("searchBackward", field.getInputMap().get(KeyStroke.getKeyStroke("shift pressed F3")));
        assertNotEquals("searchForward", field.getInputMap().get(KeyStroke.getKeyStroke("ENTER")));
    }

    @Test
    void getIncrementalSearchTooltip_mentionsConfiguredBindings() {
        Map<String, String> preferences = new HashMap<>();
        preferences.put(Binding.FIND_NEXT.getPreferenceKey(), "pressed F3");
        preferences.put(Binding.FIND_PREVIOUS.getPreferenceKey(), "shift pressed F3");

        String tooltip = SearchUIOptions.getIncrementalSearchTooltip(preferences);

        assertTrue(tooltip.contains("F3"));
        assertTrue(tooltip.contains("Previous"));
    }

    @Test
    void stripShortcutSuffix_removesEmbeddedShortcutHint() {
        assertEquals("Match Case", SearchUIOptions.stripShortcutSuffix("Match Case (Alt + C)"));
    }
}
