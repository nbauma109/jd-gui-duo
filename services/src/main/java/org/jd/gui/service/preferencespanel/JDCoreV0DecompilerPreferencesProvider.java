/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.preferencespanel;

import java.util.Map;

import javax.swing.JCheckBox;

import static jd.core.preferences.Preferences.DISPLAY_DEFAULT_CONSTRUCTOR;
import static jd.core.preferences.Preferences.OMIT_THIS_PREFIX;

public class JDCoreV0DecompilerPreferencesProvider extends AbstractJDCoreDecompilerPreferencesProvider {

    private static final long serialVersionUID = 1L;

    protected JCheckBox omitThisPrefixCheckBox;
    protected JCheckBox displayDefaultConstructorCheckBox;

    public JDCoreV0DecompilerPreferencesProvider() {

        omitThisPrefixCheckBox = new JCheckBox("Omit the prefix 'this' if possible");
        displayDefaultConstructorCheckBox = new JCheckBox("Display default constructor");

        add(omitThisPrefixCheckBox);
        add(displayDefaultConstructorCheckBox);
    }

    @Override
    public void restoreDefaults() {
        super.restoreDefaults();
        omitThisPrefixCheckBox.setSelected(false);
        displayDefaultConstructorCheckBox.setSelected(false);
    }

    @Override
    public void loadPreferences(Map<String, String> preferences) {
        super.loadPreferences(preferences);
        omitThisPrefixCheckBox.setSelected("true".equals(preferences.get(OMIT_THIS_PREFIX)));
        displayDefaultConstructorCheckBox.setSelected("true".equals(preferences.get(DISPLAY_DEFAULT_CONSTRUCTOR)));
    }

    @Override
    public void savePreferences(Map<String, String> preferences) {
        super.savePreferences(preferences);
        preferences.put(OMIT_THIS_PREFIX, Boolean.toString(omitThisPrefixCheckBox.isSelected()));
        preferences.put(DISPLAY_DEFAULT_CONSTRUCTOR, Boolean.toString(displayDefaultConstructorCheckBox.isSelected()));
    }
}
