/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */
package org.jd.gui.view.component;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * Content of a translucent undecorated window: a white card with rounded corners and a thin outline that
 * casts a soft drop shadow, like Eclipse's pop-ups (Inspect, hovers).
 */
public class ShadowPopupPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final int ARC = 12;
    private static final int LEFT = 3;
    private static final int TOP = 3;
    private static final int RIGHT = 9;
    private static final int BOTTOM = 9;
    private static final int SHADOW_STEPS = 6;

    /** The rounded card: clips its children to the rounded outline. */
    private final JPanel card = new JPanel(new BorderLayout()) {
        private static final long serialVersionUID = 1L;

        @Override
        public void paint(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                RoundRectangle2D shape = new RoundRectangle2D.Float(0, 0, getWidth() - 1f, getHeight() - 1f, ARC, ARC);
                g2.setColor(Color.WHITE);
                g2.fill(shape);
                g2.clip(shape);
                super.paint(g2);
                g2.setClip(null);
                g2.setColor(new Color(0xB4B4B4));
                g2.draw(shape);
            } finally {
                g2.dispose();
            }
        }
    };

    public ShadowPopupPanel(JComponent content) {
        super(new BorderLayout());
        setOpaque(false);
        card.setOpaque(false);
        card.add(content, BorderLayout.CENTER);
        setBorder(BorderFactory.createEmptyBorder(TOP, LEFT, BOTTOM, RIGHT));
        add(card, BorderLayout.CENTER);
    }

    /** The card holding the content, e.g. to bind keys. */
    public JComponent getCard() {
        return card;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int width = getWidth() - LEFT - RIGHT;
            int height = getHeight() - TOP - BOTTOM;
            for (int i = SHADOW_STEPS; i >= 1; i--) {
                g2.setColor(new Color(0, 0, 0, 9));
                g2.fill(new RoundRectangle2D.Float(LEFT - i + 2, TOP - i + 4, width + 2f * i, height + 2f * i, ARC + 2f * i, ARC + 2f * i));
            }
        } finally {
            g2.dispose();
        }
    }
}
