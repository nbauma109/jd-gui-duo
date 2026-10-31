/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */
package org.jd.gui.view.component;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.Icon;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.tree.TreeModel;

/**
 * Tree drawn like the SWT trees of Eclipse: no dotted connector lines and chevrons ({@code >} and {@code v})
 * instead of the look and feel's plus/minus boxes.
 */
public class LinelessTree extends JTree {
    private static final long serialVersionUID = 1L;

    public LinelessTree(TreeModel model) {
        super(model);
    }

    private static final String PAINT_LINES = "Tree.paintLines";
    private static final String COLLAPSED_ICON = "Tree.collapsedIcon";
    private static final String EXPANDED_ICON = "Tree.expandedIcon";

    @Override
    public void updateUI() {
        // the look and feel reads these defaults when it installs its UI
        Object paintLines = UIManager.get(PAINT_LINES);
        Object collapsedIcon = UIManager.get(COLLAPSED_ICON);
        Object expandedIcon = UIManager.get(EXPANDED_ICON);
        UIManager.put(PAINT_LINES, Boolean.FALSE);
        UIManager.put(COLLAPSED_ICON, new ChevronIcon(false));
        UIManager.put(EXPANDED_ICON, new ChevronIcon(true));
        try {
            super.updateUI();
        } finally {
            UIManager.put(PAINT_LINES, paintLines);
            UIManager.put(COLLAPSED_ICON, collapsedIcon);
            UIManager.put(EXPANDED_ICON, expandedIcon);
        }
    }

    private static final class ChevronIcon implements Icon {
        private final boolean expanded;

        ChevronIcon(boolean expanded) {
            this.expanded = expanded;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0x707070));
                g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                if (expanded) {
                    g2.drawPolyline(new int[] { x + 1, x + 5, x + 9 }, new int[] { y + 3, y + 7, y + 3 }, 3);
                } else {
                    g2.drawPolyline(new int[] { x + 3, x + 7, x + 3 }, new int[] { y + 1, y + 5, y + 9 }, 3);
                }
            } finally {
                g2.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            return 11;
        }

        @Override
        public int getIconHeight() {
            return 11;
        }
    }
}
