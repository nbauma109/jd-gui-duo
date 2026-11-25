/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.preferencespanel;

import org.apache.commons.lang3.math.NumberUtils;

import java.awt.GridLayout;
import java.util.HashMap;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;

/** The options of a decompiler, each one shown by a component: a check box, a combo box, a spinner or a text field. */
abstract class AbstractDecompilerPreferencesProvider extends JPanel implements DecompilerOptionsPanel {

    private static final long serialVersionUID = 1L;

    private final transient Map<String, JComponent> components = new HashMap<>();

    /** How a check box is saved when it is selected, and when it is not. */
    private final String selected;
    private final String unselected;

    protected AbstractDecompilerPreferencesProvider(int columns, String selected, String unselected) {
        super(new GridLayout(0, columns));
        this.selected = selected;
        this.unselected = unselected;
    }

    protected void register(String key, JComponent component) {
        components.put(key, component);
    }

    /** Adds the component of an option after a label with its name, and gives the label. */
    protected JLabel addOption(String key, JComponent component) {
        JLabel label = new JLabel(key);
        label.setLabelFor(component);
        label.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));
        register(key, component);
        add(label);
        add(component);
        return label;
    }

    /** What the component of an option shows when nothing is saved for it; empty when the option has no default. */
    protected abstract String defaultValue(String key, JComponent component);

    /** Whether nothing is saved for an option whose component shows the value. */
    protected boolean isUnset(JComponent component, String value) {
        return component instanceof JTextField && value.isEmpty();
    }

    @Override
    public void restoreDefaults() {
        components.forEach((key, component) -> {
            // a combo box without a default shows its first item
            if (component instanceof JComboBox<?> comboBox) {
                comboBox.setSelectedIndex(0);
            }
            show(component, defaultValue(key, component));
        });
    }

    // --- PreferencesPanel --- //
    /** Shows the saved value of every option, and the default of an option that none is saved for: saving writes them all. */
    @Override
    public void loadPreferences(Map<String, String> preferences) {
        restoreDefaults();
        components.forEach((key, component) -> {
            String value = preferences.get(key);
            if (value != null) {
                show(component, value);
            }
        });
    }

    private void show(JComponent component, String value) {
        if (component instanceof JComboBox<?> comboBox) {
            comboBox.setSelectedItem(value);
        } else if (component instanceof JCheckBox checkBox) {
            checkBox.setSelected(selected.equals(value));
        } else if (component instanceof JSpinner spinner) {
            spinner.setValue(NumberUtils.toInt(value));
        } else if (component instanceof JTextField textField) {
            textField.setText(value);
        }
    }

    private String shownValue(JComponent component) {
        return switch (component) {
            case JComboBox<?> comboBox -> comboBox.getSelectedItem().toString();
            case JCheckBox checkBox -> checkBox.isSelected() ? selected : unselected;
            case JSpinner spinner -> spinner.getValue().toString();
            default -> ((JTextField) component).getText();
        };
    }

    @Override
    public void savePreferences(Map<String, String> preferences) {
        components.forEach((key, component) -> {
            String value = shownValue(component);
            if (isUnset(component, value)) {
                preferences.remove(key);
            } else {
                preferences.put(key, value);
            }
        });
    }
}
