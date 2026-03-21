/*
 * Copyright (c) 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.util;

import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Map;

import javax.swing.Action;
import javax.swing.KeyStroke;

public final class KeyBindings {

    private KeyBindings() {
    }

    public enum Binding {
        OPEN_FILE("Main Window", "UIKeyBindingsPreferencesProvider.openFile", "Open File...", menuShortcut(KeyEvent.VK_O)),
        CLOSE("Main Window", "UIKeyBindingsPreferencesProvider.close", "Close", menuShortcut(KeyEvent.VK_W)),
        SAVE("Main Window", "UIKeyBindingsPreferencesProvider.save", "Save", menuShortcut(KeyEvent.VK_S)),
        SAVE_ALL_SOURCES("Main Window", "UIKeyBindingsPreferencesProvider.saveAllSources", "Save All Sources", menuShortcut(KeyEvent.VK_S, InputEvent.ALT_DOWN_MASK)),
        EXIT("Main Window", "UIKeyBindingsPreferencesProvider.exit", "Exit", shortcut(KeyEvent.VK_X, InputEvent.ALT_DOWN_MASK)),
        COPY("Main Window", "UIKeyBindingsPreferencesProvider.copy", "Copy", menuShortcut(KeyEvent.VK_C)),
        PASTE_LOG("Main Window", "UIKeyBindingsPreferencesProvider.pasteLog", "Paste Log", menuShortcut(KeyEvent.VK_V)),
        SELECT_ALL("Main Window", "UIKeyBindingsPreferencesProvider.selectAll", "Select All", menuShortcut(KeyEvent.VK_A)),
        FIND("Main Window", "UIKeyBindingsPreferencesProvider.find", "Find...", menuShortcut(KeyEvent.VK_F)),
        OPEN_TYPE("Main Window", "UIKeyBindingsPreferencesProvider.openType", "Open Type...", menuShortcut(KeyEvent.VK_T)),
        OPEN_TYPE_HIERARCHY("Main Window", "UIKeyBindingsPreferencesProvider.openTypeHierarchy", "Open Type Hierarchy...", menuShortcut(KeyEvent.VK_H)),
        QUICK_OUTLINE("Main Window", "UIKeyBindingsPreferencesProvider.quickOutline", "Quick Outline...", menuShortcut(KeyEvent.VK_O, InputEvent.SHIFT_DOWN_MASK)),
        GO_TO_LINE("Main Window", "UIKeyBindingsPreferencesProvider.goToLine", "Go to Line...", menuShortcut(KeyEvent.VK_L)),
        BACK("Main Window", "UIKeyBindingsPreferencesProvider.back", "Back", shortcut(KeyEvent.VK_LEFT, InputEvent.ALT_DOWN_MASK)),
        FORWARD("Main Window", "UIKeyBindingsPreferencesProvider.forward", "Forward", shortcut(KeyEvent.VK_RIGHT, InputEvent.ALT_DOWN_MASK)),
        SEARCH("Main Window", "UIKeyBindingsPreferencesProvider.search", "Search...", menuShortcut(KeyEvent.VK_S, InputEvent.SHIFT_DOWN_MASK)),
        PREFERENCES("Main Window", "UIKeyBindingsPreferencesProvider.preferences", "Preferences...", menuShortcut(KeyEvent.VK_P, InputEvent.SHIFT_DOWN_MASK)),
        ABOUT("Main Window", "UIKeyBindingsPreferencesProvider.about", "About...", shortcut(KeyEvent.VK_F1, 0)),
        FIND_NEXT("Incremental Search", "UIKeyBindingsPreferencesProvider.findNext", "Find Next", shortcut(KeyEvent.VK_ENTER, 0)),
        FIND_PREVIOUS("Incremental Search", "UIKeyBindingsPreferencesProvider.findPrevious", "Find Previous", shortcut(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK)),
        FIND_MATCH_CASE("Incremental Search", "UIKeyBindingsPreferencesProvider.findMatchCase", "Toggle Match Case", searchOptionShortcut(KeyEvent.VK_C)),
        FIND_WHOLE_WORD("Incremental Search", "UIKeyBindingsPreferencesProvider.findWholeWord", "Toggle Whole Word", searchOptionShortcut(KeyEvent.VK_O)),
        FIND_REGEX("Incremental Search", "UIKeyBindingsPreferencesProvider.findRegex", "Toggle Regular Expression", searchOptionShortcut(KeyEvent.VK_G)),
        FIND_HIGHLIGHT("Incremental Search", "UIKeyBindingsPreferencesProvider.findHighlight", "Toggle Highlight Matches", searchOptionShortcut(KeyEvent.VK_H)),
        FIND_WRAP_AROUND("Incremental Search", "UIKeyBindingsPreferencesProvider.findWrapAround", "Toggle Wrap Around", searchOptionShortcut(KeyEvent.VK_U));

        private final String sectionTitle;
        private final String preferenceKey;
        private final String label;
        private final DefaultShortcutProvider defaultShortcutProvider;

        Binding(String sectionTitle, String preferenceKey, String label, DefaultShortcutProvider defaultShortcutProvider) {
            this.sectionTitle = sectionTitle;
            this.preferenceKey = preferenceKey;
            this.label = label;
            this.defaultShortcutProvider = defaultShortcutProvider;
        }

        public String getSectionTitle() {
            return sectionTitle;
        }

        public String getPreferenceKey() {
            return preferenceKey;
        }

        public String getLabel() {
            return label;
        }

        public KeyStroke getDefaultKeyStroke() {
            return defaultShortcutProvider.get(Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx());
        }
    }

    public static void apply(Map<String, String> preferences, Map<Binding, Action> actions) {
        for (Map.Entry<Binding, Action> entry : actions.entrySet()) {
            entry.getValue().putValue(Action.ACCELERATOR_KEY, getConfiguredKeyStroke(preferences, entry.getKey()));
        }
    }

    public static KeyStroke getConfiguredKeyStroke(Map<String, String> preferences, Binding binding) {
        String value = preferences.get(binding.getPreferenceKey());

        if (value == null) {
            return binding.getDefaultKeyStroke();
        }
        if (value.isBlank()) {
            return null;
        }

        KeyStroke keyStroke = KeyStroke.getKeyStroke(value.trim());
        return keyStroke != null ? keyStroke : binding.getDefaultKeyStroke();
    }

    public static String toPreferenceValue(KeyStroke keyStroke) {
        return keyStroke == null ? "" : keyStroke.toString();
    }

    public static String toDisplayText(KeyStroke keyStroke) {
        if (keyStroke == null) {
            return "Not set";
        }

        String modifiers = getModifierText(keyStroke.getModifiers());
        String keyText = getKeyText(keyStroke);

        return modifiers.isEmpty() ? keyText : modifiers + "+" + keyText;
    }

    public static boolean isModifierKey(int keyCode) {
        return (keyCode == KeyEvent.VK_SHIFT) || (keyCode == KeyEvent.VK_CONTROL) || (keyCode == KeyEvent.VK_ALT)
            || (keyCode == KeyEvent.VK_ALT_GRAPH) || (keyCode == KeyEvent.VK_META);
    }

    private static String getKeyText(KeyStroke keyStroke) {
        if (keyStroke.getKeyCode() != KeyEvent.VK_UNDEFINED) {
            return KeyEvent.getKeyText(keyStroke.getKeyCode());
        }

        char keyChar = keyStroke.getKeyChar();
        return keyChar == KeyEvent.CHAR_UNDEFINED ? "" : Character.toString(Character.toUpperCase(keyChar));
    }

    private static String getModifierText(int modifiers) {
        StringBuilder builder = new StringBuilder();

        appendModifier(builder, modifiers, InputEvent.CTRL_DOWN_MASK, "Ctrl");
        appendModifier(builder, modifiers, InputEvent.ALT_DOWN_MASK, "Alt");
        appendModifier(builder, modifiers, InputEvent.SHIFT_DOWN_MASK, "Shift");
        appendModifier(builder, modifiers, InputEvent.META_DOWN_MASK, "\u2318");
        appendModifier(builder, modifiers, InputEvent.ALT_GRAPH_DOWN_MASK, "Alt Graph");

        return builder.toString();
    }

    private static void appendModifier(StringBuilder builder, int modifiers, int mask, String label) {
        if ((modifiers & mask) == 0) {
            return;
        }

        if (!builder.isEmpty()) {
            builder.append('+');
        }

        builder.append(label);
    }

    private static DefaultShortcutProvider menuShortcut(int keyCode) {
        return menuShortcut(keyCode, 0);
    }

    private static DefaultShortcutProvider menuShortcut(int keyCode, int extraModifiers) {
        return menuShortcutMask -> KeyStroke.getKeyStroke(keyCode, menuShortcutMask | extraModifiers);
    }

    private static DefaultShortcutProvider searchOptionShortcut(int keyCode) {
        return menuShortcutMask -> {
            int modifiers = isMac() ? InputEvent.CTRL_DOWN_MASK | InputEvent.META_DOWN_MASK : InputEvent.ALT_DOWN_MASK;
            return KeyStroke.getKeyStroke(keyCode, modifiers);
        };
    }

    private static DefaultShortcutProvider shortcut(int keyCode, int modifiers) {
        return _ -> KeyStroke.getKeyStroke(keyCode, modifiers);
    }

    private static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase().contains("mac");
    }

    @FunctionalInterface
    private interface DefaultShortcutProvider {
        KeyStroke get(int menuShortcutMask);
    }
}
