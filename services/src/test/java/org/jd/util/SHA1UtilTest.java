/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SHA1UtilTest {

    @TempDir
    Path directory;

    @Test
    void theHashesOfAChecksumFileAreReadByFileAndOtherLinesAreIgnored() throws IOException {
        Path checksums = Files.writeString(directory.resolve("checksums.sha1"),
                "da39a3ee *lib/a.jar\n0beec7b5    *b c.jar\nno hash here\n");

        assertThat(SHA1Util.readSHA1File(checksums.toFile())).isEqualTo(Map.of(
                new File("lib/a.jar"), "da39a3ee",
                new File("b c.jar"), "0beec7b5"));
    }

    @Test
    void aChecksumFileThatIsMissingHasNoHashes() {
        assertThat(SHA1Util.readSHA1File(directory.resolve("none.sha1").toFile())).isEmpty();
    }
}
