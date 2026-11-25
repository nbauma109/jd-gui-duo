/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */
package org.jd.gui.util;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.jd.gui.api.API;
import org.jd.gui.util.KeyBindings.Binding;
import org.junit.jupiter.api.Test;

import java.awt.Font;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.Icon;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import javax.swing.plaf.metal.MetalLookAndFeel;

import static org.jd.gui.util.decompiler.GuiPreferences.FONT_FAMILY_KEY;
import static org.jd.gui.util.decompiler.GuiPreferences.FONT_SIZE_KEY;
import static org.junit.jupiter.api.Assertions.*;

class ThemeUtilTest {
    @Test
    void appliesApiPreferencesToProvidedTextArea() {
        Map<String, String> preferences = Map.of(
                "UIMainWindowPreferencesProvider.darkMode", "true",
                FONT_FAMILY_KEY, Font.SERIF,
                FONT_SIZE_KEY, "17");
        API api = (API) Proxy.newProxyInstance(API.class.getClassLoader(), new Class<?>[]{API.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getPreferences")) return preferences;
                    throw new AssertionError("Unexpected API call: " + method.getName());
                });
        RSyntaxTextArea area = new RSyntaxTextArea();
        RSyntaxTextArea light = ThemeUtil.applyTheme(Map.of(), new RSyntaxTextArea());

        assertSame(area, ThemeUtil.applyTheme(api, area));
        assertNotEquals(light.getBackground(), area.getBackground());
        assertEquals(Font.SERIF, area.getFont().getFamily());
        assertEquals(17, area.getFont().getSize());
    }

    @Test
    void appliesLightAndDarkThemes() {
        RSyntaxTextArea light = ThemeUtil.applyTheme(Map.of(), new RSyntaxTextArea());
        RSyntaxTextArea dark = ThemeUtil.applyTheme(Map.of("UIMainWindowPreferencesProvider.darkMode", "true"), new RSyntaxTextArea());

        assertNotEquals(light.getBackground(), dark.getBackground());
    }

    @Test
    void appliesFontPreferencesWhenThemeResourceCannotBeRead() throws Exception {
        AtomicBoolean readAttempted = new AtomicBoolean();
        URL[] locations = {
                ThemeUtil.class.getProtectionDomain().getCodeSource().getLocation(),
                RSyntaxTextArea.class.getProtectionDomain().getCodeSource().getLocation()
        };
        try (URLClassLoader loader = new URLClassLoader(locations, ThemeUtil.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("org.fife.") || name.equals(ThemeUtil.class.getName()) || name.startsWith(ThemeUtil.class.getName() + "$")) {
                    synchronized (getClassLoadingLock(name)) {
                        Class<?> type = findLoadedClass(name);
                        if (type == null) type = findClass(name);
                        if (resolve) resolveClass(type);
                        return type;
                    }
                }
                return super.loadClass(name, resolve);
            }

            @Override
            public InputStream getResourceAsStream(String name) {
                if (name.equals("rsyntaxtextarea/themes/eclipse.xml")) {
                    return new InputStream() {
                        @Override public int read() throws IOException {
                            readAttempted.set(true);
                            throw new IOException("Theme read failed");
                        }
                    };
                }
                return super.getResourceAsStream(name);
            }
        }) {
            loader.setDefaultAssertionStatus(true);
            Class<?> areaClass = loader.loadClass(RSyntaxTextArea.class.getName());
            Object area = areaClass.getConstructor().newInstance();
            Class<?> themeUtilClass = loader.loadClass(ThemeUtil.class.getName());
            Object result = themeUtilClass.getMethod("applyTheme", Map.class, areaClass)
                    .invoke(null, Map.of(FONT_SIZE_KEY, "17"), area);

            assertSame(area, result);
            assertTrue(readAttempted.get());
            assertEquals(17, ((JComponent) area).getFont().getSize());
            assertNotNull(((JComponent) area).getClientProperty("jd.defaultFontFamily"));
        }
    }

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

    @Test
    void handlesMissingBlankAndInvalidFontPreferences() {
        RSyntaxTextArea area = new RSyntaxTextArea();
        Font original = area.getFont();
        ThemeUtil.applyFontPreferences(Map.of(FONT_FAMILY_KEY, " ", FONT_SIZE_KEY, "invalid"), area);
        assertEquals(original, area.getFont());
        area.putClientProperty("jd.defaultFontFamily", " ");
        ThemeUtil.applyFontPreferences(Map.of(), area);
        assertEquals(original, area.getFont());

        area = ThemeUtil.applyTheme(Map.of(), area);
        String defaultFamily = area.getFont().getFamily();
        ThemeUtil.applyFontPreferences(Map.of(FONT_FAMILY_KEY, Font.SERIF), area);
        ThemeUtil.applyFontPreferences(Map.of(FONT_FAMILY_KEY, " "), area);
        assertEquals(defaultFamily, area.getFont().getFamily());
    }

    @Test
    void restoresLocalAndInheritedShortcutsAfterPreferencesChange() {
        RSyntaxTextArea area = new RSyntaxTextArea();
        InputMap input = new InputMap();
        InputMap parent = new InputMap();
        KeyStroke local = KeyStroke.getKeyStroke("ctrl F2");
        KeyStroke inherited = KeyStroke.getKeyStroke("ctrl F3");
        input.put(local, "local action");
        parent.put(inherited, "inherited action");
        input.setParent(parent);
        area.setInputMap(JComponent.WHEN_FOCUSED, input);
        Map<String, String> preferences = new HashMap<>();
        for (Binding binding : Binding.values()) {
            preferences.put(binding.getPreferenceKey(), "");
        }
        preferences.put(Binding.DISCONNECT.getPreferenceKey(), local.toString());
        preferences.put(Binding.RESUME.getPreferenceKey(), inherited.toString());
        preferences.put(Binding.STEP_INTO.getPreferenceKey(), local.toString());

        ThemeUtil.refreshDebugShortcuts(preferences, area);
        assertEquals("none", input.get(local));
        assertEquals("none", input.get(inherited));
        preferences.put(Binding.DISCONNECT.getPreferenceKey(), "");
        preferences.put(Binding.RESUME.getPreferenceKey(), "");
        preferences.put(Binding.STEP_INTO.getPreferenceKey(), "");
        ThemeUtil.refreshDebugShortcuts(preferences, area);
        assertEquals("local action", input.get(local));
        assertEquals("inherited action", input.get(inherited));
        assertEquals(1, input.keys().length);
        area.setInputMap(JComponent.WHEN_FOCUSED, new InputMap());
        ThemeUtil.refreshDebugShortcuts(preferences, area);
    }

    @Test
    void defaultLookAndFeelUsesPropertyOrSystemDefault() {
        String old = System.getProperty("swing.defaultlaf");
        try {
            System.setProperty("swing.defaultlaf", "example.LookAndFeel");
            assertEquals("example.LookAndFeel", ThemeUtil.getDefaultLookAndFeel());
            System.clearProperty("swing.defaultlaf");
            assertEquals(UIManager.getSystemLookAndFeelClassName(), ThemeUtil.getDefaultLookAndFeel());
        } finally {
            if (old == null) System.clearProperty("swing.defaultlaf");
            else System.setProperty("swing.defaultlaf", old);
        }
    }

    @Test
    void windowsMenuPaddingFixOnlyAppliesOnSupportedJava() throws Exception {
        LookAndFeel old = UIManager.getLookAndFeel();
        String[] keys = {"MenuItem.checkIconFactory", "MenuItem.checkIcon", "Menu.checkIconFactory", "Menu.checkIcon"};
        Object[] values = new Object[keys.length];
        for (int i = 0; i < keys.length; i++) values[i] = UIManager.getDefaults().get(keys[i]);
        try {
            UIManager.setLookAndFeel(new MetalLookAndFeel());
            Object marker = new Object();
            UIManager.put(keys[0], marker);
            ThemeUtil.applyMenuItemPaddingFix();
            assertSame(marker, UIManager.get(keys[0]));
            UIManager.setLookAndFeel(new MetalLookAndFeel() {
                @Override public String getID() { return "Windows"; }
            });
            UIManager.put(keys[0], marker);
            UIManager.put(keys[2], marker);
            ThemeUtil.applyMenuItemPaddingFix();
            if (Runtime.version().feature() >= 24) {
                assertNull(UIManager.get(keys[0]));
                assertNull(UIManager.get(keys[2]));
                Icon icon = (Icon) UIManager.get(keys[1]);
                assertSame(icon, UIManager.get(keys[3]));
                assertEquals(0, icon.getIconWidth());
                assertEquals(0, icon.getIconHeight());
                icon.paintIcon(null, null, 0, 0);
            } else {
                assertSame(marker, UIManager.get(keys[0]));
                assertSame(marker, UIManager.get(keys[2]));
            }
        } finally {
            UIManager.setLookAndFeel(old);
            for (int i = 0; i < keys.length; i++) UIManager.put(keys[i], values[i]);
        }
    }
}