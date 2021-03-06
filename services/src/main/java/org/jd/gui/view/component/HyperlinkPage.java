/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.view.component;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.Token;

import java.awt.Cursor;
import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import jd.core.links.HyperlinkData;

public abstract class HyperlinkPage extends TextPage {

    private static final long serialVersionUID = 1L;
    protected static final Cursor DEFAULT_CURSOR = Cursor.getDefaultCursor();
    protected static final Cursor HAND_CURSOR = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);

    protected final transient NavigableMap<Integer, HyperlinkData> hyperlinks = new TreeMap<>();

    protected HyperlinkPage() {
        MouseAdapter listener = new MouseAdapter() {
            private int lastX = -1;
            private int lastY = -1;
            private int lastModifiers = -1;

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 1 && (e.getModifiersEx() & (InputEvent.ALT_DOWN_MASK|InputEvent.META_DOWN_MASK|InputEvent.SHIFT_DOWN_MASK)) == 0) {
                    int offset = textArea.viewToModel2D(new Point(e.getX(), e.getY()));
                    if (offset != -1) {
                        Map.Entry<Integer, HyperlinkData> entry = hyperlinks.floorEntry(offset);
                        if (entry != null) {
                            HyperlinkData entryData = entry.getValue();
                            if (entryData != null && offset < entryData.getEndPosition() && offset >= entryData.getStartPosition() && isHyperlinkEnabled(entryData)) {
                                openHyperlink(e.getXOnScreen(), e.getYOnScreen(), entryData);
                            }
                        }
                    }
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                if (e.getX() != lastX || e.getY() != lastY || lastModifiers != e.getModifiersEx()) {
                    lastX = e.getX();
                    lastY = e.getY();
                    lastModifiers = e.getModifiersEx();

                    if ((e.getModifiersEx() & (InputEvent.ALT_DOWN_MASK|InputEvent.META_DOWN_MASK|InputEvent.SHIFT_DOWN_MASK)) == 0) {
                        int offset = textArea.viewToModel2D(new Point(e.getX(), e.getY()));
                        if (offset != -1) {
                            Map.Entry<Integer, HyperlinkData> entry = hyperlinks.floorEntry(offset);
                            if (entry != null) {
                                HyperlinkData entryData = entry.getValue();
                                if (entryData != null && offset < entryData.getEndPosition() && offset >= entryData.getStartPosition() && isHyperlinkEnabled(entryData)) {
                                    if (textArea.getCursor() != HAND_CURSOR) {
                                        textArea.setCursor(HAND_CURSOR);
                                    }
                                    return;
                                }
                            }
                        }
                    }

                    if (textArea.getCursor() != DEFAULT_CURSOR) {
                        textArea.setCursor(DEFAULT_CURSOR);
                    }
                }
            }
        };

        textArea.addMouseListener(listener);
        textArea.addMouseMotionListener(listener);
    }

    @Override
    protected RSyntaxTextArea newSyntaxTextArea() { return new HyperlinkSyntaxTextArea(); }

    public void addHyperlink(HyperlinkData hyperlinkData) {
        hyperlinks.put(hyperlinkData.getStartPosition(), hyperlinkData);
    }

    public void clearHyperlinks() {
        hyperlinks.clear();
    }

    protected abstract boolean isHyperlinkEnabled(HyperlinkData hyperlinkData);

    protected abstract void openHyperlink(int x, int y, HyperlinkData hyperlinkData);

    public class HyperlinkSyntaxTextArea extends RSyntaxTextArea {

        private static final long serialVersionUID = 1L;

        /**
         * @see HyperlinkPage.HyperlinkSyntaxTextArea#getUnderlineForToken(org.fife.ui.rsyntaxtextarea.Token)
         */
        @Override
        public boolean getUnderlineForToken(Token t) {
            Map.Entry<Integer, HyperlinkData> entry = hyperlinks.floorEntry(t.getOffset());
            if (entry != null) {
                HyperlinkData entryData = entry.getValue();
                if (entryData != null && t.getOffset() < entryData.getEndPosition() && t.getOffset() >= entryData.getStartPosition() && isHyperlinkEnabled(entryData)) {
                    return true;
                }
            }
            return super.getUnderlineForToken(t);
        }
    }
}
