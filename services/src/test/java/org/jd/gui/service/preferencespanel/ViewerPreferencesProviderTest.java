/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.preferencespanel;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JComboBox;

import static org.jd.gui.util.decompiler.GuiPreferences.DEFAULT_SEARCH_HIGHLIGHT_COLOR;
import static org.jd.gui.util.decompiler.GuiPreferences.DEFAULT_SELECTION_HIGHLIGHT_COLOR;
import static org.jd.gui.util.decompiler.GuiPreferences.SEARCH_HIGHLIGHT_COLOR;
import static org.jd.gui.util.decompiler.GuiPreferences.SELECTION_HIGHLIGHT_COLOR;
import static org.jd.gui.util.decompiler.GuiPreferences.DEFAULT_SELECTED_WORD_HIGHLIGHT_COLOR;
import static org.jd.gui.util.decompiler.GuiPreferences.SELECTED_WORD_HIGHLIGHT_COLOR;
import static org.jd.gui.util.decompiler.GuiPreferences.SELECTED_WORD_HIGHLIGHT_ENABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewerPreferencesProviderTest {

    private static final String FONT_FAMILY_KEY = "ViewerPreferences.fontFamily";

    @Test
    void fontFamilyComboBox_containsDefaultAndInstalledFamilies() {
        ViewerPreferencesProvider provider = new ViewerPreferencesProvider();
        JComboBox<?> comboBox = fontFamilyComboBox(provider);

        assertEquals("Default", comboBox.getItemAt(0));
        for (String family : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
            assertTrue(containsItem(comboBox, family), family);
        }
    }

    @Test
    void fontFamilyPreference_loadsAndSavesInstalledFamily() {
        ViewerPreferencesProvider provider = new ViewerPreferencesProvider();
        String family = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()[0];
        Map<String, String> preferences = new HashMap<>();
        preferences.put(FONT_FAMILY_KEY, family);

        provider.loadPreferences(preferences);

        assertEquals(family, fontFamilyComboBox(provider).getSelectedItem());
        Map<String, String> saved = new HashMap<>();
        provider.savePreferences(saved);
        assertEquals(family, saved.get(FONT_FAMILY_KEY));
    }

    @Test
    void fontFamilyPreference_selectionNotifiesAndSaves() {
        ViewerPreferencesProvider provider = new ViewerPreferencesProvider();
        Map<String, String> preferences = new HashMap<>();
        provider.loadPreferences(preferences);
        AtomicInteger notifications = new AtomicInteger();
        provider.addPreferencesChangeListener(source -> notifications.incrementAndGet());
        String family = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()[0];

        fontFamilyComboBox(provider).setSelectedItem(family);
        provider.savePreferences(preferences);

        assertTrue(notifications.get() > 0);
        assertEquals(family, preferences.get(FONT_FAMILY_KEY));
    }

    @Test
    void fontFamilyPreference_retainsUnavailableFamily() {
        ViewerPreferencesProvider provider = new ViewerPreferencesProvider();
        String family = "Unavailable test font family";
        while (Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()).contains(family)) {
            family += " unavailable";
        }
        Map<String, String> preferences = new HashMap<>();
        preferences.put(FONT_FAMILY_KEY, family);

        provider.loadPreferences(preferences);

        JComboBox<?> comboBox = fontFamilyComboBox(provider);
        assertTrue(containsItem(comboBox, family));
        assertEquals(family, comboBox.getSelectedItem());
        provider.savePreferences(preferences);
        assertEquals(family, preferences.get(FONT_FAMILY_KEY));
    }

    @Test
    void fontFamilyPreference_defaultAndRestoreRemoveSavedKey() {
        ViewerPreferencesProvider provider = new ViewerPreferencesProvider();
        Map<String, String> preferences = new HashMap<>();
        provider.loadPreferences(preferences);

        assertEquals("Default", fontFamilyComboBox(provider).getSelectedItem());
        preferences.put(FONT_FAMILY_KEY, "previous family");
        provider.savePreferences(preferences);
        assertFalse(preferences.containsKey(FONT_FAMILY_KEY));

        preferences.put(FONT_FAMILY_KEY, "previous family");
        provider.loadPreferences(preferences);
        provider.restoreDefaults();
        assertEquals("Default", fontFamilyComboBox(provider).getSelectedItem());
        provider.savePreferences(preferences);
        assertFalse(preferences.containsKey(FONT_FAMILY_KEY));
    }

    private static JComboBox<?> fontFamilyComboBox(ViewerPreferencesProvider provider) {
        JComboBox<?> comboBox = null;
        for (Component component : provider.getComponents()) {
            if (component instanceof JComboBox<?>) {
                assertEquals(null, comboBox, "Expected one font-family selector");
                comboBox = (JComboBox<?>) component;
            }
        }
        assertNotNull(comboBox, "Expected a font-family selector");
        return comboBox;
    }

    private static boolean containsItem(JComboBox<?> comboBox, String item) {
        for (int i = 0; i < comboBox.getItemCount(); i++) {
            if (item.equals(comboBox.getItemAt(i))) {
                return true;
            }
        }
        return false;
    }

    @Test
    void loadPreferences_readsSelectedWordHighlightSettings() {
        ViewerPreferencesProvider provider = new ViewerPreferencesProvider();
        Map<String, String> preferences = new HashMap<>();
        preferences.put(SELECTED_WORD_HIGHLIGHT_ENABLED, "false");
        preferences.put(SELECTED_WORD_HIGHLIGHT_COLOR, "0x112233");
        preferences.put(SEARCH_HIGHLIGHT_COLOR, "0x445566");
        preferences.put(SELECTION_HIGHLIGHT_COLOR, "0x778899");

        provider.loadPreferences(preferences);

        assertFalse(provider.selectedWordHighlightCheckBox.isSelected());
        assertEquals(Color.decode("0x112233"), provider.selectedWordHighlightColor);
        assertEquals("0x112233", provider.selectedWordHighlightColorButton.getText());
        assertEquals(Color.decode("0x445566"), provider.searchHighlightColor);
        assertEquals("0x445566", provider.searchHighlightColorButton.getText());
        assertEquals(Color.decode("0x778899"), provider.selectionHighlightColor);
        assertEquals("0x778899", provider.selectionHighlightColorButton.getText());
    }

    @Test
    void savePreferences_writesSelectedWordHighlightSettings() {
        ViewerPreferencesProvider provider = new ViewerPreferencesProvider();
        provider.selectedWordHighlightCheckBox.setSelected(false);
        provider.selectedWordHighlightColor = Color.decode(DEFAULT_SELECTED_WORD_HIGHLIGHT_COLOR);
        provider.searchHighlightColor = Color.decode(DEFAULT_SEARCH_HIGHLIGHT_COLOR);
        provider.selectionHighlightColor = Color.decode(DEFAULT_SELECTION_HIGHLIGHT_COLOR);
        provider.updateColorPreviews();

        Map<String, String> preferences = new HashMap<>();
        provider.savePreferences(preferences);

        assertEquals("false", preferences.get(SELECTED_WORD_HIGHLIGHT_ENABLED));
        assertEquals(DEFAULT_SELECTED_WORD_HIGHLIGHT_COLOR, preferences.get(SELECTED_WORD_HIGHLIGHT_COLOR));
        assertEquals(DEFAULT_SEARCH_HIGHLIGHT_COLOR, preferences.get(SEARCH_HIGHLIGHT_COLOR));
        assertEquals(DEFAULT_SELECTION_HIGHLIGHT_COLOR, preferences.get(SELECTION_HIGHLIGHT_COLOR));
    }
}
