package org.jd.gui.util.archive;

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.apache.commons.io.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.SeekableByteChannel;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ArchiveIO {

    public static final String EXTENSION_JAR = "jar";
    public static final String EXTENSION_ZIP = "zip";
    public static final String EXTENSION_WAR = "war";
    public static final String EXTENSION_EAR = "ear";
    public static final String EXTENSION_TAR_GZ = "tar.gz";
    public static final String EXTENSION_TAR_BZ2 = "tar.bz2";
    public static final String EXTENSION_TAR_XZ = "tar.xz";
    public static final String EXTENSION_SEVEN_Z = "7z";

    public static final String[] ALL_ARCHIVE_EXTENSIONS = {
        EXTENSION_JAR,
        EXTENSION_ZIP,
        EXTENSION_WAR,
        EXTENSION_EAR,
        EXTENSION_TAR_GZ,
        EXTENSION_TAR_BZ2,
        EXTENSION_TAR_XZ,
        EXTENSION_SEVEN_Z
    };

    public static final String[] OPENABLE_ARCHIVE_EXTENSIONS = {
        EXTENSION_TAR_GZ,
        EXTENSION_TAR_BZ2,
        EXTENSION_TAR_XZ,
        EXTENSION_SEVEN_Z
    };

    public static final String OPENABLE_ARCHIVE_DESCRIPTION =
        "Tar and 7z archives (*.tar.gz, *.tar.bz2, *.tar.xz, *.7z)";
    public static final String ALL_ARCHIVE_DESCRIPTION =
        "Jar, Zip, War, Ear, Tar.gz, Tar.bz2, Tar.xz, and 7z archives";
    public static final String ALL_ARCHIVE_LABEL =
        ".jar, .zip, .war, .ear, .tar.gz, .tar.bz2, .tar.xz, or .7z";

    private static final String ENTRY_SEPARATOR = "/";
    private static final String EXTENSION_PREFIX = ".";
    private static final byte[] EMPTY_BYTES = new byte[0];
    private static final long UNKNOWN_COMPRESSED_LENGTH = -1L;

    private ArchiveIO() {
    }

    public static boolean hasSupportedArchiveExtension(String fileName) {
        return hasSupportedExtension(fileName, ALL_ARCHIVE_EXTENSIONS);
    }

    public static boolean hasSupportedOpenArchiveExtension(String fileName) {
        return hasSupportedExtension(fileName, OPENABLE_ARCHIVE_EXTENSIONS);
    }

    public static ArchiveSnapshot readArchive(File file) throws IOException {
        return readArchive(new FileSource(file));
    }

    public static ArchiveSnapshot readArchive(String fileName, byte[] bytes) throws IOException {
        return readArchive(new MemorySource(fileName, bytes));
    }

    private static ArchiveSnapshot readArchive(ArchiveSource source) throws IOException {
        ArchiveFormat format = ArchiveFormat.fromFileName(source.fileName());
        return switch (format.kind()) {
            case ZIP -> readZipArchive(source);
            case TAR_GZ -> readTarArchive(source, CompressionType.GZIP);
            case TAR_BZ2 -> readTarArchive(source, CompressionType.BZIP2);
            case TAR_XZ -> readTarArchive(source, CompressionType.XZ);
            case SEVEN_Z -> readSevenZipArchive(source);
        };
    }

    private static boolean hasSupportedExtension(String fileName, String[] extensions) {
        if (fileName == null) {
            return false;
        }
        String lowerCaseName = fileName.toLowerCase(Locale.ROOT);
        for (String extension : extensions) {
            if (lowerCaseName.endsWith(EXTENSION_PREFIX + extension)) {
                return true;
            }
        }
        return false;
    }

    private static ArchiveSnapshot readZipArchive(ArchiveSource source) throws IOException {
        Map<String, ArchiveItem> entries = new LinkedHashMap<>();
        int entryCount = 0;

        try (ZipInputStream zipInputStream = new ZipInputStream(source.openStream())) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                entryCount++;
                String entryName = normalizeEntryName(entry.getName(), entry.isDirectory());
                if (!entryName.isEmpty()) {
                    byte[] bytes = entry.isDirectory() ? EMPTY_BYTES : IOUtils.toByteArray(zipInputStream);
                    entries.put(entryName, new ArchiveItem(entry.isDirectory(), bytes, compressedLength(entry.getSize(), bytes.length)));
                }
            }
        }

        return new ArchiveSnapshot(entryCount, entries);
    }

    private static ArchiveSnapshot readTarArchive(ArchiveSource source, CompressionType compressionType) throws IOException {
        Map<String, ArchiveItem> entries = new LinkedHashMap<>();
        int entryCount = 0;

        try (InputStream fileInputStream = source.openStream();
             InputStream archiveInputStream = wrapCompressedInputStream(fileInputStream, compressionType);
             TarArchiveInputStream tarInputStream = new TarArchiveInputStream(archiveInputStream)) {
            TarArchiveEntry entry;
            while ((entry = tarInputStream.getNextEntry()) != null) {
                entryCount++;
                String entryName = normalizeEntryName(entry.getName(), entry.isDirectory());
                if (!entryName.isEmpty()) {
                    byte[] bytes = entry.isDirectory() ? EMPTY_BYTES : IOUtils.toByteArray(tarInputStream);
                    entries.put(entryName, new ArchiveItem(entry.isDirectory(), bytes, compressedLength(entry.getSize(), bytes.length)));
                }
            }
        }

        return new ArchiveSnapshot(entryCount, entries);
    }

    private static ArchiveSnapshot readSevenZipArchive(ArchiveSource source) throws IOException {
        Map<String, ArchiveItem> entries = new LinkedHashMap<>();
        int entryCount = 0;

        try (SevenZFile sevenZFile = openSevenZipFile(source)) {
            SevenZArchiveEntry entry;
            while ((entry = sevenZFile.getNextEntry()) != null) {
                entryCount++;
                String entryName = normalizeEntryName(entry.getName(), entry.isDirectory());
                if (!entryName.isEmpty()) {
                    byte[] bytes;
                    if (entry.isDirectory()) {
                        bytes = EMPTY_BYTES;
                    } else {
                        try (InputStream inputStream = sevenZFile.getInputStream(entry)) {
                            bytes = IOUtils.toByteArray(inputStream);
                        }
                    }
                    entries.put(entryName, new ArchiveItem(entry.isDirectory(), bytes, compressedLength(entry.getSize(), bytes.length)));
                }
            }
        }

        return new ArchiveSnapshot(entryCount, entries);
    }

    private static SevenZFile openSevenZipFile(ArchiveSource source) throws IOException {
        if (source instanceof FileSource fileSource) {
            return new SevenZFile(fileSource.file());
        }
        MemorySource memorySource = (MemorySource) source;
        SeekableByteChannel byteChannel = new SeekableInMemoryByteChannel(memorySource.bytes());
        return new SevenZFile(byteChannel, memorySource.fileName());
    }

    private static InputStream wrapCompressedInputStream(InputStream inputStream, CompressionType compressionType) throws IOException {
        return switch (compressionType) {
            case GZIP -> new GzipCompressorInputStream(inputStream);
            case BZIP2 -> new BZip2CompressorInputStream(inputStream);
            case XZ -> new XZCompressorInputStream(inputStream);
        };
    }

    private static long compressedLength(long archiveLength, int defaultLength) {
        return archiveLength == UNKNOWN_COMPRESSED_LENGTH ? defaultLength : archiveLength;
    }

    private static String normalizeEntryName(String entryName, boolean directory) {
        String normalized = entryName.replace('\\', '/');

        while (normalized.startsWith(ENTRY_SEPARATOR)) {
            normalized = normalized.substring(1);
        }

        while (directory && normalized.endsWith(ENTRY_SEPARATOR)) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        return normalized;
    }

    public record ArchiveSnapshot(int entryCount, Map<String, ArchiveItem> entries) {
    }

    public record ArchiveItem(boolean directory, byte[] bytes, long compressedLength) {
        public long length() {
            return bytes.length;
        }
    }

    private sealed interface ArchiveSource permits FileSource, MemorySource {
        String fileName();

        InputStream openStream() throws IOException;
    }

    private record FileSource(File file) implements ArchiveSource {
        @Override
        public String fileName() {
            return file.getName();
        }

        @Override
        public InputStream openStream() throws IOException {
            return java.nio.file.Files.newInputStream(file.toPath());
        }
    }

    private record MemorySource(String fileName, byte[] bytes) implements ArchiveSource {
        @Override
        public InputStream openStream() {
            return new ByteArrayInputStream(bytes);
        }
    }

    private enum CompressionType {
        GZIP,
        BZIP2,
        XZ
    }

    private enum ArchiveKind {
        ZIP,
        TAR_GZ,
        TAR_BZ2,
        TAR_XZ,
        SEVEN_Z
    }

    private enum ArchiveFormat {
        JAR(EXTENSION_JAR, ArchiveKind.ZIP),
        ZIP(EXTENSION_ZIP, ArchiveKind.ZIP),
        WAR(EXTENSION_WAR, ArchiveKind.ZIP),
        EAR(EXTENSION_EAR, ArchiveKind.ZIP),
        TAR_GZ(EXTENSION_TAR_GZ, ArchiveKind.TAR_GZ),
        TAR_BZ2(EXTENSION_TAR_BZ2, ArchiveKind.TAR_BZ2),
        TAR_XZ(EXTENSION_TAR_XZ, ArchiveKind.TAR_XZ),
        SEVEN_Z(EXTENSION_SEVEN_Z, ArchiveKind.SEVEN_Z);

        private final String extension;
        private final ArchiveKind kind;

        ArchiveFormat(String extension, ArchiveKind kind) {
            this.extension = extension;
            this.kind = kind;
        }

        ArchiveKind kind() {
            return kind;
        }

        static ArchiveFormat fromFileName(String fileName) throws IOException {
            String lowerCaseName = fileName.toLowerCase(Locale.ROOT);
            ArchiveFormat matchedFormat = null;
            int matchedLength = -1;

            for (ArchiveFormat format : values()) {
                if (lowerCaseName.endsWith(EXTENSION_PREFIX + format.extension) && format.extension.length() > matchedLength) {
                    matchedFormat = format;
                    matchedLength = format.extension.length();
                }
            }

            if (matchedFormat == null) {
                throw new IOException("Unsupported archive type: " + fileName);
            }

            return matchedFormat;
        }
    }
}
