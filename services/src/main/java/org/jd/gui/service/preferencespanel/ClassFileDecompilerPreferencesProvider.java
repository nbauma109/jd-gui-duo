/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.preferencespanel;

import org.jd.gui.spi.PreferencesPanel;

import java.awt.Color;
import java.awt.GridLayout;
import java.util.Map;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import static jd.core.preferences.Preferences.ESCAPE_UNICODE_CHARACTERS;
import static jd.core.preferences.Preferences.REALIGN_LINE_NUMBERS;
import static org.jd.gui.util.decompiler.GuiPreferences.DECOMPILE_ENGINE;

import static com.heliosdecompiler.transformerapi.StandardTransformers.Decompilers.ENGINE_JD_CORE_V0;
import static com.heliosdecompiler.transformerapi.StandardTransformers.Decompilers.ENGINE_JD_CORE_V1;
import static com.heliosdecompiler.transformerapi.StandardTransformers.Decompilers.ENGINE_CFR;
import static com.heliosdecompiler.transformerapi.StandardTransformers.Decompilers.ENGINE_PROCYON;
import static com.heliosdecompiler.transformerapi.StandardTransformers.Decompilers.ENGINE_FERNFLOWER;

public class ClassFileDecompilerPreferencesProvider extends JPanel implements PreferencesPanel {

    private static final long serialVersionUID = 1L;

    protected transient PreferencesPanel.PreferencesPanelChangeListener listener;
    protected JCheckBox escapeUnicodeCharactersCheckBox;
    protected JCheckBox realignLineNumbersCheckBox;
    protected JLabel selectDecompiler;
    protected JComboBox<String> decompileEngine;

    private static final String[] DECOMPILERS = { ENGINE_JD_CORE_V1, ENGINE_JD_CORE_V0, ENGINE_CFR, ENGINE_PROCYON, ENGINE_FERNFLOWER };

    public ClassFileDecompilerPreferencesProvider() {
        super(new GridLayout(0,1));

        escapeUnicodeCharactersCheckBox = new JCheckBox("Escape unicode characters");
        realignLineNumbersCheckBox = new JCheckBox("Realign line numbers");
        selectDecompiler = new JLabel("Select Decompile Engine: ");
        decompileEngine = new JComboBox<>(DECOMPILERS);

        add(escapeUnicodeCharactersCheckBox);
        add(realignLineNumbersCheckBox);
        add(selectDecompiler);
        add(decompileEngine);
    }

    // --- PreferencesPanel --- //
    @Override
    public String getPreferencesGroupTitle() { return "Decompiler"; }
    @Override
    public String getPreferencesPanelTitle() { return "Class file"; }
    @Override
    public JComponent getPanel() { return this; }

    @Override
    public void init(Color errorBackgroundColor) {}

    @Override
    public boolean isActivated() { return true; }

    @Override
    public void loadPreferences(Map<String, String> preferences) {
        escapeUnicodeCharactersCheckBox.setSelected("true".equals(preferences.get(ESCAPE_UNICODE_CHARACTERS)));
        realignLineNumbersCheckBox.setSelected("true".equals(preferences.get(REALIGN_LINE_NUMBERS)));
        decompileEngine.setSelectedItem(preferences.getOrDefault(DECOMPILE_ENGINE, ENGINE_JD_CORE_V1));
    }

    @Override
    public void savePreferences(Map<String, String> preferences) {
        preferences.put(ESCAPE_UNICODE_CHARACTERS, Boolean.toString(escapeUnicodeCharactersCheckBox.isSelected()));
        preferences.put(REALIGN_LINE_NUMBERS, Boolean.toString(realignLineNumbersCheckBox.isSelected()));
        preferences.put(DECOMPILE_ENGINE, decompileEngine.getSelectedItem().toString());
    }

    @Override
    public boolean arePreferencesValid() { return true; }

    @Override
    public void addPreferencesChangeListener(PreferencesPanel.PreferencesPanelChangeListener listener) {}
}
