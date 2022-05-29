package org.jd.gui.service.preferencespanel;

import org.jd.gui.spi.PreferencesPanel;

import java.awt.Color;
import java.awt.GridLayout;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;

import static jd.core.preferences.Preferences.ESCAPE_UNICODE_CHARACTERS;
import static jd.core.preferences.Preferences.REALIGN_LINE_NUMBERS;
import static jd.core.preferences.Preferences.WRITE_LINE_NUMBERS;
import static jd.core.preferences.Preferences.WRITE_METADATA;

public abstract class AbstractJDCoreDecompilerPreferencesProvider extends JPanel implements PreferencesPanel {

    private static final long serialVersionUID = 1L;
    protected JCheckBox writeLineNumbersCheckBox;
    protected JCheckBox writeMetadataCheckBox;
    protected JCheckBox escapeUnicodeCharactersCheckBox;
    protected JCheckBox realignLineNumbersCheckBox;

    protected AbstractJDCoreDecompilerPreferencesProvider() {
        super(new GridLayout(0, 2));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        writeLineNumbersCheckBox = new JCheckBox("Write original line numbers");
        writeMetadataCheckBox = new JCheckBox("Write metadata");
        escapeUnicodeCharactersCheckBox = new JCheckBox("Escape unicode characters");
        realignLineNumbersCheckBox = new JCheckBox("Realign line numbers");

        add(writeLineNumbersCheckBox);
        add(writeMetadataCheckBox);
        add(escapeUnicodeCharactersCheckBox);
        add(realignLineNumbersCheckBox);
    }

    @Override
    public void restoreDefaults() {
        writeLineNumbersCheckBox.setSelected(false);
        writeMetadataCheckBox.setSelected(false);
        escapeUnicodeCharactersCheckBox.setSelected(false);
        realignLineNumbersCheckBox.setSelected(false);
    }

    @Override
    public void loadPreferences(Map<String, String> preferences) {
        writeLineNumbersCheckBox.setSelected("true".equals(preferences.get(WRITE_LINE_NUMBERS)));
        writeMetadataCheckBox.setSelected("true".equals(preferences.get(WRITE_METADATA)));
        escapeUnicodeCharactersCheckBox.setSelected("true".equals(preferences.get(ESCAPE_UNICODE_CHARACTERS)));
        realignLineNumbersCheckBox.setSelected("true".equals(preferences.get(REALIGN_LINE_NUMBERS)));
    }

    @Override
    public void savePreferences(Map<String, String> preferences) {
        preferences.put(WRITE_LINE_NUMBERS, Boolean.toString(writeLineNumbersCheckBox.isSelected()));
        preferences.put(WRITE_METADATA, Boolean.toString(writeMetadataCheckBox.isSelected()));
        preferences.put(ESCAPE_UNICODE_CHARACTERS, Boolean.toString(escapeUnicodeCharactersCheckBox.isSelected()));
        preferences.put(REALIGN_LINE_NUMBERS, Boolean.toString(realignLineNumbersCheckBox.isSelected()));
    }

    // --- PreferencesPanel --- //
    @Override
    public String getPreferencesGroupTitle() {
        return "Decompiler";
    }

    @Override
    public String getPreferencesPanelTitle() {
        return "Class file";
    }

    @Override
    public JComponent getPanel() {
        return this;
    }

    @Override
    public void init(Color errorBackgroundColor) {
    }

    @Override
    public boolean isActivated() {
        return true;
    }

    @Override
    public boolean arePreferencesValid() {
        return true;
    }

    @Override
    public void addPreferencesChangeListener(PreferencesPanel.PreferencesPanelChangeListener listener) {
    }

}
