/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.view.component;

import java.awt.Toolkit;
import java.awt.event.KeyEvent;

import javax.swing.InputMap;
import javax.swing.JTree;
import javax.swing.KeyStroke;

public class Tree extends JTree {

    private static final long serialVersionUID = 1L;

    public Tree() {
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        KeyStroke ctrlA = KeyStroke.getKeyStroke(KeyEvent.VK_A, toolkit.getMenuShortcutKeyMask());
        KeyStroke ctrlC = KeyStroke.getKeyStroke(KeyEvent.VK_C, toolkit.getMenuShortcutKeyMask());
        KeyStroke ctrlV = KeyStroke.getKeyStroke(KeyEvent.VK_V, toolkit.getMenuShortcutKeyMask());

        InputMap inputMap = getInputMap();
        inputMap.put(ctrlA, "none");
        inputMap.put(ctrlC, "none");
        inputMap.put(ctrlV, "none");

        setRootVisible(false);
    }

    public void fireVisibleDataPropertyChange() {
        if (getAccessibleContext() != null) {
            getAccessibleContext().firePropertyChange("AccessibleVisibleData", false, true);
        }
    }
}
