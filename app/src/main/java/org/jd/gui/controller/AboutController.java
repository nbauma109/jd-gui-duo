/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.controller;

import org.jd.gui.view.AboutView;

import javax.swing.JFrame;

public class AboutController {
    private final AboutView aboutView;

    public AboutController(JFrame mainFrame, boolean darkMode) {
        // Create UI
        aboutView = new AboutView(mainFrame, darkMode);
    }

    public void show() {
        // Show
        aboutView.show();
    }
}
