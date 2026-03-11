/*
 * Copyright (c) 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.controller;

import org.jd.gui.model.configuration.Configuration;
import org.jd.gui.spi.EclipsePreferencesPanel;
import org.jd.gui.view.EclipsePreferencesView;

import java.util.Collection;

import javax.swing.JFrame;

public class EclipsePreferencesController {
    private final EclipsePreferencesView preferencesView;

    public EclipsePreferencesController(
            Configuration configuration,
            JFrame mainFrame,
            Collection<EclipsePreferencesPanel> panels) {
        // Create UI
        preferencesView = new EclipsePreferencesView(configuration, mainFrame, panels);
    }

    public void show(Runnable okCallback) {
        // Show
        preferencesView.show(okCallback);
    }
}
