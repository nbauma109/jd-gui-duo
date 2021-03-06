/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.view.renderer;

import org.jd.gui.view.bean.OpenTypeListCellBean;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;

import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.UIManager;

public class OpenTypeListCellRenderer implements ListCellRenderer<OpenTypeListCellBean> {
    private final Color textSelectionColor;
    private final Color textNonSelectionColor;
    private final Color infoSelectionColor;
    private final Color infoNonSelectionColor;
    private final Color backgroundSelectionColor;
    private final Color backgroundNonSelectionColor;

    private final JPanel panel;
    private final JLabel label;
    private final JLabel info;

    public OpenTypeListCellRenderer() {
        textSelectionColor = UIManager.getColor("List.selectionForeground");
        textNonSelectionColor = UIManager.getColor("List.foreground");
        backgroundSelectionColor = UIManager.getColor("List.selectionBackground");
        backgroundNonSelectionColor = UIManager.getColor("List.background");

        infoSelectionColor = infoColor(textSelectionColor);
        infoNonSelectionColor = infoColor(textNonSelectionColor);

        panel = new JPanel(new BorderLayout());
        label = new JLabel();
        panel.add(label, BorderLayout.WEST);
        info = new JLabel();
        panel.add(info, BorderLayout.CENTER);
    }

    protected static Color infoColor(Color c) {
        if (c.getRed() + c.getGreen() + c.getBlue() > 3*127) {
            return new Color(
                    (int)((c.getRed()-127)  *0.7 + 127),
                    (int)((c.getGreen()-127)*0.7 + 127),
                    (int)((c.getBlue()-127) *0.7 + 127),
                    c.getAlpha());
        }
        return new Color(
                (int)(127 - (127-c.getRed())  *0.7),
                (int)(127 - (127-c.getGreen())*0.7),
                (int)(127 - (127-c.getBlue()) *0.7),
                c.getAlpha());
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends OpenTypeListCellBean> list, OpenTypeListCellBean value, int index, boolean selected, boolean hasFocus) {
        if (value != null) {
            // Display first level item
            label.setText(value.getLabel());
            label.setIcon(value.getIcon());

            info.setText(value.getPackag() != null ? " - "+value.getPackag() : "");

            if (selected) {
                label.setForeground(textSelectionColor);
                info.setForeground(infoSelectionColor);
                panel.setBackground(backgroundSelectionColor);
            } else {
                label.setForeground(textNonSelectionColor);
                info.setForeground(infoNonSelectionColor);
                panel.setBackground(backgroundNonSelectionColor);
            }
        } else {
            label.setText(" ...");
            label.setIcon(null);
            info.setText("");
            label.setForeground(textNonSelectionColor);
            panel.setBackground(backgroundNonSelectionColor);
        }

        return panel;
    }
}
