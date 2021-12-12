/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jdv1.gui.service.preferencespanel;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.spi.PreferencesPanel;

import java.awt.BorderLayout;
import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import static org.jd.gui.util.decompiler.GuiPreferences.FONT_SIZE_KEY;

public class ViewerPreferencesProvider extends JPanel implements PreferencesPanel, DocumentListener {

    private static final long serialVersionUID = 1L;
    protected static final int MIN_VALUE = 2;
    protected static final int MAX_VALUE = 40;

    protected transient PreferencesPanel.PreferencesPanelChangeListener listener = null;
    protected JTextField fontSizeTextField;
    protected Color errorBackgroundColor = Color.RED;
    protected Color defaultBackgroundColor;

    public ViewerPreferencesProvider() {
        super(new BorderLayout());

        add(new JLabel("Font size (" + MIN_VALUE + ".." + MAX_VALUE + "): "), BorderLayout.WEST);

        fontSizeTextField = new JTextField();
        fontSizeTextField.getDocument().addDocumentListener(this);
        add(fontSizeTextField, BorderLayout.CENTER);

        defaultBackgroundColor = fontSizeTextField.getBackground();
    }

    // --- PreferencesPanel --- //
    @Override
    public String getPreferencesGroupTitle() { return "Viewer"; }
    @Override
    public String getPreferencesPanelTitle() { return "Appearance"; }
    @Override
    public JComponent getPanel() { return this; }

    @Override
    public void init(Color errorBackgroundColor) {
        this.errorBackgroundColor = errorBackgroundColor;
    }

    @Override
    public boolean isActivated() { return true; }

    @Override
    public void loadPreferences(Map<String, String> preferences) {
        String fontSize = preferences.get(FONT_SIZE_KEY);

        if (fontSize == null) {
            // Search default value for the current platform
            RSyntaxTextArea textArea = new RSyntaxTextArea();

            try (InputStream resourceAsStream = getClass().getClassLoader().getResourceAsStream("rsyntaxtextarea/themes/eclipse.xml")) {
                Theme theme = Theme.load(resourceAsStream);
                theme.apply(textArea);
            } catch (IOException e) {
                assert ExceptionUtil.printStackTrace(e);
            }

            fontSize = String.valueOf(textArea.getFont().getSize());
        }

        fontSizeTextField.setText(fontSize);
        fontSizeTextField.setCaretPosition(fontSizeTextField.getText().length());
    }

    @Override
    public void savePreferences(Map<String, String> preferences) {
        preferences.put(FONT_SIZE_KEY, fontSizeTextField.getText());
    }

    @Override
    public boolean arePreferencesValid() {
        try {
            String fontSize = fontSizeTextField.getText();
            if (fontSize != null && fontSize.matches("\\d+")) {
                int i = Integer.parseInt(fontSize);
                return i >= 2 && i <= 40;
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
        fontSizeTextField.setBackground(arePreferencesValid() ? defaultBackgroundColor : errorBackgroundColor);

        if (listener != null) {
            listener.preferencesPanelChanged(this);
        }
    }
}
