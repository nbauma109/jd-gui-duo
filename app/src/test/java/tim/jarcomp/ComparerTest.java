/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package tim.jarcomp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** The entry-by-entry comparison of two archives. */
class ComparerTest {

    @TempDir
    Path dir;

    private File zip(String name, Map<String, String> entries) throws IOException {
        Path file = dir.resolve(name);
        try (OutputStream out = Files.newOutputStream(file); ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                if (!entry.getKey().endsWith("/")) {
                    zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                }
                zip.closeEntry();
            }
        }
        return file.toFile();
    }

    private static Map<String, EntryDetails.EntryStatus> statuses(CompareResults results) {
        return results.getEntryList().stream().collect(Collectors.toMap(EntryDetails::getName, EntryDetails::getStatus, (a, b) -> a, LinkedHashMap::new));
    }

    @Test
    void everyKindOfDifferenceIsReported() throws IOException {
        Map<String, String> left = new LinkedHashMap<>();
        left.put("same.txt", "identical");
        left.put("longer.txt", "short");
        left.put("sameSize.txt", "aaaa");
        left.put("removed.txt", "only on the left");
        Map<String, String> right = new LinkedHashMap<>();
        right.put("same.txt", "identical");
        right.put("longer.txt", "a much longer content");
        right.put("sameSize.txt", "bbbb");
        right.put("added.txt", "only on the right");

        CompareResults results = Comparer.compare(zip("left.zip", left), zip("right.zip", right));

        assertThat(statuses(results)).containsExactlyInAnyOrderEntriesOf(Map.of(
                "same.txt", EntryDetails.EntryStatus.EQUAL,
                "longer.txt", EntryDetails.EntryStatus.CHANGED_SIZE,
                "sameSize.txt", EntryDetails.EntryStatus.CHANGED_SUM,
                "removed.txt", EntryDetails.EntryStatus.REMOVED,
                "added.txt", EntryDetails.EntryStatus.ADDED));
        assertThat(results.getNumFiles(0)).isEqualTo(4);
        assertThat(results.getNumFiles(1)).isEqualTo(4);
        assertThat(results.getEntriesDifferent()).isTrue();
        assertThat(results.isEntriesCRCChecked()).isTrue();
    }

    @Test
    void theSizeChangeOfAnEntryIsTheDifferenceOfItsSizes() throws IOException {
        CompareResults results = Comparer.compare(zip("a.zip", Map.of("grow.txt", "12345")), zip("b.zip", Map.of("grow.txt", "1234567890")));

        EntryDetails entry = results.getEntryList().get(0);
        assertThat(entry.getSize(0)).isEqualTo(5);
        assertThat(entry.getSize(1)).isEqualTo(10);
        assertThat(entry.getSizeDifference()).isEqualTo(5);
        assertThat(entry.isChanged()).isTrue();
    }

    @Test
    void identicalArchivesHaveNoDifference() throws IOException {
        Map<String, String> content = new LinkedHashMap<>();
        content.put("a.txt", "alpha");
        content.put("b.txt", "beta");

        CompareResults results = Comparer.compare(zip("one.zip", content), zip("two.zip", content));

        assertThat(statuses(results).values()).containsOnly(EntryDetails.EntryStatus.EQUAL);
        assertThat(results.getEntriesDifferent()).isFalse();
        assertThat(results.isEntriesCRCChecked()).isTrue();
        assertThat(results.getEntryList()).noneMatch(EntryDetails::isChanged);
    }

    @Test
    void foldersAndInnerClassesAreNotListed() throws IOException {
        Map<String, String> content = new LinkedHashMap<>();
        content.put("pkg/", "");
        content.put("pkg/Outer.class", "outer");
        content.put("pkg/Outer$Inner.class", "inner");

        CompareResults results = Comparer.compare(zip("c.zip", content), zip("d.zip", content));

        assertThat(statuses(results)).containsOnlyKeys("pkg/Outer.class");
        assertThat(results.getNumFiles(0)).isEqualTo(3); // every entry counts, listed or not
    }

    @Test
    void aFileThatIsNotAnArchiveHasNoEntries() throws IOException {
        File notAnArchive = Files.writeString(dir.resolve("plain.zip"), "this is not a zip file").toFile();

        CompareResults results = Comparer.compare(notAnArchive, zip("e.zip", Map.of("x.txt", "x")));

        assertThat(results.getNumFiles(0)).isZero();
        assertThat(results.getNumFiles(1)).isEqualTo(1);
        assertThat(statuses(results)).containsExactlyEntriesOf(Map.of("x.txt", EntryDetails.EntryStatus.ADDED));
        assertThat(results.getSize(0)).isEqualTo(notAnArchive.length());
    }
}
