package org.jd.gui.util.archive;

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchiveIOTest {

    @TempDir
    Path tempDir;

    @Test
    void readsTarXzArchivesFromFile() throws IOException {
        Path archive = tempDir.resolve("sample.tar.xz");
        writeTarXzArchive(archive, "docs/readme.txt", "hello tar");

        ArchiveIO.ArchiveSnapshot snapshot = ArchiveIO.readArchive(archive.toFile());

        assertEquals(1, snapshot.entries().size());
        assertEquals("hello tar", new String(snapshot.entries().get("docs/readme.txt").bytes(), StandardCharsets.UTF_8));
    }

    @Test
    void readsSevenZipArchivesFromMemory() throws IOException {
        Path archive = tempDir.resolve("sample.7z");
        writeSevenZipArchive(archive, "pkg/data.txt", "hello 7z");

        ArchiveIO.ArchiveSnapshot snapshot = ArchiveIO.readArchive("sample.7z", Files.readAllBytes(archive));

        assertEquals(1, snapshot.entries().size());
        assertEquals("hello 7z", new String(snapshot.entries().get("pkg/data.txt").bytes(), StandardCharsets.UTF_8));
    }

    @Test
    void recognizesSupportedArchiveExtensions() {
        assertTrue(ArchiveIO.hasSupportedArchiveExtension("sample.jar"));
        assertTrue(ArchiveIO.hasSupportedArchiveExtension("sample.tar.gz"));
        assertTrue(ArchiveIO.hasSupportedArchiveExtension("sample.tar.bz2"));
        assertTrue(ArchiveIO.hasSupportedArchiveExtension("sample.tar.xz"));
        assertTrue(ArchiveIO.hasSupportedArchiveExtension("sample.7z"));
        assertTrue(ArchiveIO.hasSupportedOpenArchiveExtension("sample.tar.xz"));
        assertTrue(ArchiveIO.hasSupportedOpenArchiveExtension("sample.7z"));
    }

    @Test
    void rejectsZipEntriesWithSuspiciousCompressionRatio() throws IOException {
        byte[] archiveBytes = writeZipArchive("bomb.txt", "0".repeat(8 * 1024));

        IOException exception = assertThrows(
            IOException.class,
            () -> ArchiveIO.readArchive(
                "sample.zip",
                archiveBytes,
                new ArchiveIO.ArchiveReadLimits(10, 64 * 1024, 64 * 1024, 5.0d)
            )
        );

        assertTrue(exception.getMessage().contains("maximum compression ratio"));
    }

    @Test
    void rejectsArchivesWithTooManyEntries() throws IOException {
        byte[] archiveBytes = writeZipArchiveWithEntryCount(3);

        IOException exception = assertThrows(
            IOException.class,
            () -> ArchiveIO.readArchive(
                "sample.zip",
                archiveBytes,
                new ArchiveIO.ArchiveReadLimits(2, 64 * 1024, 64 * 1024, 100.0d)
            )
        );

        assertTrue(exception.getMessage().contains("too many entries"));
    }

    @Test
    void rejectsTarXzArchivesThatExceedTotalUncompressedSize() throws IOException {
        Path archive = tempDir.resolve("oversized.tar.xz");
        writeTarXzArchive(archive, "docs/readme.txt", "abcdefghijk");

        IOException exception = assertThrows(
            IOException.class,
            () -> ArchiveIO.readArchive(
                "oversized.tar.xz",
                Files.readAllBytes(archive),
                new ArchiveIO.ArchiveReadLimits(10, 10, 64 * 1024, 100.0d)
            )
        );

        assertTrue(exception.getMessage().contains("maximum uncompressed size"));
    }

    @Test
    void zipEntryReportsRealCompressedLength() throws IOException {
        // Highly repetitive content that deflate will reduce significantly
        int contentSize = 1000;
        byte[] archiveBytes = writeZipArchive("data.txt", "A".repeat(contentSize));

        ArchiveIO.ArchiveSnapshot snapshot = ArchiveIO.readArchive("sample.zip", archiveBytes);

        ArchiveIO.ArchiveItem item = snapshot.entries().get("data.txt");
        // compressedLength() must report the real compressed size, not the uncompressed size
        assertTrue(item.compressedLength() > 0, "compressedLength() must be positive");
        assertTrue(item.compressedLength() < item.length(), "compressedLength() must be less than uncompressed length for highly repetitive content");
    }

    @Test
    void tarEntryFallsBackToUncompressedLengthWhenCompressedSizeUnknown() throws IOException {
        Path archive = tempDir.resolve("sample.tar.xz");
        writeTarXzArchive(archive, "docs/readme.txt", "hello tar");

        ArchiveIO.ArchiveSnapshot snapshot = ArchiveIO.readArchive(archive.toFile());

        ArchiveIO.ArchiveItem item = snapshot.entries().get("docs/readme.txt");
        // TAR has no per-entry compressed size; compressedLength() must fall back to length()
        assertEquals(item.length(), item.compressedLength());
    }

    @Test
    void sevenZipEntryFallsBackToUncompressedLengthWhenCompressedSizeUnknown() throws IOException {
        Path archive = tempDir.resolve("sample.7z");
        writeSevenZipArchive(archive, "pkg/data.txt", "hello 7z");

        ArchiveIO.ArchiveSnapshot snapshot = ArchiveIO.readArchive("sample.7z", Files.readAllBytes(archive));

        ArchiveIO.ArchiveItem item = snapshot.entries().get("pkg/data.txt");
        // 7z has no per-entry compressed size; compressedLength() must fall back to length()
        assertEquals(item.length(), item.compressedLength());
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
        try (SevenZOutputFile sevenZOutputFile = new SevenZOutputFile(archive.toFile())) {
            SevenZArchiveEntry entry = new SevenZArchiveEntry();
            entry.setName(entryName);
            entry.setSize(bytes.length);
            sevenZOutputFile.putArchiveEntry(entry);
            sevenZOutputFile.write(bytes);
            sevenZOutputFile.closeArchiveEntry();
        }
    }

    private static byte[] writeZipArchive(String entryName, String contents) throws IOException {
        return writeZipArchive(entryName, contents.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] writeZipArchive(String entryName, byte[] bytes) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            ZipEntry entry = new ZipEntry(entryName);
            zipOutputStream.putNextEntry(entry);
            zipOutputStream.write(bytes);
            zipOutputStream.closeEntry();
        }
        return outputStream.toByteArray();
    }

    private static byte[] writeZipArchiveWithEntryCount(int count) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            for (int i = 0; i < count; i++) {
                ZipEntry entry = new ZipEntry("entry-" + i + ".txt");
                zipOutputStream.putNextEntry(entry);
                zipOutputStream.write('a');
                zipOutputStream.closeEntry();
            }
        }
        return outputStream.toByteArray();
    }
}
