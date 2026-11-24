/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */
package org.jd.gui.util;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.junit.jupiter.api.Test;

import java.awt.Font;
import java.util.HashMap;
import java.util.Map;

import static org.jd.gui.util.decompiler.GuiPreferences.FONT_FAMILY_KEY;
import static org.jd.gui.util.decompiler.GuiPreferences.FONT_SIZE_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ThemeUtilFontTest {
    @Test
    void appliesAndRestoresFontFamilyWithoutChangingSize() {
        Map<String, String> preferences = new HashMap<>();
        preferences.put(FONT_SIZE_KEY, "17");
        RSyntaxTextArea area = ThemeUtil.applyTheme(preferences, new RSyntaxTextArea());
        String defaultFamily = area.getFont().getFamily();
        String selectedFamily = defaultFamily.equals(Font.SERIF) ? Font.MONOSPACED : Font.SERIF;

        preferences.put(FONT_FAMILY_KEY, selectedFamily);
        ThemeUtil.applyFontPreferences(preferences, area);
        assertEquals(selectedFamily, area.getFont().getFamily());
        assertEquals(17, area.getFont().getSize());

        RSyntaxTextArea newArea = ThemeUtil.applyTheme(preferences, new RSyntaxTextArea());
        assertEquals(selectedFamily, newArea.getFont().getFamily());
        assertEquals(17, newArea.getFont().getSize());

        preferences.remove(FONT_FAMILY_KEY);
        ThemeUtil.applyFontPreferences(preferences, area);
        assertEquals(defaultFamily, area.getFont().getFamily());
        assertEquals(17, area.getFont().getSize());
    }
}