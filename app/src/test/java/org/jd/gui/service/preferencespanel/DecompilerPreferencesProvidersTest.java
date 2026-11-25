/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.preferencespanel;

import org.jd.gui.spi.PreferencesPanel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jd.gui.util.decompiler.GuiPreferences.DECOMPILE_ENGINE;

/** The options of the decompilers: what their panels show, and what clicking "Ok" saves. */
class DecompilerPreferencesProvidersTest {

    private static Arguments panel(String name, Supplier<PreferencesPanel> panel, String defaultOnOption, String on) {
        return Arguments.of(name, panel, defaultOnOption, on);
    }

    static Stream<Arguments> panels() {
        return Stream.of(
                panel("CFR", CFRDecompilerPreferencesProvider::new, "sugarboxing", "true"),
                panel("Procyon", ProcyonDecompilerPreferencesProvider::new, null, "true"),
                panel("Fernflower", FernflowerDecompilerPreferencesProvider::new, "rbr", "1"),
                panel("Vineflower", VineflowerDecompilerPreferencesProvider::new, "remove-bridge", "1"),
                panel("Jadx", JadxDecompilerPreferencesProvider::new, "UseImports", "true"));
    }

    private static Map<String, String> saved(PreferencesPanel panel) {
        Map<String, String> preferences = new HashMap<>();
        panel.savePreferences(preferences);
        return preferences;
    }

    private static Map<String, String> defaults(Supplier<PreferencesPanel> factory) {
        PreferencesPanel panel = factory.get();
        panel.restoreDefaults();
        return saved(panel);
    }

    /** Gives every option another value than the one it shows. */
    private static void changeEveryOption(PreferencesPanel panel) {
        JPanel root = (JPanel) panel;
        PanelSupport.find(root, JCheckBox.class).forEach(box -> box.setSelected(!box.isSelected()));
        PanelSupport.find(root, JSpinner.class).forEach(spinner -> spinner.setValue((Integer) spinner.getValue() + 7));
        // the text fields of the spinners and of the combo boxes are not options
        for (java.awt.Component component : root.getComponents()) {
            if (component instanceof JTextField field) {
                field.setText(field.getText() + "7");
            } else if (component instanceof JComboBox<?> combo) {
                combo.setSelectedIndex((combo.getSelectedIndex() + 1) % combo.getItemCount());
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("panels")
    void whenNothingIsSavedYetTheDefaultsAreShownAndOkSavesThem(String name, Supplier<PreferencesPanel> factory, String defaultOnOption, String on) {
        PreferencesPanel panel = factory.get();

        panel.loadPreferences(new HashMap<>());

        assertThat(saved(panel)).isNotEmpty().isEqualTo(defaults(factory));
        if (defaultOnOption != null) {
            assertThat(saved(panel)).containsEntry(defaultOnOption, on);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("panels")
    void everySavedOptionIsLoadedBack(String name, Supplier<PreferencesPanel> factory) {
        PreferencesPanel panel = factory.get();
        panel.restoreDefaults();
        changeEveryOption(panel);
        Map<String, String> changed = saved(panel);

        PreferencesPanel reloaded = factory.get();
        reloaded.loadPreferences(changed);

        assertThat(changed).isNotEqualTo(defaults(factory));
        assertThat(saved(reloaded)).isEqualTo(changed);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("panels")
    void anOptionThatIsNoLongerSavedShowsItsDefaultAgain(String name, Supplier<PreferencesPanel> factory) {
        PreferencesPanel panel = factory.get();
        panel.restoreDefaults();
        changeEveryOption(panel);
        Map<String, String> withoutValues = saved(panel);
        withoutValues.replaceAll((_, _) -> null);

        panel.loadPreferences(withoutValues);

        assertThat(saved(panel)).isEqualTo(defaults(factory));
        changeEveryOption(panel);
        panel.loadPreferences(new HashMap<>());
        assertThat(saved(panel)).isEqualTo(defaults(factory));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("panels")
    void restoreDefaultsUndoesEveryChange(String name, Supplier<PreferencesPanel> factory) {
        PreferencesPanel panel = factory.get();
        panel.restoreDefaults();
        changeEveryOption(panel);

        panel.restoreDefaults();

        assertThat(saved(panel)).isEqualTo(defaults(factory));
        assertThat(panel.getPreferencesGroupTitle()).isEqualTo("Decompiler");
        assertThat(panel.getPreferencesPanelTitle()).isEqualTo("Class file");
        assertThat(panel.arePreferencesValid()).isTrue();
        assertThat(panel.useCompactDisplay()).isFalse();
        panel.addPreferencesChangeListener(null);
    }

    @Test
    void theNumbersOfVineflowerAreSavedAndLoadedAndOneThatIsNoNumberIsZero() {
        VineflowerDecompilerPreferencesProvider panel = new VineflowerDecompilerPreferencesProvider();
        JSpinner spinner = PanelSupport.find(panel, JSpinner.class).get(0);
        panel.loadPreferences(new HashMap<>());
        spinner.setValue(1234);

        Map<String, String> preferences = saved(panel);

        assertThat(preferences).containsValue("1234");
        String key = preferences.entrySet().stream().filter(e -> "1234".equals(e.getValue())).findFirst().orElseThrow().getKey();
        VineflowerDecompilerPreferencesProvider reloaded = new VineflowerDecompilerPreferencesProvider();
        reloaded.loadPreferences(preferences);
        assertThat(saved(reloaded)).containsEntry(key, "1234");
        preferences.put(key, "many");
        reloaded.loadPreferences(preferences);
        assertThat(saved(reloaded)).containsEntry(key, "0");
    }

    @Test
    void theChosenDecompilerAndTheOptionsOfEveryDecompilerAreSaved() {
        ClassFileDecompilerPreferencesProvider panel = new ClassFileDecompilerPreferencesProvider();
        panel.loadPreferences(new HashMap<>());
        String initial = saved(panel).get(DECOMPILE_ENGINE);
        Map<String, String> preferences = new HashMap<>();
        preferences.put(DECOMPILE_ENGINE, "CFR");

        panel.loadPreferences(preferences);

        assertThat(saved(panel)).containsEntry(DECOMPILE_ENGINE, "CFR").containsEntry("sugarboxing", "true").containsEntry("rbr", "1")
                .containsEntry("remove-bridge", "1").containsEntry("UseImports", "true");
        panel.restoreDefaults();
        assertThat(saved(panel)).containsEntry(DECOMPILE_ENGINE, initial);
        assertThat(panel.getPreferencesGroupTitle()).isEqualTo("Decompiler");
        assertThat(panel.getPreferencesPanelTitle()).isEqualTo("Class file");
        assertThat(panel.arePreferencesValid()).isTrue();
        assertThat(panel.useCompactDisplay()).isTrue();
        panel.addPreferencesChangeListener(null);
    }
}
