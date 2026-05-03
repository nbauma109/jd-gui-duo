/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.preferencespanel;

import org.eclipse.jdt.core.JavaCore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.jd.gui.util.decompiler.GuiPreferences.INCLUDE_RUNNING_VM_BOOT_CLASSPATH;
import static org.jd.gui.util.decompiler.GuiPreferences.JRE_SYSTEM_LIBRARY_PATH;
import static org.junit.jupiter.api.Assertions.assertEquals;

class EclipsePreferencesProviderTest {

    @TempDir
    Path tempDir;

    @Test
    void savePreferences_normalizesChildDirectoryToJavaHome() throws Exception {
        Path javaHome = createJavaHome("jdk-21");
        EclipsePreferencesProvider provider = new EclipsePreferencesProvider();
        provider.jreSystemLibraryPathTextField.setText(javaHome.resolve("lib").toString());

        Map<String, String> preferences = new HashMap<>();
        provider.savePreferences(preferences);

        assertEquals(javaHome.toString(), preferences.get(JRE_SYSTEM_LIBRARY_PATH));
    }

    @Test
    void loadPreferences_normalizesStoredChildDirectoryToJavaHome() throws Exception {
        Path javaHome = createJavaHome("jdk-17");
        EclipsePreferencesProvider provider = new EclipsePreferencesProvider();
        Map<String, String> preferences = new HashMap<>();
        preferences.put(INCLUDE_RUNNING_VM_BOOT_CLASSPATH, "false");
        preferences.put(JRE_SYSTEM_LIBRARY_PATH, javaHome.resolve("lib").toString());
        preferences.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_17);
        preferences.put(JavaCore.COMPILER_COMPLIANCE, JavaCore.VERSION_17);

        provider.loadPreferences(preferences);

        assertEquals(javaHome.toString(), provider.jreSystemLibraryPathTextField.getText());
    }

    private Path createJavaHome(String directoryName) throws Exception {
        Path javaHome = Files.createDirectories(tempDir.resolve(directoryName));
        Files.createDirectories(javaHome.resolve("lib"));
        Files.writeString(javaHome.resolve("release"), "JAVA_VERSION=\"17.0.10\"\n", StandardCharsets.UTF_8);
        return javaHome;
    }
}
