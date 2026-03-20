/*
 * Copyright (c) 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.preferencespanel;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

import static org.jd.gui.util.decompiler.GuiPreferences.DEFAULT_SEARCH_HIGHLIGHT_COLOR;
import static org.jd.gui.util.decompiler.GuiPreferences.DEFAULT_SELECTION_HIGHLIGHT_COLOR;
import static org.jd.gui.util.decompiler.GuiPreferences.SEARCH_HIGHLIGHT_COLOR;
import static org.jd.gui.util.decompiler.GuiPreferences.SELECTION_HIGHLIGHT_COLOR;
import static org.jd.gui.util.decompiler.GuiPreferences.DEFAULT_SELECTED_WORD_HIGHLIGHT_COLOR;
import static org.jd.gui.util.decompiler.GuiPreferences.SELECTED_WORD_HIGHLIGHT_COLOR;
import static org.jd.gui.util.decompiler.GuiPreferences.SELECTED_WORD_HIGHLIGHT_ENABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ViewerPreferencesProviderTest {

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
