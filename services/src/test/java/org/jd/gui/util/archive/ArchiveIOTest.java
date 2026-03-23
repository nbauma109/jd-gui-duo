package org.jd.gui.util.archive;

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
}
