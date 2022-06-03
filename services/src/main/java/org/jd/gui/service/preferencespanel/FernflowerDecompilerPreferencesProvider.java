/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.preferencespanel;

import org.jd.gui.spi.PreferencesPanel;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger.Severity;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences.Description;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences.Name;

import com.strobel.reflection.FieldInfo;
import com.strobel.reflection.Type;

import java.awt.Color;
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
                    }
                    components.put(trigram, component);
                    Name name = fieldInfo.getAnnotation(Name.class);
                    Description description = fieldInfo.getAnnotation(Description.class);
                    JLabel label = new JLabel((name == null ? fieldInfo.getName() : name.value()) + " (" + trigram + ")");
                    if (description != null) {
                        label.setToolTipText(description.value());
                    }
                    label.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));
                    add(label);
                    add(component);
                }
            }
        }
    }

    @Override
    public void restoreDefaults() {
        for (Map.Entry<String, Object> defaultEntry : IFernflowerPreferences.DEFAULTS.entrySet()) {
            String componentKey = defaultEntry.getKey();
            Object defaultValue = defaultEntry.getValue();
            JComponent component = components.get(componentKey);
            if (component instanceof JComboBox) {
                ((JComboBox<String>) component).setSelectedItem(defaultValue);
            } else if (component instanceof JCheckBox) {
                ((JCheckBox) component).setSelected(TRUE.equals(defaultValue));
            } else if (component instanceof JTextField) {
                ((JTextField) component).setText(defaultValue.toString());
            }
        }
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

    @Override
    public JComponent getPanel() {
        return this;
    }

    @Override
    public void init(Color errorBackgroundColor) {
    }

    @Override
    public boolean isActivated() {
        return true;
    }

    @Override
    public void loadPreferences(Map<String, String> preferences) {
        for (Map.Entry<String, String> preference : preferences.entrySet()) {
            String preferenceKey = preference.getKey();
            String preferenceValue = preference.getValue();
            JComponent component = components.get(preferenceKey);
            if (preferenceValue != null) {
                if (component instanceof JComboBox) {
                    ((JComboBox<String>) component).setSelectedItem(preferenceValue);
                } else if (component instanceof JCheckBox) {
                    ((JCheckBox) component).setSelected(TRUE.equals(preferenceValue));
                } else if (component instanceof JTextField) {
                    ((JTextField) component).setText(preferenceValue);
                }
            } else if (component instanceof JComboBox) {
                ((JComboBox<String>) component).setSelectedItem(IFernflowerPreferences.DEFAULTS.get(preferenceKey));
            } else if (component instanceof JCheckBox) {
                ((JCheckBox) component).setSelected(TRUE.equals(IFernflowerPreferences.DEFAULTS.get(preferenceKey)));
            } else if (component instanceof JTextField) {
                ((JTextField) component).setText(IFernflowerPreferences.DEFAULTS.getOrDefault(preferenceKey, "").toString());
            }
        }
    }

    @Override
    public void savePreferences(Map<String, String> preferences) {
        for (Map.Entry<String, JComponent> componentEntry : components.entrySet()) {
            String componentKey = componentEntry.getKey();
            JComponent component = componentEntry.getValue();
            if (component instanceof JComboBox) {
                JComboBox<String> comboBox = (JComboBox<String>) component;
                preferences.put(componentKey, comboBox.getSelectedItem().toString());
            } else if (component instanceof JCheckBox) {
                preferences.put(componentKey, ((JCheckBox) component).isSelected() ? TRUE : FALSE);
            } else if (component instanceof JTextField) {
                String text = ((JTextField) component).getText();
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
    }
}
