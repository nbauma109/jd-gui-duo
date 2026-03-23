package tim.jarcomp;

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComparerArchiveSupportTest {

    @TempDir
    Path tempDir;

    @Test
    void compareDetectsChangedSumInsideTarXz() throws IOException {
        Path leftArchive = tempDir.resolve("left.tar.xz");
        Path rightArchive = tempDir.resolve("right.tar.xz");

        writeTarXzArchive(leftArchive, "sample.txt", "abc");
        writeTarXzArchive(rightArchive, "sample.txt", "abd");

        CompareResults results = Comparer.compare(leftArchive.toFile(), rightArchive.toFile());

        assertEquals(1, results.getEntryList().size());
        assertEquals(EntryDetails.EntryStatus.CHANGED_SUM, results.getEntryList().getFirst().getStatus());
        assertTrue(results.getEntriesDifferent());
        assertTrue(results.isEntriesCRCChecked());
    }

    @Test
    void compareReadsSevenZipArchives() throws IOException {
        Path leftArchive = tempDir.resolve("left.7z");
        Path rightArchive = tempDir.resolve("right.7z");

        writeSevenZipArchive(leftArchive, "pkg/Alpha.class", "left");
        writeSevenZipArchive(rightArchive, "pkg/Alpha.class", "rght");

        CompareResults results = Comparer.compare(leftArchive.toFile(), rightArchive.toFile());

        assertEquals(1, results.getEntryList().size());
        assertEquals(EntryDetails.EntryStatus.CHANGED_SUM, results.getEntryList().getFirst().getStatus());
        assertTrue(results.getEntriesDifferent());
        assertTrue(results.isEntriesCRCChecked());
        assertTrue(ArchiveSupport.isArchiveFile(leftArchive.toFile()));
        assertTrue(new GenericFileFilter(ArchiveSupport.SUPPORTED_ARCHIVE_DESCRIPTION, ArchiveSupport.SUPPORTED_ARCHIVE_SUFFIXES)
            .accept(leftArchive.toFile()));
    }

    @Test
    void genericFileFilterAcceptsMultipartArchiveSuffixes() {
        GenericFileFilter filter = new GenericFileFilter(ArchiveSupport.SUPPORTED_ARCHIVE_DESCRIPTION, ArchiveSupport.SUPPORTED_ARCHIVE_SUFFIXES);

        assertTrue(filter.acceptFilename("bundle.tar.gz"));
        assertTrue(filter.acceptFilename("bundle.tar.bz2"));
        assertTrue(filter.acceptFilename("bundle.tar.xz"));
        assertTrue(filter.acceptFilename("bundle.7z"));
    }

    private static void writeTarXzArchive(Path archive, String entryName, String contents) throws IOException {
        byte[] bytes = contents.getBytes(StandardCharsets.UTF_8);
        try (OutputStream fileOutputStream = Files.newOutputStream(archive);
             XZCompressorOutputStream xzOutputStream = new XZCompressorOutputStream(fileOutputStream);
             TarArchiveOutputStream tarOutputStream = new TarArchiveOutputStream(xzOutputStream)) {
            TarArchiveEntry entry = new TarArchiveEntry(entryName);
            entry.setSize(bytes.length);
            tarOutputStream.putArchiveEntry(entry);
            tarOutputStream.write(bytes);
            tarOutputStream.closeArchiveEntry();
            tarOutputStream.finish();
        }
    }

    private static void writeSevenZipArchive(Path archive, String entryName, String contents) throws IOException {
        byte[] bytes = contents.getBytes(StandardCharsets.UTF_8);
        Path sourceFile = Files.createTempFile(archive.getParent(), "entry", ".bin");
        Files.write(sourceFile, bytes);
        try (SevenZOutputFile sevenZOutputFile = new SevenZOutputFile(archive.toFile())) {
            SevenZArchiveEntry entry = sevenZOutputFile.createArchiveEntry(sourceFile.toFile(), entryName);
            sevenZOutputFile.putArchiveEntry(entry);
            sevenZOutputFile.write(bytes);
            sevenZOutputFile.closeArchiveEntry();
        }
    }
}
