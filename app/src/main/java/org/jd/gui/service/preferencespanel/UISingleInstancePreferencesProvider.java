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

import de.cismet.custom.visualdiff.PlatformService;

/**
 * Single instance is the default mode on Mac OSX, so this panel is not activated.
 */
public class UISingleInstancePreferencesProvider extends JPanel implements PreferencesPanel {

    private static final long serialVersionUID = 1L;

    protected static final String SINGLE_INSTANCE = "UIMainWindowPreferencesProvider.singleInstance";

    protected JCheckBox singleInstanceTabsCheckBox;

    public UISingleInstancePreferencesProvider() {
        super(new GridLayout(0,1));

        singleInstanceTabsCheckBox = new JCheckBox("Single instance (needs restart)");

        add(singleInstanceTabsCheckBox);
    }

    // --- PreferencesPanel --- //
    @Override
    public String getPreferencesGroupTitle() { return "User Interface"; }
    @Override
    public String getPreferencesPanelTitle() { return "Main window"; }
    @Override
    public JComponent getPanel() { return this; }

    @Override
    public void init(Color errorBackgroundColor) {
        // nothing to do
    }

    @Override
    public boolean isActivated() { return !PlatformService.getInstance().isMac(); }

    @Override
    public void loadPreferences(Map<String, String> preferences) {
        singleInstanceTabsCheckBox.setSelected("true".equals(preferences.get(SINGLE_INSTANCE)));
    }

    @Override
    public void savePreferences(Map<String, String> preferences) {
        preferences.put(SINGLE_INSTANCE, Boolean.toString(singleInstanceTabsCheckBox.isSelected()));
    }

    @Override
    public boolean arePreferencesValid() { return true; }

    @Override
    public void addPreferencesChangeListener(PreferencesPanel.PreferencesPanelChangeListener listener) {
        // nothing to do
    }

    @Override
    public void restoreDefaults() {
        singleInstanceTabsCheckBox.setSelected(false);
    }

    @Override
    public boolean useCompactDisplay() {
        return true;
    }
}
