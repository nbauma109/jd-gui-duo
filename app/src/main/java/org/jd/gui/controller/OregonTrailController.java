/*
 * © 2021-2025 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.controller;

import org.jd.gui.view.OregonTrailView;

import javax.swing.JFrame;

public class OregonTrailController {
    private final OregonTrailView oregonTrailView;

    public OregonTrailController(JFrame mainFrame) {
        oregonTrailView = new OregonTrailView(mainFrame);
    }

    public void show() {
        oregonTrailView.show();
    }
}
