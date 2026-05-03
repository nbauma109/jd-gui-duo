/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.util.parser.jdt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ASTParserFactoryTest {

    @TempDir
    Path tempDir;

    @Test
    void getJDKClasspath_normalizesChildDirectorySelectionToJavaHome() throws Exception {
        Path javaHome = Files.createDirectories(tempDir.resolve("jdk-21"));
        Path libDirectory = Files.createDirectories(javaHome.resolve("lib"));
        Path jrtFsJar = Files.writeString(libDirectory.resolve("jrt-fs.jar"), "", StandardCharsets.UTF_8);
        Files.writeString(javaHome.resolve("release"), "JAVA_VERSION=\"21.0.2\"\n", StandardCharsets.UTF_8);

        List<String> classpath = ASTParserFactory.getJDKClasspath(libDirectory.toString());

        assertEquals(List.of(jrtFsJar.toString()), classpath);
    }
}
