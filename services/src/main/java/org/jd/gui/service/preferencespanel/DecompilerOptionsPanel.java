/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.preferencespanel;

import org.jd.gui.spi.PreferencesPanel;

/** What the panels of the options of the decompilers have in common. */
interface DecompilerOptionsPanel extends PreferencesPanel {

    @Override
    default String getPreferencesGroupTitle() {
        return "Decompiler";
    }

    @Override
    default String getPreferencesPanelTitle() {
        return "Class file";
    }

    @Override
    default boolean arePreferencesValid() {
        return true;
    }

    @Override
    default void addPreferencesChangeListener(PreferencesPanel.PreferencesPanelChangeListener listener) {
        // nothing to do
    }

    @Override
    default boolean useCompactDisplay() {
        return false;
    }
}
