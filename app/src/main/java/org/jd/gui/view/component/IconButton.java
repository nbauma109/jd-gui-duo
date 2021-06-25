/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.view.component;

import java.awt.Insets;

import javax.swing.Action;
import javax.swing.JButton;

public class IconButton extends JButton {

    private static final long serialVersionUID = 1L;
    protected static final Insets INSETS0 = new Insets(0, 0, 0, 0);

    public IconButton(String text, Action action) {
        setFocusPainted(false);
        setBorderPainted(false);
        setMargin(INSETS0);
        setAction(action);
        setText(text);
    }

    public IconButton(Action action) {
        this(null, action);
    }
}
