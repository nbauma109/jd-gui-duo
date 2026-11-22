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

import javax.swing.JButton;
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

    @Test
    void theSearchFieldAndTheButtonsHaveTheTooltipsOfTheSearch() {
        JTextField field = new JTextField();
        JButton find = new JButton();
        JButton selectAll = new JButton();
        Map<String, String> preferences = new HashMap<>();
        preferences.put(Binding.FIND_PREVIOUS.getPreferenceKey(), " ");

        SearchUIOptions.configureIncrementalSearchField(field);
        SearchUIOptions.configureFindButton(find, preferences);
        SearchUIOptions.configureFindButton(new JButton());
        SearchUIOptions.configureSelectAllButton(selectAll);
        SearchUIOptions.configureIncrementalSearchField(field, preferences);

        assertTrue(field.getToolTipText().startsWith("Search Text"));
        // the previous shortcut is cleared: only the next one is given
        assertTrue(find.getToolTipText().contains("Next"), find.getToolTipText());
        assertTrue(!find.getToolTipText().contains("Previous"), find.getToolTipText());
        assertTrue(!selectAll.getToolTipText().isEmpty());
        assertEquals("Next", SearchUIOptions.getFindNextLabel());
        assertEquals("Previous", SearchUIOptions.getFindPreviousLabel());
        assertTrue(SearchUIOptions.getIncrementalSearchTooltip().startsWith("Search Text"));
    }

    @Test
    void theSearchTooltipHasOnlyTheShortcutsThatAreSet() {
        Map<String, String> onlyPrevious = new HashMap<>();
        onlyPrevious.put(Binding.FIND_NEXT.getPreferenceKey(), "");
        Map<String, String> none = new HashMap<>();
        none.put(Binding.FIND_NEXT.getPreferenceKey(), "");
        none.put(Binding.FIND_PREVIOUS.getPreferenceKey(), "");

        String previous = SearchUIOptions.getIncrementalSearchTooltip(onlyPrevious);
        String neither = SearchUIOptions.getIncrementalSearchTooltip(none);

        assertTrue(previous.contains("Previous") && !previous.contains("Next"), previous);
        assertEquals("Search Text", neither);
    }

    @Test
    void theCriteriaListenerIsCalledForEveryChangeOfTheText() {
        JTextField field = new JTextField();
        int[] calls = {0};
        SearchUIOptions.installCriteriaListener(field, () -> calls[0]++);

        field.setText("abc");
        field.setText("");

        assertTrue(calls[0] >= 2, "calls: " + calls[0]);
    }

    @Test
    void aMissingTooltipHasNoShortcutToStrip() {
        assertEquals("", SearchUIOptions.stripShortcutSuffix(null));
        assertEquals("No suffix", SearchUIOptions.stripShortcutSuffix("No suffix"));
        assertEquals("Open (not a shortcut", SearchUIOptions.stripShortcutSuffix("Open (not a shortcut"));
    }
}
