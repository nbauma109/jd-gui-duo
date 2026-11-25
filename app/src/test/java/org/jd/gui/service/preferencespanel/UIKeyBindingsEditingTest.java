/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.preferencespanel;

import org.jd.gui.spi.PreferencesPanel;
import org.jd.gui.util.KeyBindings.Binding;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import static org.assertj.core.api.Assertions.assertThat;

/** Changing the shortcuts in the table of the key bindings. */
class UIKeyBindingsEditingTest {

    private static final int SHORTCUT = 2;

    private final List<PreferencesPanel> changes = new ArrayList<>();

    private UIKeyBindingsPreferencesProvider panel() {
        UIKeyBindingsPreferencesProvider panel = new UIKeyBindingsPreferencesProvider();
        panel.loadPreferences(new HashMap<>());
        panel.addPreferencesChangeListener(changes::add);
        return panel;
    }

    private static JTable table(UIKeyBindingsPreferencesProvider panel) {
        return PanelSupport.find(panel, JTable.class).get(0);
    }

    private static JTextField edit(JTable table, int row) {
        assertThat(table.editCellAt(row, SHORTCUT)).isTrue();
        return (JTextField) table.getEditorComponent();
    }

    private static void press(JTextField field, int modifiers, int key) {
        KeyEvent event = new KeyEvent(field, KeyEvent.KEY_PRESSED, 0, modifiers, key, KeyEvent.CHAR_UNDEFINED);
        for (KeyListener listener : field.getKeyListeners()) {
            listener.keyPressed(event);
        }
    }

    @Test
    void aNewCombinationReplacesTheShortcutAndAModifierAloneWaits() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            UIKeyBindingsPreferencesProvider panel = panel();
            JTable table = table(panel);
            Binding first = Binding.values()[0];
            JTextField field = edit(table, 0);

            press(field, InputEvent.CTRL_DOWN_MASK, KeyEvent.VK_CONTROL);
            assertThat(table.isEditing()).isTrue();
            press(field, InputEvent.CTRL_DOWN_MASK, KeyEvent.VK_F7);

            assertThat(table.isEditing()).isFalse();
            assertThat(panel.getAssignedKeyStroke(first)).isEqualTo(KeyStroke.getKeyStroke(KeyEvent.VK_F7, InputEvent.CTRL_DOWN_MASK));
            assertThat(changes).containsOnly(panel).isNotEmpty();
            press(edit(table, 0), 0, KeyEvent.VK_BACK_SPACE);
            assertThat(panel.getAssignedKeyStroke(first)).isNull();
        });
    }

    @Test
    void anEditThatIsGoingOnIsFinishedBeforeSaving() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            UIKeyBindingsPreferencesProvider panel = panel();
            JTable table = table(panel);
            edit(table, 0);
            Map<String, String> preferences = new HashMap<>();

            panel.savePreferences(preferences);

            assertThat(table.isEditing()).isFalse();
            assertThat(preferences).containsKey(Binding.values()[0].getPreferenceKey()).hasSize(Binding.values().length);
        });
    }

    @Test
    void theDeleteKeyClearsTheSelectedShortcut() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            UIKeyBindingsPreferencesProvider panel = panel();
            JTable table = table(panel);
            Binding first = Binding.values()[0];
            KeyStroke before = panel.getAssignedKeyStroke(first);
            Runnable clear = () -> table.getActionMap().get("clearShortcut").actionPerformed(new ActionEvent(table, ActionEvent.ACTION_PERFORMED, ""));

            clear.run(); // nothing is selected
            table.setCellSelectionEnabled(true);
            table.changeSelection(0, 0, false, false); // another column
            clear.run();
            assertThat(panel.getAssignedKeyStroke(first)).isEqualTo(before);
            table.changeSelection(0, SHORTCUT, false, false);
            clear.run();

            assertThat(panel.getAssignedKeyStroke(first)).isNull();
        });
    }

    @Test
    void aShortcutThatIsUsedTwiceIsMarked() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            UIKeyBindingsPreferencesProvider panel = panel();
            panel.init(Color.PINK);
            JTable table = table(panel);
            press(edit(table, 0), InputEvent.CTRL_DOWN_MASK, KeyEvent.VK_F7);
            press(edit(table, 1), InputEvent.CTRL_DOWN_MASK, KeyEvent.VK_F7);

            JLabel marked = (JLabel) table.prepareRenderer(table.getCellRenderer(0, SHORTCUT), 0, SHORTCUT);

            assertThat(panel.arePreferencesValid()).isFalse();
            assertThat(marked.getBackground()).isEqualTo(Color.PINK);
            assertThat(marked.getToolTipText()).startsWith("Duplicate shortcut: ");
            JLabel plain = (JLabel) table.prepareRenderer(table.getCellRenderer(2, SHORTCUT), 2, SHORTCUT);
            assertThat(plain.getBackground()).isNotEqualTo(Color.PINK);
            assertThat(plain.getToolTipText()).startsWith("Double-click to change.");
            assertThat(table.getColumnName(SHORTCUT)).isEqualTo("Shortcut");
            assertThat(table.getValueAt(0, 0)).isEqualTo(Binding.values()[0].getSectionTitle());
            assertThat(table.getValueAt(0, 1)).isEqualTo(Binding.values()[0].getLabel());
            assertThat(table.isCellEditable(0, 1)).isFalse();
            assertThat(panel.getPreferencesGroupTitle()).isEqualTo("Key Bindings").isEqualTo(panel.getPreferencesPanelTitle());
            assertThat(panel.useCompactDisplay()).isFalse();
        });
    }
}
