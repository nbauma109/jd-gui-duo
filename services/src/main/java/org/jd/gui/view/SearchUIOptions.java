/*
 * Copyright (c) 2026 Nicolas Baumann (@nbauma109).
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.view;

import static org.jd.gui.util.ImageUtil.newImageIcon;

import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;

public final class SearchUIOptions {
    private static final String NETBEANS_BUNDLE = "org.netbeans.modules.editor.search.Bundle";
    private static final String DEFAULT_INCREMENTAL_SEARCH_TOOLTIP = "Search Text (ENTER - Find Next, Shift+ENTER - Find Previous)";
    private static final String DEFAULT_SELECT_ALL_TOOLTIP = "Place a selection around all occurrences in the editor";
    private static final boolean MAC_OS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");

    private final JToggleButton matchCaseButton;
    private final JToggleButton wholeWordButton;
    private final JToggleButton regexButton;
    private final JToggleButton markAllButton;
    private final JToggleButton wrapButton;

    public SearchUIOptions(ActionListener findWithOptionsActionListener) {
        this.matchCaseButton = createToggleIconButton("matchCase.png", getOptionTooltip("TT_MatchCase", "Match case", "C"));
        this.wholeWordButton = createToggleIconButton("wholeWord.png", getOptionTooltip("TT_WholeWords", "Whole word", "O"));
        this.regexButton = createToggleIconButton("regexp.png", getOptionTooltip("TT_Regexp", "Regular expression", "G"));
        this.markAllButton = createToggleIconButton("highlight.png", getOptionTooltip("TT_Highlight", "Highlight matches", "H"));
        this.wrapButton = createToggleIconButton("wrapAround.png", getOptionTooltip("TT_WrapAround", "Wrap Around", "U"));
        addOptionsChangeListener(findWithOptionsActionListener);
    }

    public SearchUIOptions() {
        this(null);
    }

    private JToggleButton createToggleIconButton(String iconPath, String tooltip) {
        JToggleButton button = new JToggleButton();
        button.setFocusPainted(false);
        button.setToolTipText(tooltip);
        button.setIcon(newImageIcon("/org/netbeans/modules/editor/search/resources/" + iconPath));
        return button;
    }

    public void attachTo(Container container) {
        container.add(matchCaseButton);
        container.add(wholeWordButton);
        container.add(regexButton);
        container.add(markAllButton);
        container.add(wrapButton);
    }

    public void addOptionsChangeListener(ActionListener listener) {
        if (listener == null) {
            return;
        }

        matchCaseButton.addActionListener(listener);
        wholeWordButton.addActionListener(listener);
        regexButton.addActionListener(listener);
        markAllButton.addActionListener(listener);
        wrapButton.addActionListener(listener);
    }

    public void bindOptionKeyStrokes(JTextComponent searchField) {
        bindOptionKeyStroke(searchField, "matchcasekey", KeyEvent.VK_C, matchCaseButton);
        bindOptionKeyStroke(searchField, "wholewordkey", KeyEvent.VK_O, wholeWordButton);
        bindOptionKeyStroke(searchField, "regexpkey", KeyEvent.VK_G, regexButton);
        bindOptionKeyStroke(searchField, "highlightkey", KeyEvent.VK_H, markAllButton);
        bindOptionKeyStroke(searchField, "wraparoundkey", KeyEvent.VK_U, wrapButton);
    }

    private void bindOptionKeyStroke(JTextComponent searchField, String actionKey, int keyCode, AbstractButton button) {
        InputMap inputMap = searchField.getInputMap();
        ActionMap actionMap = searchField.getActionMap();

        inputMap.put(KeyStroke.getKeyStroke(keyCode, getOptionModifierMask()), actionKey);
        actionMap.put(actionKey, new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                button.doClick(0);
            }
        });
    }

    public static void configureIncrementalSearchField(JTextComponent searchField) {
        searchField.setToolTipText(getIncrementalSearchTooltip());
    }

    public static void configureFindButton(AbstractButton button) {
        button.setToolTipText(getIncrementalSearchTooltip());
    }

    public static void configureSelectAllButton(AbstractButton button) {
        button.setToolTipText(getSelectAllTooltip());
    }

    public static void installCriteriaListener(JTextComponent searchField, Runnable callback) {
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                callback.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                callback.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                callback.run();
            }
        });
    }

    public static String getFindNextLabel() {
        return getMessage("CTL_FindNext", "Next");
    }

    public static String getFindPreviousLabel() {
        return getMessage("CTL_FindPrevious", "Previous");
    }

    public static String getIncrementalSearchTooltip() {
        return getMessage("TOOLTIP_IncrementalSearchText", DEFAULT_INCREMENTAL_SEARCH_TOOLTIP);
    }

    public static String getSelectAllTooltip() {
        return getMessage("TOOLTIP_SelectAllText", DEFAULT_SELECT_ALL_TOOLTIP);
    }

    private static String getMessage(String key, String fallback) {
        try {
            return ResourceBundle.getBundle(NETBEANS_BUNDLE).getString(key);
        } catch (MissingResourceException e) {
            return fallback;
        }
    }

    private static String getOptionTooltip(String key, String label, String keySuffix) {
        String fallback = label + " (" + getOptionModifierText() + " + " + keySuffix + ")";
        return adaptOptionTooltip(getMessage(key, fallback), keySuffix);
    }

    private static String adaptOptionTooltip(String tooltip, String keySuffix) {
        if (!MAC_OS) {
            return tooltip;
        }

        int altIndex = tooltip.indexOf("(Alt + ");
        if (altIndex == -1) {
            return tooltip;
        }

        return tooltip.substring(0, altIndex) + "(" + getOptionModifierText() + " + " + keySuffix + ")";
    }

    private static int getOptionModifierMask() {
        return MAC_OS ? InputEvent.CTRL_DOWN_MASK | InputEvent.META_DOWN_MASK : InputEvent.ALT_DOWN_MASK;
    }

    private static String getOptionModifierText() {
        return MAC_OS ? "Ctrl + \u2318" : "Alt";
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
