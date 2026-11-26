/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;

import static org.assertj.core.api.Assertions.assertThat;

class TempFileTest {

    @Test
    void aTemporaryFileDoesNotExistYetAndIsInADirectoryOfItsOwn() throws IOException {
        try (TempFile first = new TempFile(".zip"); TempFile second = new TempFile(".zip")) {
            assertThat(first).doesNotExist();
            assertThat(first.getName()).startsWith(TempFile.TMP_FILE_PREFIX).endsWith(".zip");
            assertThat(first.getParentFile()).isDirectory().isEmptyDirectory().isNotEqualTo(second.getParentFile());
            if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
                assertThat(Files.getPosixFilePermissions(first.getParentFile().toPath())).containsExactlyInAnyOrder(
                        PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE);
            }
        }
    }

    /** The suffix may hold the name of a class and of a method, longer than a file system accepts. */
    @ParameterizedTest
    @ValueSource(strings = {"a", "é", "\uD83D\uDE00"})
    void aLongSuffixKeepsItsEndWithinTheLimitOfAFileName(String character) throws IOException {
        String suffix = "." + character.repeat(300) + ".method.svg";

        try (TempFile file = new TempFile(suffix)) {
            Files.writeString(file.toPath(), "content");

            assertThat(file.getName()).startsWith(TempFile.TMP_FILE_PREFIX + character).endsWith(".method.svg");
            assertThat(file.getName().getBytes(StandardCharsets.UTF_8).length).isBetween(150, 161);
        }
    }
}
