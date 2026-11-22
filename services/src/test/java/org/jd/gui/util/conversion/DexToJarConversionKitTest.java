/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.util.conversion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A dex is converted back to class files, once per file. */
class DexToJarConversionKitTest {

    @TempDir
    Path dir;

    private Path dex;

    @BeforeEach
    void buildADex() throws IOException {
        dex = dir.resolve("tiny.dex");
        Files.write(dex, TinyDex.bytes());
    }

    @Test
    void theClassesOfADexAreGivenByTheirPath() throws IOException {
        Map<String, byte[]> classes = DexToJarConversionKit.getOrCreate(dex.toFile());

        assertThat(classes).containsOnlyKeys("demo/Tiny.class", "demo/Tiny$Inner.class");
        // a class file starts with its magic number
        assertThat(classes.get("demo/Tiny.class")).startsWith((byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE);
    }

    @Test
    void theSameFileIsConvertedOnceAndTheResultCannotBeChanged() throws IOException {
        Map<String, byte[]> first = DexToJarConversionKit.getOrCreate(dex.toFile());
        Map<String, byte[]> second = DexToJarConversionKit.getOrCreate(dex.toFile());

        assertThat(second).isSameAs(first);
        assertThatThrownBy(() -> first.put("x.class", new byte[0])).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void aChangedFileIsConvertedAgain() throws Exception {
        Path copy = dir.resolve("copy.dex");
        Files.copy(dex, copy);
        Map<String, byte[]> before = DexToJarConversionKit.getOrCreate(copy.toFile());

        Files.write(copy, Files.readAllBytes(dex), java.nio.file.StandardOpenOption.APPEND);
        copy.toFile().setLastModified(System.currentTimeMillis() + 5_000);
        Map<String, byte[]> after = DexToJarConversionKit.getOrCreate(copy.toFile());

        assertThat(after).isNotSameAs(before).containsOnlyKeys(before.keySet());
    }

    @Test
    void aFileThatIsNotADexIsAnError() throws IOException {
        Path garbage = dir.resolve("garbage.dex");
        Files.write(garbage, "this is not a dex file".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> DexToJarConversionKit.getOrCreate(garbage.toFile())).isInstanceOf(Exception.class);
    }
}
