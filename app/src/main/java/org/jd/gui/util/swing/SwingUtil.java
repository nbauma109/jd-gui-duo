/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.util.swing;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;

/**
 * See:
 * https://www.ailis.de/~k/archives/67-Workaround-for-borderless-Java-Swing-menus-on-Linux.html
 */
public class SwingUtil {

	private SwingUtil() {
	}

	public static void invokeLater(Runnable runnable) {
		if (SwingUtilities.isEventDispatchThread()) {
			runnable.run();
		} else {
			SwingUtilities.invokeLater(runnable);
		}
	}

	public static Action newAction(String name, boolean enable, ActionListener listener) {
		Action action = new AbstractAction(name) {

			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(ActionEvent actionEvent) {
				listener.actionPerformed(actionEvent);
			}
		};
		action.setEnabled(enable);
		return action;
	}

	public static Action newAction(String name, ImageIcon icon, boolean enable, ActionListener listener) {
		Action action = newAction(name, enable, listener);
		action.putValue(Action.SMALL_ICON, icon);
		return action;
	}

	public static Action newAction(ImageIcon icon, boolean enable, ActionListener listener) {
		Action action = newAction(null, icon, enable, listener);
		action.putValue(Action.SMALL_ICON, icon);
		return action;
	}

	public static Action newAction(String name, ImageIcon icon, boolean enable, String shortDescription, ActionListener listener) {
		Action action = newAction(name, icon, enable, listener);
		action.putValue(Action.SHORT_DESCRIPTION, shortDescription);
		return action;
	}

	public static Action newAction(String name, boolean enable, String shortDescription, ActionListener listener) {
		Action action = newAction(name, enable, listener);
		action.putValue(Action.SHORT_DESCRIPTION, shortDescription);
		return action;
	}
}
