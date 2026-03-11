/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.preferencespanel;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.spi.PreferencesPanel;
import org.jd.gui.util.ThemeUtil;

import java.awt.Color;
import java.awt.GridLayout;
import java.util.Map;

import javax.swing.JTree;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import static org.jd.gui.util.decompiler.GuiPreferences.FONT_SIZE_KEY;
import static org.jd.gui.util.decompiler.GuiPreferences.TREE_NODE_FONT_SIZE_KEY;

public class ViewerPreferencesProvider extends JPanel implements PreferencesPanel, DocumentListener {

    private static final long serialVersionUID = 1L;
    protected static final int MIN_VALUE = 2;
    protected static final int MAX_VALUE = 40;

    protected transient PreferencesPanel.PreferencesPanelChangeListener listener;
    protected JTextField fontSizeTextField;
    protected JTextField treeNodeFontSizeTextField;
    protected Color errorBackgroundColor = Color.RED;
    protected Color defaultBackgroundColor;

    public ViewerPreferencesProvider() {
        super(new GridLayout(2, 2, 6, 4));

        add(new JLabel("Font Size Of Text Area (" + MIN_VALUE + ".." + MAX_VALUE + "): "));

        fontSizeTextField = new JTextField();
        fontSizeTextField.getDocument().addDocumentListener(this);
        add(fontSizeTextField);

        add(new JLabel("Font Size Of Tree Node (" + MIN_VALUE + ".." + MAX_VALUE + "): "));

        treeNodeFontSizeTextField = new JTextField();
        treeNodeFontSizeTextField.getDocument().addDocumentListener(this);
        add(treeNodeFontSizeTextField);

        defaultBackgroundColor = fontSizeTextField.getBackground();
    }

    // --- PreferencesPanel --- //
    @Override
    public String getPreferencesGroupTitle() { return "Viewer"; }
    @Override
    public String getPreferencesPanelTitle() { return "Appearance"; }

    @Override
    public void init(Color errorBackgroundColor) {
        this.errorBackgroundColor = errorBackgroundColor;
    }

    @Override
    public void loadPreferences(Map<String, String> preferences) {
        String fontSize = preferences.get(FONT_SIZE_KEY);
        String treeNodeFontSize = preferences.get(TREE_NODE_FONT_SIZE_KEY);

        if (fontSize == null) {
            // Search default value for the current platform
            RSyntaxTextArea textArea = ThemeUtil.applyTheme(preferences, new RSyntaxTextArea());

            fontSize = String.valueOf(textArea.getFont().getSize());
        }
        if (treeNodeFontSize == null) {
            treeNodeFontSize = String.valueOf(new JTree().getFont().getSize());
        }

        fontSizeTextField.setText(fontSize);
        fontSizeTextField.setCaretPosition(fontSizeTextField.getText().length());
        treeNodeFontSizeTextField.setText(treeNodeFontSize);
        treeNodeFontSizeTextField.setCaretPosition(treeNodeFontSizeTextField.getText().length());
    }

    @Override
    public void savePreferences(Map<String, String> preferences) {
        preferences.put(FONT_SIZE_KEY, fontSizeTextField.getText());
        preferences.put(TREE_NODE_FONT_SIZE_KEY, treeNodeFontSizeTextField.getText());
    }

    @Override
    public boolean arePreferencesValid() {
        return isValidFontSize(fontSizeTextField.getText()) && isValidFontSize(treeNodeFontSizeTextField.getText());
    }

    protected boolean isValidFontSize(String fontSize) {
        try {
            if (fontSize != null && fontSize.matches("\\d+")) {
                int i = Integer.parseInt(fontSize);
                return i >= MIN_VALUE && i <= MAX_VALUE;
            }
        } catch (NumberFormatException e) {
            assert ExceptionUtil.printStackTrace(e);
        }
        return false;
    }

    @Override
    public void addPreferencesChangeListener(PreferencesPanel.PreferencesPanelChangeListener listener) {
        this.listener = listener;
    }

    // --- DocumentListener --- //
    @Override
    public void insertUpdate(DocumentEvent e) { onTextChange(); }
    @Override
    public void removeUpdate(DocumentEvent e) { onTextChange(); }
    @Override
    public void changedUpdate(DocumentEvent e) { onTextChange(); }

    public void onTextChange() {
        fontSizeTextField.setBackground(isValidFontSize(fontSizeTextField.getText()) ? defaultBackgroundColor : errorBackgroundColor);
        treeNodeFontSizeTextField.setBackground(isValidFontSize(treeNodeFontSizeTextField.getText()) ? defaultBackgroundColor : errorBackgroundColor);

        if (listener != null) {
            listener.preferencesPanelChanged(this);
        }
    }

    @Override
    public void restoreDefaults() {
        fontSizeTextField.setText("12");
        treeNodeFontSizeTextField.setText(String.valueOf(new JTree().getFont().getSize()));
    }

    @Override
    public boolean useCompactDisplay() {
        return true;
    }
}
