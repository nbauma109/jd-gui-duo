/*
 * Copyright (c) 2026 Nicolas Baumann (@nbauma109).
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.view;

import static org.jd.gui.util.ImageUtil.newImageIcon;
import static org.jd.gui.util.KeyBindings.Binding.FIND_HIGHLIGHT;
import static org.jd.gui.util.KeyBindings.Binding.FIND_MATCH_CASE;
import static org.jd.gui.util.KeyBindings.Binding.FIND_NEXT;
import static org.jd.gui.util.KeyBindings.Binding.FIND_PREVIOUS;
import static org.jd.gui.util.KeyBindings.Binding.FIND_REGEX;
import static org.jd.gui.util.KeyBindings.Binding.FIND_WHOLE_WORD;
import static org.jd.gui.util.KeyBindings.Binding.FIND_WRAP_AROUND;

import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Map;
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

import org.jd.gui.util.KeyBindings;
import org.jd.gui.util.KeyBindings.Binding;

public final class SearchUIOptions {
    private static final String NETBEANS_BUNDLE = "org.netbeans.modules.editor.search.Bundle";
    private static final String DEFAULT_INCREMENTAL_SEARCH_TOOLTIP = "Search Text";
    private static final String DEFAULT_SELECT_ALL_TOOLTIP = "Place a selection around all occurrences in the editor";

    private final JToggleButton matchCaseButton;
    private final JToggleButton wholeWordButton;
    private final JToggleButton regexButton;
    private final JToggleButton markAllButton;
    private final JToggleButton wrapButton;
    private Map<String, String> preferences;

    public SearchUIOptions(ActionListener findWithOptionsActionListener) {
        this(Map.of(), findWithOptionsActionListener);
    }

    public SearchUIOptions(Map<String, String> preferences, ActionListener findWithOptionsActionListener) {
        this.preferences = preferences != null ? preferences : Map.of();
        this.matchCaseButton = createToggleIconButton("matchCase.png", getOptionTooltip("TT_MatchCase", "Match case", "C"));
        this.wholeWordButton = createToggleIconButton("wholeWord.png", getOptionTooltip("TT_WholeWords", "Whole word", "O"));
        this.regexButton = createToggleIconButton("regexp.png", getOptionTooltip("TT_Regexp", "Regular expression", "G"));
        this.markAllButton = createToggleIconButton("highlight.png", getOptionTooltip("TT_Highlight", "Highlight matches", "H"));
        this.wrapButton = createToggleIconButton("wrapAround.png", getOptionTooltip("TT_WrapAround", "Wrap Around", "U"));
        refreshTooltips(this.preferences);
        addOptionsChangeListener(findWithOptionsActionListener);
    }

    public SearchUIOptions() {
        this(Map.of(), null);
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
        bindOptionKeyStrokes(searchField, preferences);
    }

    public void bindOptionKeyStrokes(JTextComponent searchField, Map<String, String> preferences) {
        bindOptionKeyStroke(searchField, "matchcasekey", FIND_MATCH_CASE, preferences, matchCaseButton);
        bindOptionKeyStroke(searchField, "wholewordkey", FIND_WHOLE_WORD, preferences, wholeWordButton);
        bindOptionKeyStroke(searchField, "regexpkey", FIND_REGEX, preferences, regexButton);
        bindOptionKeyStroke(searchField, "highlightkey", FIND_HIGHLIGHT, preferences, markAllButton);
        bindOptionKeyStroke(searchField, "wraparoundkey", FIND_WRAP_AROUND, preferences, wrapButton);
    }

    private void bindOptionKeyStroke(JTextComponent searchField, String actionKey, Binding binding, Map<String, String> preferences, AbstractButton button) {
        InputMap inputMap = searchField.getInputMap();
        ActionMap actionMap = searchField.getActionMap();

        clearInputMapAction(inputMap, actionKey);

        KeyStroke keyStroke = KeyBindings.getConfiguredKeyStroke(preferences, binding);
        if (keyStroke != null) {
            inputMap.put(keyStroke, actionKey);
        }
        actionMap.put(actionKey, new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                button.doClick(0);
            }
        });
    }

    public static void configureIncrementalSearchField(JTextComponent searchField) {
        configureIncrementalSearchField(searchField, Map.of());
    }

    public static void configureIncrementalSearchField(JTextComponent searchField, Map<String, String> preferences) {
        searchField.setToolTipText(getIncrementalSearchTooltip(preferences));
    }

    public static void configureFindButton(AbstractButton button) {
        configureFindButton(button, Map.of());
    }

    public static void configureFindButton(AbstractButton button, Map<String, String> preferences) {
        button.setToolTipText(getIncrementalSearchTooltip(preferences));
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
        return getIncrementalSearchTooltip(Map.of());
    }

    public static String getIncrementalSearchTooltip(Map<String, String> preferences) {
        String tooltip = DEFAULT_INCREMENTAL_SEARCH_TOOLTIP;
        KeyStroke next = KeyBindings.getConfiguredKeyStroke(preferences, FIND_NEXT);
        KeyStroke previous = KeyBindings.getConfiguredKeyStroke(preferences, FIND_PREVIOUS);
        String suffix = formatSearchNavigationText(next, previous);

        return suffix.isEmpty() ? tooltip : tooltip + " (" + suffix + ")";
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

    private String getOptionTooltip(String key, String label, String keySuffix) {
        return getMessage(key, label);
    }

    public void refreshTooltips(Map<String, String> preferences) {
        this.preferences = preferences != null ? preferences : Map.of();
        matchCaseButton.setToolTipText(buildOptionTooltip("TT_MatchCase", "Match case", FIND_MATCH_CASE));
        wholeWordButton.setToolTipText(buildOptionTooltip("TT_WholeWords", "Whole word", FIND_WHOLE_WORD));
        regexButton.setToolTipText(buildOptionTooltip("TT_Regexp", "Regular expression", FIND_REGEX));
        markAllButton.setToolTipText(buildOptionTooltip("TT_Highlight", "Highlight matches", FIND_HIGHLIGHT));
        wrapButton.setToolTipText(buildOptionTooltip("TT_WrapAround", "Wrap Around", FIND_WRAP_AROUND));
    }

    public static void bindSearchNavigationKeyStrokes(
        JTextComponent searchField,
        Map<String, String> preferences,
        Runnable nextAction,
        Runnable previousAction
    ) {
        bindSearchNavigationKeyStroke(searchField, "searchForward", FIND_NEXT, preferences, nextAction);
        bindSearchNavigationKeyStroke(searchField, "searchBackward", FIND_PREVIOUS, preferences, previousAction);
    }

    private static void bindSearchNavigationKeyStroke(
        JTextComponent searchField,
        String actionKey,
        Binding binding,
        Map<String, String> preferences,
        Runnable action
    ) {
        InputMap inputMap = searchField.getInputMap();
        ActionMap actionMap = searchField.getActionMap();

        clearInputMapAction(inputMap, actionKey);

        KeyStroke keyStroke = KeyBindings.getConfiguredKeyStroke(preferences, binding);
        if (keyStroke != null) {
            inputMap.put(keyStroke, actionKey);
        }
        actionMap.put(actionKey, new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    private String buildOptionTooltip(String key, String fallbackLabel, Binding binding) {
        String label = stripShortcutSuffix(getMessage(key, fallbackLabel));
        KeyStroke keyStroke = KeyBindings.getConfiguredKeyStroke(preferences, binding);
        return keyStroke == null ? label : label + " (" + KeyBindings.toDisplayText(keyStroke) + ")";
    }

    static String stripShortcutSuffix(String tooltip) {
        if (tooltip == null) {
            return "";
        }

        int start = tooltip.lastIndexOf(" (");
        return (start == -1) || !tooltip.endsWith(")") ? tooltip : tooltip.substring(0, start);
    }

    private static String formatSearchNavigationText(KeyStroke next, KeyStroke previous) {
        if ((next == null) && (previous == null)) {
            return "";
        }
        if (next == null) {
            return KeyBindings.toDisplayText(previous) + " - " + getFindPreviousLabel();
        }
        if (previous == null) {
            return KeyBindings.toDisplayText(next) + " - " + getFindNextLabel();
        }

        return KeyBindings.toDisplayText(next) + " - " + getFindNextLabel()
            + ", " + KeyBindings.toDisplayText(previous) + " - " + getFindPreviousLabel();
    }

    private static void clearInputMapAction(InputMap inputMap, String actionKey) {
        KeyStroke[] keys = inputMap.allKeys();

        if (keys == null) {
            return;
        }

        for (KeyStroke key : keys) {
            Object value = inputMap.get(key);
            if (actionKey.equals(value)) {
                inputMap.remove(key);
            }
        }
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
