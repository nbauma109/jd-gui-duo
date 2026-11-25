/*
 * © 2008-2019 Emmanuel Dupuy
 * © 2022-2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.preferencespanel;

import java.awt.GridLayout;
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

import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.spi.PreferencesPanel;

import jadx.api.JadxArgs;

public class JadxDecompilerPreferencesProvider extends JPanel implements PreferencesPanel {

    private static final long serialVersionUID = 1L;

    private static final Class<?>[] BOOLEAN_PARAM = { boolean.class };
    private static final Class<?>[] INT_PARAM = { int.class };

    private Map<String, JComponent> components = new HashMap<>();
    private Map<String, String> defaults = new HashMap<>();

    public JadxDecompilerPreferencesProvider() {
        super(new GridLayout(0, 4));
        JadxArgs defaultOptions = new JadxArgs();
        Method[] methods = JadxArgs.class.getMethods();
        Arrays.sort(methods, Comparator.comparing(Method::getName));
        for (Method method : methods) {
            if (method.getName().startsWith("set")) {
                String optionKey = method.getName().substring(3);
                try {
                    JComponent component = null;
                    if (Arrays.equals(method.getParameterTypes(), BOOLEAN_PARAM)) {
                        Method getter = JadxArgs.class.getMethod("is" + optionKey);
                        Boolean defaultValue = (Boolean) getter.invoke(defaultOptions);
                        component = new JCheckBox();
                        defaults.put(optionKey, Boolean.toString(defaultValue));
                    } else if (Arrays.equals(method.getParameterTypes(), INT_PARAM)) {
                        Method getter = JadxArgs.class.getMethod("get" + optionKey);
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
                    }
                } catch (Exception e) {
                    assert ExceptionUtil.printStackTrace(e);
                }
            }
        }
    }

    @Override
    public void restoreDefaults() {
        components.forEach(this::restoreDefault);
    }

    private void restoreDefault(String key, JComponent component) {
        show(component, defaults.getOrDefault(key, ""));
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
        if (component instanceof JCheckBox checkBox) {
            checkBox.setSelected("true".equals(value));
        } else if (component instanceof JTextField textField) {
            textField.setText(value);
        }
    }

    @Override
    public void savePreferences(Map<String, String> preferences) {
        for (Map.Entry<String, JComponent> componentEntry : components.entrySet()) {
            String componentKey = componentEntry.getKey();
            JComponent component = componentEntry.getValue();
            if (component instanceof JCheckBox checkBox) {
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
