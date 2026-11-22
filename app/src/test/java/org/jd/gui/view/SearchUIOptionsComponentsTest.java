/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.view;

import org.jd.gui.util.KeyBindings.Binding;
import org.junit.jupiter.api.Test;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The option buttons of the search, whose icons are resources of the application. */
class SearchUIOptionsComponentsTest {

    @Test
    void theOptionButtonsAreAddedToAContainerAndReportTheirChanges() {
        List<String> changes = new ArrayList<>();
        ActionListener listener = (ActionEvent e) -> changes.add("changed");
        SearchUIOptions options = new SearchUIOptions(listener);
        JPanel panel = new JPanel();

        options.attachTo(panel);
        for (java.awt.Component component : panel.getComponents()) {
            ((JToggleButton) component).doClick(0);
        }

        assertEquals(5, panel.getComponentCount());
        assertEquals(5, changes.size());
        assertTrue(options.isMatchCaseButtonSelected());
        // no listener: nothing is added
        new SearchUIOptions().addOptionsChangeListener(null);
        assertEquals(5, changes.size());
    }

    @Test
    void theTooltipsOfTheOptionsShowTheirShortcutsAndFollowThePreferences() {
        SearchUIOptions options = new SearchUIOptions();
        JPanel panel = new JPanel();
        options.attachTo(panel);
        Map<String, String> preferences = new HashMap<>();
        preferences.put(Binding.FIND_MATCH_CASE.getPreferenceKey(), "alt pressed X");
        preferences.put(Binding.FIND_REGEX.getPreferenceKey(), " ");

        options.refreshTooltips(preferences);

        String[] tooltips = new String[5];
        for (int i = 0; i < 5; i++) {
            tooltips[i] = ((JToggleButton) panel.getComponent(i)).getToolTipText();
        }
        assertTrue(tooltips[0].contains("("), tooltips[0]);       // match case: its shortcut
        assertTrue(tooltips[0].toLowerCase().contains("x"), tooltips[0]);
        assertTrue(!tooltips[2].contains("("), tooltips[2]);      // regular expression: no shortcut
        options.refreshTooltips(null);
        assertTrue(((JToggleButton) panel.getComponent(0)).getToolTipText().contains("("));
    }

    @Test
    void anOptionShortcutPressedInTheSearchFieldTogglesItsButton() {
        SearchUIOptions options = new SearchUIOptions();
        JPanel panel = new JPanel();
        options.attachTo(panel);
        JTextField field = new JTextField();
        Map<String, String> preferences = new HashMap<>();
        preferences.put(Binding.FIND_WHOLE_WORD.getPreferenceKey(), "alt pressed W");

        options.bindOptionKeyStrokes(field, preferences);
        options.bindOptionKeyStrokes(field, preferences); // again: the previous binding is replaced

        assertEquals("wholewordkey", field.getInputMap().get(KeyStroke.getKeyStroke("alt pressed W")));
        JToggleButton wholeWord = (JToggleButton) panel.getComponent(1);
        assertTrue(!wholeWord.isSelected());
        field.getActionMap().get("wholewordkey").actionPerformed(null);
        assertTrue(wholeWord.isSelected());
        options.bindOptionKeyStrokes(field);
    }
}
