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

import java.awt.Component;
import java.awt.Graphics;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;

import javax.swing.Icon;
import javax.swing.LookAndFeel;
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

    private static final int JAVA_MAJOR_VERSION = Runtime.version().feature();

    private static final Icon ZERO_WIDTH_ICON = new Icon() {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            // zero-width spacer — nothing to paint
        }

        @Override
        public int getIconWidth() {
            return 0;
        }

        @Override
        public int getIconHeight() {
            return 0;
        }
    };

    public static String getDefaultLookAndFeel() {
        return Optional.ofNullable(System.getProperty("swing.defaultlaf")).orElseGet(UIManager::getSystemLookAndFeelClassName);
    }

    /**
     * Workaround for JDK-8370945: Windows LAF in Java 24+ lets the check-icon factory reserve
     * non-zero column space for regular JMenuItems, causing unwanted left padding in menus that
     * contain no checkboxes or radio buttons.
     *
     * Fix: after the LAF is installed, replace the "MenuItem.checkIconFactory" UIManager key with
     * null (so the factory's instanceof-guard in BasicMenuItemUI fails) and set "MenuItem.checkIcon"
     * to a zero-width placeholder.  JCheckBoxMenuItem and JRadioButtonMenuItem are unaffected
     * because they read "CheckBoxMenuItem.*" / "RadioButtonMenuItem.*" keys, not "MenuItem.*".
     *
     * Remove once OpenJDK PR #29730 ships in a released JDK.
     */
    public static void applyMenuItemPaddingFix() {
        if (JAVA_MAJOR_VERSION < 24) {
            return;
        }
        LookAndFeel laf = UIManager.getLookAndFeel();
        if (laf == null || !"Windows".equals(laf.getID())) {
            return;
        }
        // "MenuItem" covers JMenuItem; "Menu" covers JMenu (submenu items in a popup).
        // Both prefixes must be patched because popup column width is the max across all items,
        // and a single JMenu entry (e.g. "Recent Files") would otherwise inflate the whole column.
        UIManager.put("MenuItem.checkIconFactory", null);
        UIManager.put("MenuItem.checkIcon", ZERO_WIDTH_ICON);
        UIManager.put("Menu.checkIconFactory", null);
        UIManager.put("Menu.checkIcon", ZERO_WIDTH_ICON);
    }

}
