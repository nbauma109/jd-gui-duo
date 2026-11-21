/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui;

import org.jd.gui.controller.MainController;

/** Gives the tests that drive the real application access to its controller (kept protected in {@link App}). */
public final class ControllerAccess {

    private ControllerAccess() {
    }

    public static MainController controller() {
        return App.controller;
    }
}
