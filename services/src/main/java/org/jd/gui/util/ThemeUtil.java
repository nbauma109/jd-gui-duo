/*
 * © 2025-2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */
package org.jd.gui.util;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.api.API;
import org.jd.gui.util.decompiler.GuiPreferences;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;

import javax.swing.UIManager;

public class ThemeUtil {

    private ThemeUtil() {
    }

    public static RSyntaxTextArea applyTheme(API api, RSyntaxTextArea rSyntaxTextArea) {
        return applyTheme(api.getPreferences(), rSyntaxTextArea);
    }

    public static RSyntaxTextArea applyTheme(Map<String, String> preferences, RSyntaxTextArea rSyntaxTextArea) {
        String themeResource;
        if (Boolean.parseBoolean(preferences.getOrDefault("UIMainWindowPreferencesProvider.darkMode", "false"))) {
            themeResource = "rsyntaxtextarea/themes/dark.xml";
        } else {
            themeResource = "rsyntaxtextarea/themes/eclipse.xml";
        }
        try (InputStream resourceAsStream = RSyntaxTextArea.class.getClassLoader().getResourceAsStream(themeResource)) {
            Theme theme = Theme.load(resourceAsStream);
            theme.apply(rSyntaxTextArea);
        } catch (IOException e) {
            assert ExceptionUtil.printStackTrace(e);
        }
        String fontSize = preferences.get(GuiPreferences.FONT_SIZE_KEY);
        if (fontSize != null) {
            try {
                rSyntaxTextArea.setFont(rSyntaxTextArea.getFont().deriveFont(Float.parseFloat(fontSize)));
            } catch (Exception e) {
                assert ExceptionUtil.printStackTrace(e);
            }
        }
        return rSyntaxTextArea;
    }

    public static String getDefaultLookAndFeel() {
        return Optional.ofNullable(System.getProperty("swing.defaultlaf")).orElseGet(UIManager::getSystemLookAndFeelClassName);
    }
}
