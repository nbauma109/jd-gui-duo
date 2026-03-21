/*
 * Copyright (c) 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.preferencespanel;

import org.jd.gui.spi.PreferencesPanel;
import org.jd.gui.util.KeyBindings;
import org.jd.gui.util.KeyBindings.Binding;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.AbstractCellEditor;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;

public class UIKeyBindingsPreferencesProvider extends JPanel implements PreferencesPanel {

    private static final long serialVersionUID = 1L;
    private static final int CONTEXT_COLUMN = 0;
    private static final int ACTION_COLUMN = 1;
    private static final int SHORTCUT_COLUMN = 2;

    private final KeyBindingsTableModel tableModel = new KeyBindingsTableModel();
    private final JTable shortcutTable = new JTable(tableModel);
    private final Color defaultBackgroundColor;
    private final Map<Integer, String> duplicateMessages = new HashMap<>();

    private transient PreferencesPanelChangeListener listener;
    private Color errorBackgroundColor = Color.RED;
    private boolean preferencesValid = true;

    public UIKeyBindingsPreferencesProvider() {
        super(new BorderLayout(0, 8));

        add(new JLabel("Double-click a shortcut and press a new combination. Use Backspace or Delete to clear it."), BorderLayout.NORTH);

        shortcutTable.setFillsViewportHeight(true);
        shortcutTable.setRowHeight(24);
        shortcutTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        shortcutTable.getTableHeader().setReorderingAllowed(false);
        shortcutTable.getColumnModel().getColumn(CONTEXT_COLUMN).setPreferredWidth(170);
        shortcutTable.getColumnModel().getColumn(ACTION_COLUMN).setPreferredWidth(260);
        shortcutTable.getColumnModel().getColumn(SHORTCUT_COLUMN).setPreferredWidth(170);
        shortcutTable.getColumnModel().getColumn(SHORTCUT_COLUMN).setCellRenderer(new ShortcutCellRenderer());
        shortcutTable.getColumnModel().getColumn(SHORTCUT_COLUMN).setCellEditor(new ShortcutCellEditor());

        installClearShortcutAction();

        JScrollPane scrollPane = new JScrollPane(shortcutTable);
        scrollPane.setPreferredSize(new Dimension(720, 500));
        add(scrollPane, BorderLayout.CENTER);

        defaultBackgroundColor = shortcutTable.getBackground();
        validateShortcuts();
    }

    @Override
    public String getPreferencesGroupTitle() {
        return "Key Bindings";
    }

    @Override
    public String getPreferencesPanelTitle() {
        return "Key Bindings";
    }

    @Override
    public void init(Color errorBackgroundColor) {
        this.errorBackgroundColor = errorBackgroundColor;
        validateShortcuts();
    }

    @Override
    public void loadPreferences(Map<String, String> preferences) {
        stopEditing();

        for (Binding binding : Binding.values()) {
            tableModel.setKeyStroke(binding, KeyBindings.getConfiguredKeyStroke(preferences, binding));
        }

        validateShortcuts();
    }

    @Override
    public void savePreferences(Map<String, String> preferences) {
        stopEditing();

        for (Binding binding : Binding.values()) {
            preferences.put(binding.getPreferenceKey(), KeyBindings.toPreferenceValue(tableModel.getKeyStroke(binding)));
        }
    }

    @Override
    public boolean arePreferencesValid() {
        return preferencesValid;
    }

    @Override
    public void addPreferencesChangeListener(PreferencesPanelChangeListener listener) {
        this.listener = listener;
    }

    @Override
    public void restoreDefaults() {
        stopEditing();

        for (Binding binding : Binding.values()) {
            tableModel.setKeyStroke(binding, binding.getDefaultKeyStroke());
        }

        onShortcutChanged();
    }

    @Override
    public boolean useCompactDisplay() {
        return false;
    }

    KeyStroke getAssignedKeyStroke(Binding binding) {
        return tableModel.getKeyStroke(binding);
    }

    private void setAssignedKeyStroke(int row, KeyStroke keyStroke) {
        tableModel.setKeyStroke(tableModel.getBindingAt(row), keyStroke);
        onShortcutChanged();
    }

    private void onShortcutChanged() {
        validateShortcuts();
        if (listener != null) {
            listener.preferencesPanelChanged(this);
        }
    }

    private void validateShortcuts() {
        Map<String, List<Integer>> duplicates = new HashMap<>();
        duplicateMessages.clear();

        for (int row = 0; row < tableModel.getRowCount(); row++) {
            KeyStroke keyStroke = tableModel.getKeyStroke(tableModel.getBindingAt(row));
            if (keyStroke != null) {
                duplicates.computeIfAbsent(KeyBindings.toPreferenceValue(keyStroke), _ -> new ArrayList<>()).add(row);
            }
        }

        preferencesValid = true;

        for (Map.Entry<String, List<Integer>> entry : duplicates.entrySet()) {
            if (entry.getValue().size() > 1) {
                preferencesValid = false;
                String message = "Duplicate shortcut: " + KeyBindings.toDisplayText(KeyStroke.getKeyStroke(entry.getKey()));
                for (Integer row : entry.getValue()) {
                    duplicateMessages.put(row, message);
                }
            }
        }

        shortcutTable.repaint();
    }

    private void stopEditing() {
        if (shortcutTable.isEditing()) {
            TableCellEditor editor = shortcutTable.getCellEditor();
            if (editor != null) {
                editor.stopCellEditing();
            }
        }
    }

    private void installClearShortcutAction() {
        InputMap inputMap = shortcutTable.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "clearShortcut");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "clearShortcut");
        shortcutTable.getActionMap().put("clearShortcut", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                int row = shortcutTable.getSelectedRow();
                int column = shortcutTable.getSelectedColumn();
                if ((row != -1) && ((column == -1) || (column == SHORTCUT_COLUMN))) {
                    setAssignedKeyStroke(row, null);
                }
            }
        });
    }

    private final class ShortcutCellRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            if (column == SHORTCUT_COLUMN) {
                setText(KeyBindings.toDisplayText((KeyStroke) value));
                String duplicateMessage = duplicateMessages.get(row);
                setToolTipText(duplicateMessage != null ? duplicateMessage : "Double-click to change. Use Delete or Backspace to clear.");
                if (!isSelected) {
                    setBackground(duplicateMessage != null ? errorBackgroundColor : defaultBackgroundColor);
                }
            } else {
                setToolTipText(null);
            }

            return this;
        }
    }

    private final class ShortcutCellEditor extends AbstractCellEditor implements TableCellEditor {
        private static final long serialVersionUID = 1L;

        private final JTextField editor = new JTextField();
        private int editingRow = -1;
        private KeyStroke currentKeyStroke;

        private ShortcutCellEditor() {
            editor.setEditable(false);
            editor.setToolTipText("Press a shortcut to change it");
            editor.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (KeyBindings.isModifierKey(e.getKeyCode())) {
                        return;
                    }

                    if ((e.getKeyCode() == KeyEvent.VK_BACK_SPACE) || (e.getKeyCode() == KeyEvent.VK_DELETE)) {
                        currentKeyStroke = null;
                    } else {
                        currentKeyStroke = KeyStroke.getKeyStrokeForEvent(e);
                    }

                    editor.setText(KeyBindings.toDisplayText(currentKeyStroke));
                    stopCellEditing();
                    e.consume();
                }
            });
        }

        @Override
        public Object getCellEditorValue() {
            return currentKeyStroke;
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            editingRow = row;
            currentKeyStroke = (KeyStroke) value;
            editor.setText(KeyBindings.toDisplayText(currentKeyStroke));
            return editor;
        }

        @Override
        public boolean stopCellEditing() {
            if (editingRow != -1) {
                setAssignedKeyStroke(editingRow, currentKeyStroke);
                editingRow = -1;
            }

            return super.stopCellEditing();
        }
    }

    private static final class KeyBindingsTableModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;

        private static final String[] COLUMN_NAMES = { "Context", "Action", "Shortcut" };

        private final List<Binding> bindings = List.of(Binding.values());
        private final EnumMap<Binding, KeyStroke> assignedKeyStrokes = new EnumMap<>(Binding.class);

        @Override
        public int getRowCount() {
            return bindings.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMN_NAMES.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMN_NAMES[column];
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == SHORTCUT_COLUMN ? KeyStroke.class : String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == SHORTCUT_COLUMN;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Binding binding = bindings.get(rowIndex);
            return switch (columnIndex) {
                case CONTEXT_COLUMN -> binding.getSectionTitle();
                case ACTION_COLUMN -> binding.getLabel();
                case SHORTCUT_COLUMN -> assignedKeyStrokes.get(binding);
                default -> "";
            };
        }

        Binding getBindingAt(int rowIndex) {
            return bindings.get(rowIndex);
        }

        KeyStroke getKeyStroke(Binding binding) {
            return assignedKeyStrokes.get(binding);
        }

        void setKeyStroke(Binding binding, KeyStroke keyStroke) {
            assignedKeyStrokes.put(binding, keyStroke);
            fireTableRowsUpdated(bindings.indexOf(binding), bindings.indexOf(binding));
        }
    }
}
