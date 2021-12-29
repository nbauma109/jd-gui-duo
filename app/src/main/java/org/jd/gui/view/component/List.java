/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.view.component;

import org.jd.gui.api.model.TreeNodeData;

import java.awt.Color;
import java.awt.Component;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.util.Optional;

import javax.swing.BorderFactory;
import javax.swing.InputMap;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.UIManager;
import javax.swing.tree.DefaultMutableTreeNode;

@SuppressWarnings("rawtypes")
public class List extends JList {

	private static final long serialVersionUID = 1L;

	@SuppressWarnings("unchecked")
	public List() {
		Toolkit toolkit = Toolkit.getDefaultToolkit();
		KeyStroke ctrlA = KeyStroke.getKeyStroke(KeyEvent.VK_A, toolkit.getMenuShortcutKeyMaskEx());
		KeyStroke ctrlC = KeyStroke.getKeyStroke(KeyEvent.VK_C, toolkit.getMenuShortcutKeyMaskEx());
		KeyStroke ctrlV = KeyStroke.getKeyStroke(KeyEvent.VK_V, toolkit.getMenuShortcutKeyMaskEx());

		InputMap inputMap = getInputMap();
		inputMap.put(ctrlA, "none");
		inputMap.put(ctrlC, "none");
		inputMap.put(ctrlV, "none");

		setCellRenderer(new Renderer());
	}

	protected class Renderer implements ListCellRenderer {
		private final Color textSelectionColor;
		private final Color backgroundSelectionColor;
		private final Color textNonSelectionColor;
		private final Color backgroundNonSelectionColor;

		private final JLabel label;

		public Renderer() {
			label = new JLabel();
			label.setOpaque(true);

			textSelectionColor = Optional.ofNullable(UIManager.getColor("List.dropCellForeground")).orElse(getSelectionForeground());
			backgroundSelectionColor = Optional.ofNullable(UIManager.getColor("List.dropCellBackground")).orElse(getSelectionBackground());
			textNonSelectionColor = UIManager.getColor("List.foreground");
			backgroundNonSelectionColor = UIManager.getColor("List.background");
			Insets margins = UIManager.getInsets("List.contentMargins");

			if (margins != null) {
				label.setBorder(BorderFactory.createEmptyBorder(margins.top, margins.left, margins.bottom, margins.right));
			} else {
				label.setBorder(BorderFactory.createEmptyBorder(0, 2, 1, 2));
			}
		}

		@Override
		public Component getListCellRendererComponent(JList list, Object value, int index, boolean selected, boolean hasFocus) {
			Object data = ((DefaultMutableTreeNode) value).getUserObject();
			if (data instanceof TreeNodeData tnd) {
				label.setIcon(tnd.getIcon());
				label.setText(tnd.getLabel());
			} else {
				label.setIcon(null);
				label.setText("" + data);
			}

			if (selected) {
				label.setForeground(textSelectionColor);
				label.setBackground(backgroundSelectionColor);
			} else {
				label.setForeground(textNonSelectionColor);
				label.setBackground(backgroundNonSelectionColor);
			}

			return label;
		}
	}
}
