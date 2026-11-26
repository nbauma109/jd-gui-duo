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

    private static final String HASH_A = "da39a3ee5e6b4b0d3255bfef95601890afd80709";
    private static final String HASH_B = "0BEEC7B5EA3F0FDBC95D0DD47F3C5BC275DA8A33";

    private Map<File, String> read(String text) throws IOException {
        return SHA1Util.readSHA1File(Files.writeString(directory.resolve("sha1.txt"), text).toFile());
    }

    @Test
    void theHashesOfAChecksumFileAreReadByFile() throws IOException {
        // binary mode has an asterisk, text mode a second space
        assertThat(read(HASH_A + " *lib/a.jar\r\n" + HASH_B + "  b *c.jar\n")).isEqualTo(Map.of(
                new File("lib/a.jar"), HASH_A,
                new File("b *c.jar"), HASH_B));
        assertThat(read(HASH_A + " \t *a.jar\n" + HASH_B + "\tb.jar")).isEqualTo(Map.of(
                new File("a.jar"), HASH_A,
                new File("b.jar"), HASH_B));
    }

    @Test
    void theLinesWithoutAHashAndAFileAreIgnored() throws IOException {
        assertThat(read("no hash here\nabc *a.jar\n" + HASH_A + "\n" + HASH_A + " *\n" + HASH_A + "a.jar\n\n")).isEmpty();
    }

    @Test
    void aChecksumFileThatIsMissingHasNoHashes() {
        assertThat(SHA1Util.readSHA1File(directory.resolve("none.sha1").toFile())).isEmpty();
    }
}
