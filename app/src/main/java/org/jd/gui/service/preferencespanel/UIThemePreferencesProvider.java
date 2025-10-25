/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.preferencespanel;

import org.jd.gui.spi.PreferencesPanel;

import java.awt.Color;
import java.awt.GridLayout;
import java.util.Map;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;

public class UIThemePreferencesProvider extends JPanel implements PreferencesPanel {

    private static final long serialVersionUID = 1L;

    protected static final String DARK_MODE = "UIMainWindowPreferencesProvider.darkMode";

    protected JCheckBox darkModeTabsCheckBox;

    public UIThemePreferencesProvider() {
        super(new GridLayout(0,1));

        darkModeTabsCheckBox = new JCheckBox("Dark Mode (needs restart)");

        add(darkModeTabsCheckBox);
    }

    // --- PreferencesPanel --- //
    @Override
    public String getPreferencesGroupTitle() { return "User Interface"; }
    @Override
    public String getPreferencesPanelTitle() { return "Theme"; }
    @Override
    public JComponent getPanel() { return this; }

    @Override
    public void init(Color errorBackgroundColor) {}

    @Override
    public boolean isActivated() { return true; }

    @Override
    public void loadPreferences(Map<String, String> preferences) {
        darkModeTabsCheckBox.setSelected("true".equals(preferences.get(DARK_MODE)));
    }

    @Override
    public void savePreferences(Map<String, String> preferences) {
        preferences.put(DARK_MODE, Boolean.toString(darkModeTabsCheckBox.isSelected()));
    }

    @Override
    public boolean arePreferencesValid() { return true; }

    @Override
    public void addPreferencesChangeListener(PreferencesPanel.PreferencesPanelChangeListener listener) {}

    @Override
    public void restoreDefaults() {
        darkModeTabsCheckBox.setSelected(false);
    }
}
