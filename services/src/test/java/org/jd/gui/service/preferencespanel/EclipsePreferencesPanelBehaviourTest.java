/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.preferencespanel;

import org.eclipse.jdt.core.JavaCore;
import org.jd.gui.spi.PreferencesPanel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jd.gui.util.decompiler.GuiPreferences.ADVANCED_CLASS_LOOKUP;
import static org.jd.gui.util.decompiler.GuiPreferences.INCLUDE_RUNNING_VM_BOOT_CLASSPATH;
import static org.jd.gui.util.decompiler.GuiPreferences.JRE_SYSTEM_LIBRARY_PATH;
import static org.jd.gui.util.decompiler.GuiPreferences.REMOVE_UNNECESSARY_CASTS;
import static org.jd.gui.util.decompiler.GuiPreferences.SHOW_COMPILER_ERRORS;
import static org.jd.gui.util.decompiler.GuiPreferences.SHOW_COMPILER_INFO;
import static org.jd.gui.util.decompiler.GuiPreferences.SHOW_COMPILER_WARNINGS;

/** The compiler panel of the Eclipse preferences: its defaults, what it loads and saves, when it is valid, and how the Java versions follow the JRE. */
class EclipsePreferencesPanelBehaviourTest {

    @TempDir
    Path dir;

    private Path javaHome(String name, String version) throws IOException {
        Path home = Files.createDirectories(dir.resolve(name));
        Files.createDirectories(home.resolve("lib"));
        Files.writeString(home.resolve("release"), "JAVA_VERSION=\"" + version + "\"\n", StandardCharsets.UTF_8);
        return home;
    }

    /** The default Java version: the latest one, limited to the version of the JVM that runs the tests (the panel follows the running JVM by default). */
    private static String defaultVersion() {
        String running = System.getProperty("java.specification.version");
        String latest = JavaCore.latestSupportedJavaVersion();
        return JavaCore.compareJavaVersions(running, latest) < 0 ? running : latest;
    }

    @Test
    void theDefaultsUseTheRunningJvmAndTheLatestJavaVersion() {
        EclipsePreferencesProvider panel = new EclipsePreferencesProvider();

        assertThat(panel.getPreferencesGroupTitle()).isEqualTo("Eclipse");
        assertThat(panel.getPreferencesPanelTitle()).isEqualTo("Compiler");
        assertThat(panel.useCompactDisplay()).isTrue();
        assertThat(panel.includeRunningVMBootClasspathCheckBox.isSelected()).isTrue();
        assertThat(panel.jreSystemLibraryPathTextField.isEnabled()).isFalse(); // the path only matters when the running JVM is not used
        assertThat(panel.jreSystemLibraryPathButton.isEnabled()).isFalse();
        assertThat(panel.arePreferencesValid()).isTrue();
        assertThat(panel.sourceComboBox.getItemCount()).isGreaterThan(5);
        assertThat(panel.sourceComboBox.getItemAt(0)).isEqualTo(JavaCore.VERSION_1_8); // the oldest version offered
    }

    @Test
    void preferencesAreLoadedAndSavedBack() {
        EclipsePreferencesProvider panel = new EclipsePreferencesProvider();
        Map<String, String> stored = new HashMap<>();
        stored.put(SHOW_COMPILER_ERRORS, "true");
        stored.put(SHOW_COMPILER_WARNINGS, "true");
        stored.put(SHOW_COMPILER_INFO, "false");
        stored.put(ADVANCED_CLASS_LOOKUP, "true");
        stored.put(REMOVE_UNNECESSARY_CASTS, "true");
        stored.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_11);
        stored.put(JavaCore.COMPILER_COMPLIANCE, JavaCore.VERSION_17);

        panel.loadPreferences(stored);
        Map<String, String> saved = new HashMap<>();
        panel.savePreferences(saved);

        assertThat(panel.showCompilerErrorsCheckBox.isSelected()).isTrue();
        assertThat(panel.showCompilerInfoCheckBox.isSelected()).isFalse();
        assertThat(saved).containsEntry(SHOW_COMPILER_ERRORS, "true").containsEntry(SHOW_COMPILER_WARNINGS, "true").containsEntry(SHOW_COMPILER_INFO, "false")
                .containsEntry(ADVANCED_CLASS_LOOKUP, "true").containsEntry(REMOVE_UNNECESSARY_CASTS, "true")
                .containsEntry(INCLUDE_RUNNING_VM_BOOT_CLASSPATH, "true").containsEntry(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_11)
                .containsEntry(JavaCore.COMPILER_COMPLIANCE, JavaCore.VERSION_17);
    }

    @Test
    void anUnknownJavaVersionFallsBackToTheLatestOne() {
        EclipsePreferencesProvider panel = new EclipsePreferencesProvider();
        Map<String, String> stored = new HashMap<>();
        stored.put(JavaCore.COMPILER_SOURCE, "not a version");
        stored.put(JavaCore.COMPILER_COMPLIANCE, "");

        panel.loadPreferences(stored);

        assertThat(panel.sourceComboBox.getSelectedItem()).isEqualTo(defaultVersion());
        assertThat(panel.complianceComboBox.getSelectedItem()).isEqualTo(defaultVersion());
        panel.loadPreferences(new HashMap<>()); // nothing stored at all
        assertThat(panel.sourceComboBox.getSelectedItem()).isEqualTo(defaultVersion());
    }

    @Test
    void anotherJreNeedsAValidPathAndItsVersionLimitsTheJavaVersions() throws IOException {
        Path jre17 = javaHome("jdk-17", "17.0.10");
        EclipsePreferencesProvider panel = new EclipsePreferencesProvider();
        Map<String, String> stored = new HashMap<>();
        stored.put(INCLUDE_RUNNING_VM_BOOT_CLASSPATH, "false");
        stored.put(JRE_SYSTEM_LIBRARY_PATH, "");
        panel.loadPreferences(stored);

        assertThat(panel.jreSystemLibraryPathTextField.isEnabled()).isTrue();
        assertThat(panel.arePreferencesValid()).as("no path").isFalse();
        panel.jreSystemLibraryPathTextField.setText(dir.resolve("missing").toString());
        assertThat(panel.arePreferencesValid()).as("a folder that does not exist").isFalse();
        Path noRelease = Files.createDirectories(dir.resolve("plain"));
        panel.jreSystemLibraryPathTextField.setText(noRelease.toString());
        assertThat(panel.arePreferencesValid()).as("a folder without a release file").isFalse();

        panel.sourceComboBox.setSelectedItem(JavaCore.latestSupportedJavaVersion());
        panel.jreSystemLibraryPathTextField.setText(jre17.toString());

        assertThat(panel.arePreferencesValid()).isTrue();
        assertThat(panel.sourceComboBox.getSelectedItem()).isEqualTo(JavaCore.VERSION_17); // a version newer than the JRE is lowered to it
        assertThat(panel.complianceComboBox.getSelectedItem()).isEqualTo(JavaCore.VERSION_17);
    }

    @Test
    void chosenVersionsAboveTheJreAreLoweredAndOlderOnesKept() throws IOException {
        Path jre17 = javaHome("jdk-17", "17.0.10");
        EclipsePreferencesProvider panel = new EclipsePreferencesProvider();
        Map<String, String> stored = new HashMap<>();
        stored.put(INCLUDE_RUNNING_VM_BOOT_CLASSPATH, "false");
        stored.put(JRE_SYSTEM_LIBRARY_PATH, jre17.toString());
        panel.loadPreferences(stored);

        panel.sourceComboBox.setSelectedItem(JavaCore.VERSION_11);
        panel.complianceComboBox.setSelectedItem(JavaCore.latestSupportedJavaVersion());

        assertThat(panel.sourceComboBox.getSelectedItem()).isEqualTo(JavaCore.VERSION_11);
        assertThat(panel.complianceComboBox.getSelectedItem()).isEqualTo(JavaCore.VERSION_17);
    }

    @Test
    void javaHomesOfTheOldAndTheNewVersionSchemesAreRecognized() throws IOException {
        EclipsePreferencesProvider panel = new EclipsePreferencesProvider();
        Map<String, String> stored = new HashMap<>();
        stored.put(INCLUDE_RUNNING_VM_BOOT_CLASSPATH, "false");
        panel.loadPreferences(stored);

        // the path is set first: choosing a version is limited by the JRE that the path designates
        panel.jreSystemLibraryPathTextField.setText(javaHome("jdk-8", "1.8.0_402").toString());
        panel.sourceComboBox.setSelectedItem(JavaCore.latestSupportedJavaVersion());
        assertThat(panel.sourceComboBox.getSelectedItem()).isEqualTo(JavaCore.VERSION_1_8);

        panel.jreSystemLibraryPathTextField.setText(javaHome("jdk-21ea", "21-ea").toString());
        panel.sourceComboBox.setSelectedItem(JavaCore.latestSupportedJavaVersion());
        assertThat(panel.sourceComboBox.getSelectedItem()).isEqualTo(JavaCore.VERSION_21);

        panel.jreSystemLibraryPathTextField.setText(javaHome("jdk-unknown", "banana").toString()); // unknown: no limit
        panel.sourceComboBox.setSelectedItem(JavaCore.latestSupportedJavaVersion());
        assertThat(panel.sourceComboBox.getSelectedItem()).isEqualTo(JavaCore.latestSupportedJavaVersion());
    }

    @Test
    void changesAreReportedToEveryListenerAndANullListenerIsIgnored() {
        EclipsePreferencesProvider panel = new EclipsePreferencesProvider();
        List<PreferencesPanel> first = new ArrayList<>();
        List<PreferencesPanel> second = new ArrayList<>();
        panel.addPreferencesChangeListener(first::add);
        panel.addPreferencesChangeListener(second::add);
        panel.addPreferencesChangeListener(null);

        panel.showCompilerErrorsCheckBox.doClick();
        panel.includeRunningVMBootClasspathCheckBox.doClick();
        panel.jreSystemLibraryPathTextField.setText("/somewhere");
        panel.sourceComboBox.setSelectedItem(JavaCore.VERSION_11);

        assertThat(first.size()).isGreaterThanOrEqualTo(4);
        assertThat(second).hasSameSizeAs(first);
        assertThat(first).isNotEmpty().allSatisfy(source -> assertThat(source).isSameAs(panel));
    }

    @Test
    void restoreDefaultsPutsEverythingBack() {
        EclipsePreferencesProvider panel = new EclipsePreferencesProvider();
        List<PreferencesPanel> notified = new ArrayList<>();
        panel.addPreferencesChangeListener(notified::add);
        panel.showCompilerWarningsCheckBox.setSelected(true);
        panel.includeRunningVMBootClasspathCheckBox.setSelected(false);
        panel.jreSystemLibraryPathTextField.setText("/somewhere");
        panel.sourceComboBox.setSelectedItem(JavaCore.VERSION_11);
        notified.clear();

        panel.restoreDefaults();

        assertThat(panel.showCompilerWarningsCheckBox.isSelected()).isFalse();
        assertThat(panel.includeRunningVMBootClasspathCheckBox.isSelected()).isTrue();
        assertThat(panel.jreSystemLibraryPathTextField.getText()).isEmpty();
        assertThat(panel.sourceComboBox.getSelectedItem()).isEqualTo(defaultVersion());
        assertThat(notified).isNotEmpty();
    }

    @Test
    void aBlankPathIsSavedAsEmptyAndAMissingOneAsItIs() {
        EclipsePreferencesProvider panel = new EclipsePreferencesProvider();
        Map<String, String> saved = new HashMap<>();

        panel.jreSystemLibraryPathTextField.setText("   ");
        panel.savePreferences(saved);
        assertThat(saved).containsEntry(JRE_SYSTEM_LIBRARY_PATH, "");

        panel.jreSystemLibraryPathTextField.setText("/does/not/exist ");
        panel.savePreferences(saved);
        assertThat(saved).containsEntry(JRE_SYSTEM_LIBRARY_PATH, "/does/not/exist");
    }
}
