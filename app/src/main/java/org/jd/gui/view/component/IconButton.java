/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.view.component;

import javax.swing.Action;
import javax.swing.JButton;

public class IconButton extends JButton {

    private static final long serialVersionUID = 1L;

    public IconButton(String text, Action action) {
        super(action);
        setText(text);
        setFocusPainted(false);
    }

    public IconButton(Action action) {
        super(action);
        setFocusPainted(false);
    }
}
