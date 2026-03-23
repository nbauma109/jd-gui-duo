package org.jd.gui.util.archive;

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class ArchiveExtraction {

    public static final String[] EXTENSIONS = { "tar.gz", "tar.bz2", "tar.xz", "7z" };
    public static final String DESCRIPTION = "Tar and 7z archives (*.tar.gz, *.tar.bz2, *.tar.xz, *.7z)";

    private static final Set<Path> TEMP_DIRECTORIES = new LinkedHashSet<>();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(ArchiveExtraction::cleanupTemporaryDirectories, "jd-gui-archive-cleanup"));
    }

    private ArchiveExtraction() {
    }

    public static boolean hasSupportedExtension(String fileName) {
        if (fileName == null) {
            return false;
        }
        String lowerCaseName = fileName.toLowerCase(Locale.ROOT);
        for (String extension : EXTENSIONS) {
            if (lowerCaseName.endsWith("." + extension)) {
                return true;
            }
        }
        return false;
    }

    public static Path extractToTemporaryDirectory(Path archivePath) throws IOException {
        Path tempDirectory = Files.createTempDirectory("jd-gui-archive-");
        synchronized (TEMP_DIRECTORIES) {
            TEMP_DIRECTORIES.add(tempDirectory);
        }

        String lowerCaseName = archivePath.getFileName().toString().toLowerCase(Locale.ROOT);
        if (lowerCaseName.endsWith(".tar.gz")) {
            extractTarArchive(Files.newInputStream(archivePath), tempDirectory, CompressionType.GZIP);
        } else if (lowerCaseName.endsWith(".tar.bz2")) {
            extractTarArchive(Files.newInputStream(archivePath), tempDirectory, CompressionType.BZIP2);
        } else if (lowerCaseName.endsWith(".tar.xz")) {
            extractTarArchive(Files.newInputStream(archivePath), tempDirectory, CompressionType.XZ);
        } else if (lowerCaseName.endsWith(".7z")) {
            extractSevenZipArchive(archivePath, tempDirectory);
        } else {
            throw new IOException("Unsupported archive type: " + archivePath.getFileName());
        }

        return tempDirectory;
    }

    private static void extractTarArchive(InputStream fileInputStream, Path targetDirectory, CompressionType compressionType) throws IOException {
        try (InputStream archiveInputStream = switch (compressionType) {
                 case GZIP -> new GzipCompressorInputStream(fileInputStream);
                 case BZIP2 -> new BZip2CompressorInputStream(fileInputStream);
                 case XZ -> new XZCompressorInputStream(fileInputStream);
             };
             TarArchiveInputStream tarInputStream = new TarArchiveInputStream(archiveInputStream)) {
            TarArchiveEntry entry;
            while ((entry = tarInputStream.getNextEntry()) != null) {
                Path targetPath = resolveEntryPath(targetDirectory, entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(targetPath);
                } else {
                    Path parent = targetPath.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    Files.copy(tarInputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void extractSevenZipArchive(Path archivePath, Path targetDirectory) throws IOException {
        try (SevenZFile sevenZFile = new SevenZFile(archivePath.toFile())) {
            SevenZArchiveEntry entry;
            while ((entry = sevenZFile.getNextEntry()) != null) {
                Path targetPath = resolveEntryPath(targetDirectory, entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(targetPath);
                } else {
                    Path parent = targetPath.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    Files.copy(new SevenZEntryInputStream(sevenZFile), targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static Path resolveEntryPath(Path targetDirectory, String entryName) throws IOException {
        Path resolvedPath = targetDirectory.resolve(entryName).normalize();
        if (!resolvedPath.startsWith(targetDirectory)) {
            throw new IOException("Archive entry escapes target directory: " + entryName);
        }
        return resolvedPath;
    }

    private static void cleanupTemporaryDirectories() {
        synchronized (TEMP_DIRECTORIES) {
            for (Path directory : TEMP_DIRECTORIES) {
                deleteRecursively(directory);
            }
        }
    }

    private static void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try {
            if (Files.isDirectory(path)) {
                try (var stream = Files.list(path)) {
                    stream.forEach(ArchiveExtraction::deleteRecursively);
                }
            }
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            path.toFile().deleteOnExit();
        }
    }

    private enum CompressionType {
        GZIP,
        BZIP2,
        XZ
    }

    private static final class SevenZEntryInputStream extends InputStream {
        private final SevenZFile sevenZFile;

        private SevenZEntryInputStream(SevenZFile sevenZFile) {
            this.sevenZFile = sevenZFile;
        }

        @Override
        public int read() throws IOException {
            byte[] buffer = new byte[1];
            int count = read(buffer, 0, 1);
            return count == -1 ? -1 : buffer[0] & 0xFF;
        }

        @Override
        public int read(byte[] buffer, int off, int len) throws IOException {
            return sevenZFile.read(buffer, off, len);
        }
    }
}
