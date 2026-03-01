/*
 * Copyright (c) 2026 Nicolas Baumann (@nbauma109).
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.view;

import static org.jd.gui.util.ImageUtil.newImageIcon;

import java.awt.Container;
import java.awt.event.ActionListener;

import javax.swing.JToggleButton;

public final class SearchUIOptions {

    private final JToggleButton matchCaseButton;
    private final JToggleButton wholeWordButton;
    private final JToggleButton regexButton;
    private final JToggleButton markAllButton;
    private final JToggleButton wrapButton;
    private final ActionListener findWithOptionsActionListener;

    public SearchUIOptions(ActionListener findWithOptionsActionListener) {
        this.findWithOptionsActionListener = findWithOptionsActionListener;
        this.matchCaseButton = createToggleIconButton("matchCase.png", "Match Case");
        this.wholeWordButton = createToggleIconButton("wholeWord.png", "Whole Word");
        this.regexButton = createToggleIconButton("regexp.png", "Regex");
        this.markAllButton = createToggleIconButton("highlight.png", "Mark All");
        this.wrapButton = createToggleIconButton("wrapAround.png", "Wrap Around");
    }

    public SearchUIOptions() {
        this(null);
    }

    private JToggleButton createToggleIconButton(String iconPath, String tooltip) {
        JToggleButton button = new JToggleButton();
        button.setFocusPainted(false);
        button.setToolTipText(tooltip);
        button.setIcon(newImageIcon("/org/netbeans/modules/editor/search/resources/" + iconPath));
        if (findWithOptionsActionListener != null) {
            button.addActionListener(findWithOptionsActionListener);
        }
        return button;
    }

    public void attachTo(Container container) {
        container.add(matchCaseButton);
        container.add(wholeWordButton);
        container.add(regexButton);
        container.add(markAllButton);
        container.add(wrapButton);
    }

    public boolean isMatchCaseButtonSelected() {
        return matchCaseButton.isSelected();
    }

    public boolean isWholeWordButtonSelected() {
        return wholeWordButton.isSelected();
    }

    public boolean isRegexButtonSelected() {
        return regexButton.isSelected();
    }

    public boolean isMarkAllButtonSelected() {
        return markAllButton.isSelected();
    }

    public boolean isWrapButtonSelected() {
        return wrapButton.isSelected();
    }
}
