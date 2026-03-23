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

class ArchiveExtractionTest {

    @TempDir
    Path tempDir;

    @Test
    void extractsTarXzArchives() throws IOException {
        Path archive = tempDir.resolve("sample.tar.xz");
        writeTarXzArchive(archive, "docs/readme.txt", "hello tar");

        Path extractedRoot = ArchiveExtraction.extractToTemporaryDirectory(archive);

        assertEquals("hello tar", Files.readString(extractedRoot.resolve("docs/readme.txt")));
    }

    @Test
    void extractsSevenZipArchives() throws IOException {
        Path archive = tempDir.resolve("sample.7z");
        writeSevenZipArchive(archive, "pkg/data.txt", "hello 7z");

        Path extractedRoot = ArchiveExtraction.extractToTemporaryDirectory(archive);

        assertEquals("hello 7z", Files.readString(extractedRoot.resolve("pkg/data.txt")));
    }

    @Test
    void recognizesMultipartArchiveExtensions() {
        assertTrue(ArchiveExtraction.hasSupportedExtension("sample.tar.gz"));
        assertTrue(ArchiveExtraction.hasSupportedExtension("sample.tar.bz2"));
        assertTrue(ArchiveExtraction.hasSupportedExtension("sample.tar.xz"));
        assertTrue(ArchiveExtraction.hasSupportedExtension("sample.7z"));
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
