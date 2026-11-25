/*
 * © 2008-2019 Emmanuel Dupuy
 * © 2022-2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.preferencespanel;

import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger.Severity;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;

import com.strobel.reflection.FieldInfo;
import com.strobel.reflection.Type;

import java.awt.Dimension;
import java.util.EnumSet;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTextField;

public class FernflowerDecompilerPreferencesProvider extends AbstractDecompilerPreferencesProvider {

    private static final long serialVersionUID = 1L;

    private static final String TRUE = "1";
    private static final String FALSE = "0";

    private static final String[] LOG_LEVELS = EnumSet.allOf(Severity.class).stream().map(Severity::name).toArray(String[]::new);
    private static final Dimension FIELD_DIMENSION = new Dimension(250, 20);

    public FernflowerDecompilerPreferencesProvider() {
        super(4, TRUE, FALSE);
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
                    register(trigram, component);
                    JLabel label = new JLabel(fieldInfo.getName() + " (" + trigram + ")");
                    label.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));
                    add(label);
                    add(component);
                }
            }
        }
    }

    @Override
    protected String defaultValue(String key, JComponent component) {
        return String.valueOf(IFernflowerPreferences.DEFAULTS.getOrDefault(key, ""));
    }
}
