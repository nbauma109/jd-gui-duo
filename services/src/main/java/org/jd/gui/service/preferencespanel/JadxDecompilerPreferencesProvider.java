/*
 * © 2008-2019 Emmanuel Dupuy
 * © 2022-2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.preferencespanel;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTextField;

import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;

import jadx.api.JadxArgs;

public class JadxDecompilerPreferencesProvider extends AbstractDecompilerPreferencesProvider {

    private static final long serialVersionUID = 1L;

    private static final Class<?>[] BOOLEAN_PARAM = { boolean.class };
    private static final Class<?>[] INT_PARAM = { int.class };

    private Map<String, String> defaults = new HashMap<>();

    public JadxDecompilerPreferencesProvider() {
        super(4, "true", "false");
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
                        register(optionKey, component);
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
    protected String defaultValue(String key, JComponent component) {
        return defaults.getOrDefault(key, "");
    }
}
