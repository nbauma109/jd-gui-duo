/*
 * © 2008-2019 Emmanuel Dupuy
 * © 2022-2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.preferencespanel;

import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;

import com.beust.jcommander.Parameter;
import com.strobel.decompiler.CommandLineOptions;

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
import javax.swing.JTextField;

public class ProcyonDecompilerPreferencesProvider extends AbstractDecompilerPreferencesProvider {

    private static final long serialVersionUID = 1L;

    private static final Class<?>[] BOOLEAN_PARAM = { boolean.class };
    private static final Class<?>[] INT_PARAM = { int.class };

    private Map<String, String> defaults = new HashMap<>();

    public ProcyonDecompilerPreferencesProvider() {
        super(2, "true", "false");
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
                        register(optionKey, component);
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
    protected String defaultValue(String key, JComponent component) {
        return defaults.getOrDefault(key, "");
    }
}
