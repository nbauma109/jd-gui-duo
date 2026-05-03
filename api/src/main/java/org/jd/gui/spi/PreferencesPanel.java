/*
 * © 2008-2019 Emmanuel Dupuy
 * © 2021-2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.spi;

import java.awt.Color;
import java.util.Map;

import javax.swing.JComponent;

public interface PreferencesPanel {

    boolean useCompactDisplay();

    String getPreferencesGroupTitle();

    String getPreferencesPanelTitle();

    default JComponent getPanel() { return (JComponent) this; }

    default void init(Color errorBackgroundColor) {}

    default boolean isActivated() { return true; }

    void loadPreferences(Map<String, String> preferences);

    void savePreferences(Map<String, String> preferences);

    boolean arePreferencesValid();

    void addPreferencesChangeListener(PreferencesPanelChangeListener listener);

    void restoreDefaults();

    interface PreferencesPanelChangeListener {
        void preferencesPanelChanged(PreferencesPanel source);
    }
}
