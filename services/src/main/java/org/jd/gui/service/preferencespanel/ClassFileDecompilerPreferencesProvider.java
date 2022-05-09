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
import static org.jd.gui.util.decompiler.GuiPreferences.SHOW_COMPILER_ERRORS;
import static org.jd.gui.util.decompiler.GuiPreferences.SHOW_COMPILER_WARNINGS;
import static org.jd.gui.util.decompiler.GuiPreferences.SHOW_COMPILER_INFO;
import static org.jd.gui.util.decompiler.GuiPreferences.ADVANCED_CLASS_LOOKUP;
import static org.jd.gui.util.decompiler.GuiPreferences.REMOVE_UNNECESSARY_CASTS;

import static com.heliosdecompiler.transformerapi.StandardTransformers.Decompilers.ENGINE_JD_CORE_V0;
import static com.heliosdecompiler.transformerapi.StandardTransformers.Decompilers.ENGINE_JD_CORE_V1;
import static com.heliosdecompiler.transformerapi.StandardTransformers.Decompilers.ENGINE_CFR;
import static com.heliosdecompiler.transformerapi.StandardTransformers.Decompilers.ENGINE_PROCYON;
import static com.heliosdecompiler.transformerapi.StandardTransformers.Decompilers.ENGINE_FERNFLOWER;
import static com.heliosdecompiler.transformerapi.StandardTransformers.Decompilers.ENGINE_JADX;
import static com.heliosdecompiler.transformerapi.StandardTransformers.Disassemblers.ENGINE_PROCYON_DISASSEMBLER;

public class ClassFileDecompilerPreferencesProvider extends JPanel implements PreferencesPanel {

    private static final long serialVersionUID = 1L;

    protected JCheckBox escapeUnicodeCharactersCheckBox;
    protected JCheckBox realignLineNumbersCheckBox;
    protected JCheckBox showCompilerErrorsCheckBox;
    protected JCheckBox showCompilerWarningsCheckBox;
    protected JCheckBox showCompilerInfoCheckBox;
    protected JCheckBox advancedClassLookupCheckBox;
    protected JCheckBox removeUnnecessaryCastsCheckBox;
    protected JLabel selectDecompiler;
    protected JComboBox<String> decompileEngine;

    private static final String[] DECOMPILERS = { ENGINE_JD_CORE_V1, ENGINE_JD_CORE_V0, ENGINE_CFR, 
            ENGINE_PROCYON, ENGINE_FERNFLOWER, ENGINE_JADX, ENGINE_PROCYON_DISASSEMBLER };

    public ClassFileDecompilerPreferencesProvider() {
        super(new GridLayout(0,2));

        escapeUnicodeCharactersCheckBox = new JCheckBox("Escape unicode characters");
        realignLineNumbersCheckBox = new JCheckBox("Realign line numbers");
        showCompilerErrorsCheckBox = new JCheckBox("Show compiler errors");
        showCompilerWarningsCheckBox = new JCheckBox("Show compiler warnings");
        showCompilerInfoCheckBox = new JCheckBox("Show compiler info");
        advancedClassLookupCheckBox = new JCheckBox("Advanced class lookup");
        removeUnnecessaryCastsCheckBox = new JCheckBox("Remove unnecessary casts");
        selectDecompiler = new JLabel("Select Decompile Engine: ");
        decompileEngine = new JComboBox<>(DECOMPILERS);

        add(showCompilerWarningsCheckBox);
        add(realignLineNumbersCheckBox);
        add(showCompilerErrorsCheckBox);
        add(escapeUnicodeCharactersCheckBox);
        add(showCompilerInfoCheckBox);
        add(advancedClassLookupCheckBox);
        add(selectDecompiler);
        add(removeUnnecessaryCastsCheckBox);
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
        showCompilerErrorsCheckBox.setSelected("true".equals(preferences.get(SHOW_COMPILER_ERRORS)));
        showCompilerWarningsCheckBox.setSelected("true".equals(preferences.get(SHOW_COMPILER_WARNINGS)));
        showCompilerInfoCheckBox.setSelected("true".equals(preferences.get(SHOW_COMPILER_INFO)));
        advancedClassLookupCheckBox.setSelected("true".equals(preferences.get(ADVANCED_CLASS_LOOKUP)));
        removeUnnecessaryCastsCheckBox.setSelected("true".equals(preferences.get(REMOVE_UNNECESSARY_CASTS)));
        decompileEngine.setSelectedItem(preferences.getOrDefault(DECOMPILE_ENGINE, ENGINE_JD_CORE_V1));
    }

    @Override
    public void savePreferences(Map<String, String> preferences) {
        preferences.put(ESCAPE_UNICODE_CHARACTERS, Boolean.toString(escapeUnicodeCharactersCheckBox.isSelected()));
        preferences.put(REALIGN_LINE_NUMBERS, Boolean.toString(realignLineNumbersCheckBox.isSelected()));
        preferences.put(SHOW_COMPILER_ERRORS, Boolean.toString(showCompilerErrorsCheckBox.isSelected()));
        preferences.put(SHOW_COMPILER_WARNINGS, Boolean.toString(showCompilerWarningsCheckBox.isSelected()));
        preferences.put(SHOW_COMPILER_INFO, Boolean.toString(showCompilerInfoCheckBox.isSelected()));
        preferences.put(ADVANCED_CLASS_LOOKUP, Boolean.toString(advancedClassLookupCheckBox.isSelected()));
        preferences.put(REMOVE_UNNECESSARY_CASTS, Boolean.toString(removeUnnecessaryCastsCheckBox.isSelected()));
        preferences.put(DECOMPILE_ENGINE, decompileEngine.getSelectedItem().toString());
    }

    @Override
    public boolean arePreferencesValid() { return true; }

    @Override
    public void addPreferencesChangeListener(PreferencesPanel.PreferencesPanelChangeListener listener) {}
}
