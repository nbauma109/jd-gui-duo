/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.preferencespanel;

import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.spi.PreferencesPanel;

import com.beust.jcommander.Parameter;
import com.strobel.decompiler.CommandLineOptions;

import java.awt.Color;
import java.awt.GridLayout;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

public class ProcyonDecompilerPreferencesProvider extends JPanel implements PreferencesPanel {

    private static final long serialVersionUID = 1L;

    private static final Class<?>[] BOOLEAN_PARAM = { boolean.class };
    private static final Class<?>[] INT_PARAM = { int.class };

    private Map<String, JComponent> components = new HashMap<>();
    private Map<String, String> defaults = new HashMap<>();

    public ProcyonDecompilerPreferencesProvider() {
        super(new GridLayout(0, 2));
        CommandLineOptions defaultOptions = new CommandLineOptions();
        Map<String, Field> fields = findFields();
        Map<String, Method> getters = findGetters();
        Method[] methods = CommandLineOptions.class.getMethods();
        Arrays.sort(methods, Comparator.comparing(Method::getName));
        for (Method method : methods) {
            if (method.getName().startsWith("set")) {
                String optionKey = method.getName().substring(3);
                Method getter = getters.get(optionKey);
                if (getter == null) {
                    continue;
                }
                try {
                    JComponent component = null;
                    if (Arrays.equals(method.getParameterTypes(), BOOLEAN_PARAM)) {
                        Boolean defaultValue = (Boolean) getter.invoke(defaultOptions);
                        component = new JCheckBox();
                        defaults.put(optionKey, Boolean.toString(defaultValue));
                    } else if (Arrays.equals(method.getParameterTypes(), INT_PARAM)) {
                        Integer defaultValue = (Integer) getter.invoke(defaultOptions);
                        component = new JTextField();
                        defaults.put(optionKey, Integer.toString(defaultValue));
                    }
                    if (component != null) {
                        JLabel label = new JLabel(optionKey);
                        label.setLabelFor(component);
                        label.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));
                        components.put(optionKey, component);
                        add(label);
                        add(component);
                        String fieldName = '_' + optionKey.substring(0, 1).toLowerCase() + optionKey.substring(1);
                        Field field = fields.get(fieldName);
                        if (field == null) {
                            continue;
                        }
                        Parameter parameterAnnotation = field.getAnnotation(Parameter.class);
                        if (parameterAnnotation != null) {
                            component.setToolTipText(parameterAnnotation.description());
                            label.setToolTipText(parameterAnnotation.description());
                        }
                    }
                } catch (Exception e) {
                    assert ExceptionUtil.printStackTrace(e);
                }
            }
        }
    }

    private static Map<String, Method> findGetters() {
        Map<String, Method> getters = new HashMap<>();
        for (Method method : CommandLineOptions.class.getMethods()) {
            String methodName = method.getName();
            if (methodName.startsWith("get") || methodName.startsWith("is")) {
                getters.put(methodName.replaceFirst("^(is|get)", ""), method);
            }
        }
        return getters;
    }

    private static Map<String, Field> findFields() {
        Map<String, Field> fields = new HashMap<>();
        for (Field field : CommandLineOptions.class.getDeclaredFields()) {
            String fieldName = field.getName();
            if (fieldName.startsWith("_is")) {
                fieldName = '_' + fieldName.substring(3, 4).toLowerCase() + fieldName.substring(4);
            }
            fields.put(fieldName, field);
        }
        return fields;
    }

    @Override
    public void restoreDefaults() {
        for (Map.Entry<String, String> defaultEntry : defaults.entrySet()) {
            String componentKey = defaultEntry.getKey();
            String defaultValue = defaultEntry.getValue();
            JComponent component = components.get(componentKey);
            if (component != null) {
                if (component instanceof JCheckBox) {
                    ((JCheckBox) component).setSelected("true".equals(defaultValue));
                } else if (component instanceof JTextField) {
                    ((JTextField) component).setText(defaultValue);
                }
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
                if (component instanceof JCheckBox) {
                    ((JCheckBox) component).setSelected("true".equals(preferenceValue));
                } else if (component instanceof JTextField) {
                    ((JTextField) component).setText(preferenceValue);
                }
            } else if (component instanceof JCheckBox) {
                ((JCheckBox) component).setSelected("true".equals(defaults.get(preferenceKey)));
            } else if (component instanceof JTextField) {
                ((JTextField) component).setText(defaults.getOrDefault(preferenceKey, ""));
            }
        }
    }

    @Override
    public void savePreferences(Map<String, String> preferences) {
        for (Map.Entry<String, JComponent> componentEntry : components.entrySet()) {
            String componentKey = componentEntry.getKey();
            JComponent component = componentEntry.getValue();
            if (component instanceof JCheckBox) {
                preferences.put(componentKey, Boolean.toString(((JCheckBox) component).isSelected()));
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
