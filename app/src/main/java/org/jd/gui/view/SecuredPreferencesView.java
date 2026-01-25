/*
 * Copyright (c) 2026 GPLv3
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.view;

import org.jd.gui.model.configuration.Configuration;
import org.jd.gui.spi.SecuredPreferencesPanel;

import java.util.Collection;

import javax.swing.JFrame;

public class SecuredPreferencesView extends PreferencesView {

    public SecuredPreferencesView(Configuration configuration, JFrame mainFrame, Collection<SecuredPreferencesPanel> panels) {
        super(configuration, mainFrame, panels, "Secured Preferences");
    }
}
