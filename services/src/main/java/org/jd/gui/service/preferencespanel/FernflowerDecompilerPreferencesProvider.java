/*
 * © 2008-2019 Emmanuel Dupuy
 * © 2022-2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.preferencespanel;

import org.jd.gui.spi.PreferencesPanel;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger.Severity;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;

import com.strobel.reflection.FieldInfo;
import com.strobel.reflection.Type;

import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

public class FernflowerDecompilerPreferencesProvider extends JPanel implements PreferencesPanel {

    private static final long serialVersionUID = 1L;

    private static final String TRUE = "1";
    private static final String FALSE = "0";

    private static final String[] LOG_LEVELS = EnumSet.allOf(Severity.class).stream().map(Severity::name).toArray(String[]::new);
    private static final Dimension FIELD_DIMENSION = new Dimension(250, 20);

    private Map<String, JComponent> components = new HashMap<>();

    public FernflowerDecompilerPreferencesProvider() {
        super(new GridLayout(0, 4));
        for (FieldInfo fieldInfo : Type.of(IFernflowerPreferences.class).getFields()) {
            if (String.class.getName().equals(fieldInfo.getFieldType().getTypeName())) {
                String trigram = (String) fieldInfo.getValue(null);
                if (trigram.matches("\\w{3}")) {
                    JComponent component;
                    Object defaultValue = IFernflowerPreferences.DEFAULTS.get(trigram);
                    if (!"mpm".equals(trigram) && (FALSE.equals(defaultValue) || TRUE.equals(defaultValue))) {
                        component = new JCheckBox();
                    } else if ("log".equals(trigram)) {
                        component = new JComboBox<>(LOG_LEVELS);
                    } else {
                        component = new JTextField();
                        component.setMinimumSize(FIELD_DIMENSION);
                        component.setMaximumSize(FIELD_DIMENSION);
                        component.setPreferredSize(FIELD_DIMENSION);
                    }
                    components.put(trigram, component);
                    JLabel label = new JLabel(fieldInfo.getName() + " (" + trigram + ")");
                    label.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));
                    add(label);
                    add(component);
                }
            }
        }
    }

    @Override
    public void restoreDefaults() {
        components.forEach(this::restoreDefault);
    }

    private void restoreDefault(String key, JComponent component) {
        show(component, String.valueOf(IFernflowerPreferences.DEFAULTS.getOrDefault(key, "")));
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
