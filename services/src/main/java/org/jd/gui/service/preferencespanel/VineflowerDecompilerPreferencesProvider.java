/*
 * © 2008-2019 Emmanuel Dupuy
 * © 2024-2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.preferencespanel;

import org.apache.commons.lang3.math.NumberUtils;
import org.jd.gui.spi.PreferencesPanel;
import org.vineflower.java.decompiler.api.DecompilerOption;
import org.vineflower.java.decompiler.main.Init;
import org.vineflower.java.decompiler.main.extern.IFernflowerLogger.Severity;
import org.vineflower.java.decompiler.main.extern.IFernflowerPreferences;

import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.swing.*;

public class VineflowerDecompilerPreferencesProvider extends JPanel implements PreferencesPanel {

    private static final long serialVersionUID = 1L;

    private static final String TRUE = "1";
    private static final String FALSE = "0";

    private static final String[] LOG_LEVELS = Arrays.stream(Severity.values()).map(Severity::name).toArray(String[]::new);
    private static final Dimension FIELD_DIMENSION = new Dimension(250, 20);

    private final Map<String, JComponent> components = new HashMap<>();

    public VineflowerDecompilerPreferencesProvider() {
        super(new GridLayout(0, 4));

        // Initialize Vineflower (to load plugins)
        Init.init();

        for (DecompilerOption option : DecompilerOption.getAll()) {
            JComponent component = switch (option.type()) {
                case BOOLEAN -> new JCheckBox();
                case INTEGER -> new JSpinner();
                case STRING -> {
                    if ("log-level".equals(option.id())) {
                        yield new JComboBox<>(LOG_LEVELS);
                    }

                    JTextField text = new JTextField();
                    text.setMaximumSize(FIELD_DIMENSION);
                    text.setMinimumSize(FIELD_DIMENSION);
                    text.setPreferredSize(FIELD_DIMENSION);
                    yield text;
                }
            };

            components.put(option.id(), component);
            JLabel label = new JLabel(option.name());
            label.setToolTipText(option.description());
            label.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));

            add(label);
            add(component);
        }

        JComponent dumpLines = new JCheckBox();
        components.put(IFernflowerPreferences.DUMP_ORIGINAL_LINES, dumpLines);
        JLabel linesLabel = new JLabel("Dump Line Numbers");
        linesLabel.setToolTipText("Dump original line numbers in the output");
        linesLabel.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));
        add(linesLabel);
        add(dumpLines);
    }

    @Override
    public void restoreDefaults() {
        Map<String, String> defaults = new HashMap<>();
        for (DecompilerOption option : DecompilerOption.getAll()) {
            defaults.put(option.id(), option.defaultValue());
        }
        // an option without a default (and the dump of the line numbers, which is not an option of the decompiler) is off, or empty
        components.forEach((key, component) -> show(component, Objects.toString(defaults.get(key), "")));
    }

    // --- PreferencesPanel --- //
    @Override
    public String getPreferencesGroupTitle() {
        return "Decompiler";
    }

    @Override
    public String getPreferencesPanelTitle() {
        return "Class file";
    }

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

    private static void show(JComponent component, String value) {
        if (component instanceof JComboBox<?> comboBox) {
            comboBox.setSelectedItem(value);
        } else if (component instanceof JCheckBox checkBox) {
            checkBox.setSelected(TRUE.equals(value));
        } else if (component instanceof JTextField textField) {
            textField.setText(value);
        } else if (component instanceof JSpinner spinner) {
            spinner.setValue(NumberUtils.toInt(value));
        }
    }

    @Override
    public void savePreferences(Map<String, String> preferences) {
        for (Map.Entry<String, JComponent> componentEntry : components.entrySet()) {
            String componentKey = componentEntry.getKey();
            JComponent component = componentEntry.getValue();
            if (component instanceof JComboBox<?> comboBox) {
                preferences.put(componentKey, comboBox.getSelectedItem().toString());
            } else if (component instanceof JCheckBox checkBox) {
                preferences.put(componentKey, checkBox.isSelected() ? TRUE : FALSE);
            } else if (component instanceof JSpinner spinner) {
                preferences.put(componentKey, spinner.getValue().toString());
            } else if (component instanceof JTextField textField) {
                String text = textField.getText();
                if (text.isEmpty()) {
                    preferences.remove(componentKey);
                } else {
                    preferences.put(componentKey, text);
                }
            }
        }
    }

    @Override
    public boolean arePreferencesValid() {
        return true;
    }

    @Override
    public void addPreferencesChangeListener(PreferencesPanel.PreferencesPanelChangeListener listener) {
        // nothing to do
    }

    @Override
    public boolean useCompactDisplay() {
        return false;
    }
}
