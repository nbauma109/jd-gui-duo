/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.view.renderer;

import org.jd.gui.api.model.TreeNodeData;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;

public class TreeNodeRenderer implements TreeCellRenderer {
    private final Color textSelectionColor;
    private final Color backgroundSelectionColor;
    private final Color textNonSelectionColor;
    private final Color textDisabledColor;
    private final Color backgroundDisabledColor;

    private final JPanel panel;
    private final JLabel icon = new JLabel();
    private final JLabel label = new JLabel();

    public TreeNodeRenderer() {
        panel = new JPanel(new BorderLayout());
        panel.add(icon, BorderLayout.WEST);
        panel.add(label, BorderLayout.CENTER);
        panel.setOpaque(false);

        textSelectionColor = UIManager.getColor("Tree.selectionForeground");
        backgroundSelectionColor = UIManager.getColor("Tree.selectionBackground");
        textNonSelectionColor = UIManager.getColor("Tree.textForeground");
        textDisabledColor = UIManager.getColor("Tree.disabledText");
        backgroundDisabledColor = UIManager.getColor("Tree.disabled");
        Insets margins = UIManager.getInsets("Tree.rendererMargins");

        icon.setForeground(textNonSelectionColor);
        icon.setOpaque(false);
        icon.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 2));

        label.setOpaque(false);

        if (margins != null) {
            label.setBorder(BorderFactory.createEmptyBorder(margins.top, margins.left, margins.bottom, margins.right));
        } else {
            label.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        }
    }

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        Object data = ((DefaultMutableTreeNode)value).getUserObject();
        if (data instanceof TreeNodeData tnd) {
            icon.setIcon(expanded && tnd.getOpenIcon() != null ? tnd.getOpenIcon() : tnd.getIcon());
            label.setText(tnd.getLabel());
        } else {
            icon.setIcon(null);
            label.setText("" + data);
        }

        if (selected) {
            if (hasFocus) {
                label.setForeground(textSelectionColor);
                label.setBackground(backgroundSelectionColor);
            } else {
                label.setForeground(textDisabledColor);
                label.setBackground(backgroundDisabledColor);
            }
            label.setOpaque(true);
        } else {
            label.setForeground(textNonSelectionColor);
            label.setOpaque(false);
        }

        return panel;
    }
}
