package org.jd.gui.service.preferencespanel;

import org.jd.gui.spi.PreferencesPanel;

import java.awt.Color;
import java.awt.GridLayout;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;

import static com.heliosdecompiler.transformerapi.StandardTransformers.Decompilers.ENGINE_JD_CORE_V0;
import static jd.core.preferences.Preferences.DISPLAY_DEFAULT_CONSTRUCTOR;
import static jd.core.preferences.Preferences.ESCAPE_UNICODE_CHARACTERS;
import static jd.core.preferences.Preferences.OMIT_THIS_PREFIX;
import static jd.core.preferences.Preferences.REALIGN_LINE_NUMBERS;
import static jd.core.preferences.Preferences.WRITE_LINE_NUMBERS;
import static jd.core.preferences.Preferences.WRITE_METADATA;

public class JDCoreDecompilerPreferencesProvider extends JPanel implements PreferencesPanel {

    private static final long serialVersionUID = 1L;
    protected JCheckBox writeLineNumbersCheckBox;
    protected JCheckBox writeMetadataCheckBox;
    protected JCheckBox escapeUnicodeCharactersCheckBox;
    protected JCheckBox realignLineNumbersCheckBox;
    protected JCheckBox omitThisPrefixCheckBox;
    protected JCheckBox displayDefaultConstructorCheckBox;
    protected JComboBox<String> decompileEngine;

    public JDCoreDecompilerPreferencesProvider(JComboBox<String> decompileEngine) {
        super(new GridLayout(0, 2));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        this.decompileEngine = decompileEngine;

        writeLineNumbersCheckBox = new JCheckBox("Write original line numbers");
        writeMetadataCheckBox = new JCheckBox("Write metadata");
        escapeUnicodeCharactersCheckBox = new JCheckBox("Escape unicode characters");
        realignLineNumbersCheckBox = new JCheckBox("Realign line numbers");
        omitThisPrefixCheckBox = new JCheckBox("Omit the prefix 'this' if possible");
        displayDefaultConstructorCheckBox = new JCheckBox("Display default constructor");

        add(writeLineNumbersCheckBox);
        add(writeMetadataCheckBox);
        add(escapeUnicodeCharactersCheckBox);
        add(realignLineNumbersCheckBox);
        add(omitThisPrefixCheckBox);
        add(displayDefaultConstructorCheckBox);
    }

    @Override
    public void restoreDefaults() {
        writeLineNumbersCheckBox.setSelected(true);
        writeMetadataCheckBox.setSelected(true);
        escapeUnicodeCharactersCheckBox.setSelected(false);
        realignLineNumbersCheckBox.setSelected(false);
        omitThisPrefixCheckBox.setSelected(false);
        displayDefaultConstructorCheckBox.setSelected(false);
    }

    @Override
    public void loadPreferences(Map<String, String> preferences) {
        writeLineNumbersCheckBox.setSelected(Boolean.parseBoolean(preferences.getOrDefault(WRITE_LINE_NUMBERS, Boolean.TRUE.toString())));
        writeMetadataCheckBox.setSelected(Boolean.parseBoolean(preferences.getOrDefault(WRITE_METADATA, Boolean.TRUE.toString())));
        escapeUnicodeCharactersCheckBox.setSelected(Boolean.parseBoolean(preferences.getOrDefault(ESCAPE_UNICODE_CHARACTERS, Boolean.FALSE.toString())));
        realignLineNumbersCheckBox.setSelected(Boolean.parseBoolean(preferences.getOrDefault(REALIGN_LINE_NUMBERS, Boolean.FALSE.toString())));
        omitThisPrefixCheckBox.setSelected(Boolean.parseBoolean(preferences.getOrDefault(OMIT_THIS_PREFIX, Boolean.FALSE.toString())));
        displayDefaultConstructorCheckBox.setSelected(Boolean.parseBoolean(preferences.getOrDefault(DISPLAY_DEFAULT_CONSTRUCTOR, Boolean.FALSE.toString())));
        toggleOldOptions();
    }

    @Override
    public void savePreferences(Map<String, String> preferences) {
        preferences.put(WRITE_LINE_NUMBERS, Boolean.toString(writeLineNumbersCheckBox.isSelected()));
        preferences.put(WRITE_METADATA, Boolean.toString(writeMetadataCheckBox.isSelected()));
        preferences.put(ESCAPE_UNICODE_CHARACTERS, Boolean.toString(escapeUnicodeCharactersCheckBox.isSelected()));
        preferences.put(REALIGN_LINE_NUMBERS, Boolean.toString(realignLineNumbersCheckBox.isSelected()));
        preferences.put(OMIT_THIS_PREFIX, Boolean.toString(omitThisPrefixCheckBox.isSelected()));
        preferences.put(DISPLAY_DEFAULT_CONSTRUCTOR, Boolean.toString(displayDefaultConstructorCheckBox.isSelected()));
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
        // nothing to do
    }

    public void toggleOldOptions() {
        omitThisPrefixCheckBox.setEnabled(ENGINE_JD_CORE_V0.equals(decompileEngine.getSelectedItem()));
        displayDefaultConstructorCheckBox.setEnabled(ENGINE_JD_CORE_V0.equals(decompileEngine.getSelectedItem()));
    }

    @Override
    public boolean useCompactDisplay() {
        return false;
    }
}
