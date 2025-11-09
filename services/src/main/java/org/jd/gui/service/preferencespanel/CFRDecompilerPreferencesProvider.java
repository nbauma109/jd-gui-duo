/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.preferencespanel;

import org.benf.cfr.reader.util.Troolean;
import org.benf.cfr.reader.util.getopt.OptionDecoderParam;
import org.benf.cfr.reader.util.getopt.OptionsImpl;
import org.benf.cfr.reader.util.getopt.PermittedOptionProvider.ArgumentParam;
import org.jd.gui.spi.PreferencesPanel;

import com.strobel.reflection.BindingFlags;
import com.strobel.reflection.FieldInfo;
import com.strobel.reflection.MethodInfo;
import com.strobel.reflection.Type;
import com.strobel.reflection.TypeList;

import java.awt.Color;
import java.awt.GridLayout;
import java.util.HashMap;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

public class CFRDecompilerPreferencesProvider extends JPanel implements PreferencesPanel {

    private static final long serialVersionUID = 1L;

    private Map<String, JComponent> components = new HashMap<>();
    private Map<String, String> defaults = new HashMap<>();

    public CFRDecompilerPreferencesProvider() {
        super(new GridLayout(0, 6));
        for (FieldInfo fieldInfo : Type.of(OptionsImpl.class).getFields()) {
            Type<?> fieldType = fieldInfo.getFieldType();
            if (fieldType.getName().startsWith("Argument")) {
                ArgumentParam<?, ?> argInstance = (ArgumentParam<?, ?>) fieldInfo.getValue(null);
                MethodInfo fnMethod = fieldType.getMethod("getFn", BindingFlags.All);
                fnMethod.getRawMethod().setAccessible(true);
                OptionDecoderParam<?, ?> optionDecoderParam = (OptionDecoderParam<?, ?>) fnMethod.invoke(argInstance);
                String defaultValue = optionDecoderParam.getDefaultValue();
                String rangeDescription = optionDecoderParam.getRangeDescription();
                MethodInfo describeMethod = fieldType.getMethod("describe", BindingFlags.All);
                describeMethod.getRawMethod().setAccessible(true);
                String helpText = (String) describeMethod.invoke(argInstance);
                String helpTextHTML = "<html>" + helpText.replace("\n", "<br>") + "</html>";
                TypeList typeArguments = fieldType.getTypeArguments();
                Type<?> optionType = typeArguments.get(0);
                JComponent component;
                if (typeArguments.size() == 1 && Boolean.class.getName().equals(optionType.getTypeName())) {
                    component = new JCheckBox();
                } else if (Troolean.class.getName().equals(optionType.getTypeName()) || (typeArguments.size() > 1 && Boolean.class.getName().equals(optionType.getTypeName()))) {
                    component = new JComboBox<>(new String[] { "auto", "true", "false" });
                } else if (rangeDescription.startsWith("One of [") && rangeDescription.endsWith("]")) {
                    component = new JComboBox<>(rangeDescription.substring(8, rangeDescription.length() - 1).split(", "));
                } else {
                    component = new JTextField();
                }
                JLabel label = new JLabel(argInstance.getName());
                label.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));
                label.setToolTipText(helpTextHTML);
                component.setToolTipText(helpTextHTML);
                add(label);
                add(component);
                components.put(argInstance.getName(), component);
                if (defaultValue != null && defaultValue.matches("\\w+")) {
                    defaults.put(argInstance.getName(), defaultValue);
                } else if (component instanceof JComboBox) {
                    defaults.put(argInstance.getName(), "auto");
                }
            }
        }
    }

    @Override
    public void restoreDefaults() {
        for (Map.Entry<String, String> defaultEntry : defaults.entrySet()) {
            String componentKey = defaultEntry.getKey();
            String defaultValue = defaultEntry.getValue();
            JComponent component = components.get(componentKey);
            if (component instanceof JComboBox<?> comboBox) {
                comboBox.setSelectedItem(defaultValue);
            } else if (component instanceof JCheckBox checkBox) {
                checkBox.setSelected("true".equals(defaultValue));
            } else if (component instanceof JTextField textField) {
                textField.setText(defaultValue);
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
        // nothing to do
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
                if (component instanceof JComboBox<?> comboBox) {
                    comboBox.setSelectedItem(preferenceValue);
                } else if (component instanceof JCheckBox checkBox) {
                    checkBox.setSelected("true".equals(preferenceValue));
                } else if (component instanceof JTextField textField) {
                    textField.setText(preferenceValue);
                }
            } else if (component instanceof JComboBox<?> comboBox) {
                comboBox.setSelectedItem(defaults.getOrDefault(preferenceKey, "auto"));
            } else if (component instanceof JCheckBox checkBox) {
                checkBox.setSelected("true".equals(defaults.get(preferenceKey)));
            } else if (component instanceof JTextField textField) {
                textField.setText(defaults.getOrDefault(preferenceKey, ""));
            }
        }
    }

    @Override
    public void savePreferences(Map<String, String> preferences) {
        for (Map.Entry<String, JComponent> componentEntry : components.entrySet()) {
            String componentKey = componentEntry.getKey();
            JComponent component = componentEntry.getValue();
            if (component instanceof JComboBox<?> comboBox) {
                if ("auto".equals(comboBox.getSelectedItem().toString())) {
                    preferences.remove(componentKey);
                } else {
                    preferences.put(componentKey, comboBox.getSelectedItem().toString());
                }
            } else if (component instanceof JCheckBox checkBox) {
                preferences.put(componentKey, Boolean.toString(checkBox.isSelected()));
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
