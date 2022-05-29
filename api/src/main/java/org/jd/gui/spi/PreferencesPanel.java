/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.spi;

import java.awt.Color;
import java.util.Map;

import javax.swing.JComponent;

public interface PreferencesPanel {
    String getPreferencesGroupTitle();

    String getPreferencesPanelTitle();

    JComponent getPanel();

    void init(Color errorBackgroundColor);

    boolean isActivated();

    void loadPreferences(Map<String, String> preferences);

    void savePreferences(Map<String, String> preferences);

    boolean arePreferencesValid();

    void addPreferencesChangeListener(PreferencesPanelChangeListener listener);

    void restoreDefaults();

    interface PreferencesPanelChangeListener {
        void preferencesPanelChanged(PreferencesPanel source);
    }
}
