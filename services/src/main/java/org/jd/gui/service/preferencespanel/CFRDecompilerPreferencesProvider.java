/*
 * © 2008-2019 Emmanuel Dupuy
 * © 2022-2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.preferencespanel;

import org.benf.cfr.reader.util.Troolean;
import org.benf.cfr.reader.util.getopt.OptionDecoderParam;
import org.benf.cfr.reader.util.getopt.OptionsImpl;
import org.benf.cfr.reader.util.getopt.PermittedOptionProvider.ArgumentParam;

import com.strobel.reflection.BindingFlags;
import com.strobel.reflection.FieldInfo;
import com.strobel.reflection.MethodInfo;
import com.strobel.reflection.Type;
import com.strobel.reflection.TypeList;

import java.util.HashMap;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTextField;

public class CFRDecompilerPreferencesProvider extends AbstractDecompilerPreferencesProvider {

    private static final long serialVersionUID = 1L;

    private static final String AUTO = "auto";

    private final Map<String, String> defaults = new HashMap<>();

    public CFRDecompilerPreferencesProvider() {
        super(6, "true", "false");
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
                    component = new JComboBox<>(new String[] { AUTO, "true", "false" });
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
                register(argInstance.getName(), component);
                if (defaultValue != null && defaultValue.matches("\\w+")) {
                    defaults.put(argInstance.getName(), defaultValue);
                } else if (component instanceof JComboBox) {
                    defaults.put(argInstance.getName(), AUTO);
                }
            }
        }
    }

    @Override
    protected String defaultValue(String key, JComponent component) {
        // an option that can be "auto" is not saved when it is: none saved means "auto"
        if (component instanceof JComboBox<?> comboBox && AUTO.equals(comboBox.getItemAt(0))) {
            return AUTO;
        }
        return defaults.getOrDefault(key, "");
    }

    @Override
    protected boolean isUnset(JComponent component, String value) {
        return component instanceof JComboBox && AUTO.equals(value) || super.isUnset(component, value);
    }
}
