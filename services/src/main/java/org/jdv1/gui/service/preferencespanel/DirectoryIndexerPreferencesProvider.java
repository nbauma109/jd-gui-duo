/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jdv1.gui.service.preferencespanel;

import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.spi.PreferencesPanel;

import java.awt.BorderLayout;
import java.awt.Color;
import java.util.Map;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import static org.jd.gui.util.decompiler.GuiPreferences.MAXIMUM_DEPTH_KEY;

public class DirectoryIndexerPreferencesProvider extends JPanel implements PreferencesPanel, DocumentListener {

    private static final long serialVersionUID = 1L;
    protected static final int MAX_VALUE = 30;

    protected transient PreferencesPanel.PreferencesPanelChangeListener listener;
    protected JTextField maximumDepthTextField;
    protected Color errorBackgroundColor = Color.RED;
    protected Color defaultBackgroundColor;

    public DirectoryIndexerPreferencesProvider() {
        super(new BorderLayout());

        add(new JLabel("Maximum depth (1.." + MAX_VALUE + "): "), BorderLayout.WEST);

        maximumDepthTextField = new JTextField();
        maximumDepthTextField.getDocument().addDocumentListener(this);
        add(maximumDepthTextField, BorderLayout.CENTER);

        defaultBackgroundColor = maximumDepthTextField.getBackground();
    }

    // --- PreferencesPanel --- //
    @Override
    public String getPreferencesGroupTitle() { return "Indexer"; }
    @Override
    public String getPreferencesPanelTitle() { return "Directory exploration"; }
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
        String preference = preferences.get(MAXIMUM_DEPTH_KEY);

        maximumDepthTextField.setText(preference != null ? preference : "15");
        maximumDepthTextField.setCaretPosition(maximumDepthTextField.getText().length());
    }

    @Override
    public void savePreferences(Map<String, String> preferences) {
        preferences.put(MAXIMUM_DEPTH_KEY, maximumDepthTextField.getText());
    }

    @Override
    public boolean arePreferencesValid() {
        try {
            String maxDepth = maximumDepthTextField.getText();
            if (maxDepth != null && maxDepth.matches("\\d+")) {
                int i = Integer.parseInt(maxDepth);
                return i > 0 && i <= MAX_VALUE;
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
        maximumDepthTextField.setBackground(arePreferencesValid() ? defaultBackgroundColor : errorBackgroundColor);

        if (listener != null) {
            listener.preferencesPanelChanged(this);
        }
    }
}
