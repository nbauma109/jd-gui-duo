package tim.jarcomp;

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.io.IOUtils;
import org.jd.core.v1.api.loader.Loader;
import org.jd.core.v1.util.StringConstants;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class ArchiveSupport {

    static final String[] SUPPORTED_ARCHIVE_SUFFIXES = {
        "jar", "zip", "war", "ear", "tar.gz", "tar.bz2", "tar.xz", "7z"
    };

    static final String SUPPORTED_ARCHIVE_DESCRIPTION =
        "Jar, Zip, War, Ear, Tar.gz, Tar.bz2, Tar.xz, and 7z archives";

    private static final int BUFFER_SIZE = 8192;

    private ArchiveSupport() {
    }

    static boolean isArchiveFile(File file) {
        return file != null && hasSupportedArchiveSuffix(file.getName());
    }

    static boolean hasSupportedArchiveSuffix(String fileName) {
        if (fileName == null) {
            return false;
        }
        String lowerCaseName = fileName.toLowerCase(Locale.ROOT);
        return Arrays.stream(SUPPORTED_ARCHIVE_SUFFIXES)
            .map(suffix -> "." + suffix)
            .anyMatch(lowerCaseName::endsWith);
    }

    static String getSupportedArchiveLabel() {
        return ".jar, .zip, .war, .ear, .tar.gz, .tar.bz2, .tar.xz, or .7z";
    }

    static ArchiveSnapshot readSnapshot(File file) throws IOException {
        Map<String, ArchiveEntryData> entries = new LinkedHashMap<>();
        Counter counter = new Counter();
        visitEntries(file, (name, directory, bytes) -> {
            counter.increment();
            if (directory || name.contains("$")) {
                return;
            }
            entries.put(name, new ArchiveEntryData(bytes.length, computeCRC32(bytes)));
        });
        return new ArchiveSnapshot(counter.value(), entries);
    }

    static byte[] readEntryBytes(File file, String entryPath) throws IOException {
        Objects.requireNonNull(entryPath, "entryPath");
        EntryBytesHolder holder = new EntryBytesHolder();
        visitEntries(file, (name, directory, bytes) -> {
            if (!directory && entryPath.equals(name)) {
                holder.set(bytes);
            }
        });
        if (holder.value == null) {
            throw new IOException("Entry not found in archive: " + entryPath);
        }
        return holder.value;
    }

    static Loader createClassLoader(File file) throws IOException {
        Map<String, byte[]> classEntries = new LinkedHashMap<>();
        visitEntries(file, (name, directory, bytes) -> {
            if (!directory && name.endsWith(StringConstants.CLASS_FILE_SUFFIX)) {
                classEntries.put(name, bytes);
            }
        });
        return new Loader() {
            @Override
            public boolean canLoad(String internalPath) {
                return classEntries.containsKey(toClassEntryPath(internalPath));
            }

            @Override
            public byte[] load(String internalName) {
                return classEntries.get(toClassEntryPath(internalName));
            }
        };
    }

    private static String toClassEntryPath(String internalPath) {
        if (internalPath.endsWith(StringConstants.CLASS_FILE_SUFFIX)) {
            return internalPath;
        }
        return internalPath + StringConstants.CLASS_FILE_SUFFIX;
    }

    private static long computeCRC32(byte[] bytes) {
        CRC32 crc32 = new CRC32();
        crc32.update(bytes);
        return crc32.getValue();
    }

    private static void visitEntries(File file, ArchiveEntryVisitor visitor) throws IOException {
        ArchiveType archiveType = detectArchiveType(file);
        switch (archiveType) {
            case ZIP -> visitZipEntries(file, visitor);
            case TAR_GZ -> visitCompressedTarEntries(file, visitor, CompressionType.GZIP);
            case TAR_BZ2 -> visitCompressedTarEntries(file, visitor, CompressionType.BZIP2);
            case TAR_XZ -> visitCompressedTarEntries(file, visitor, CompressionType.XZ);
            case SEVEN_Z -> visitSevenZEntries(file, visitor);
            default -> throw new IOException("Unsupported archive type: " + file.getName());
        }
    }

    private static ArchiveType detectArchiveType(File file) throws IOException {
        if (file == null) {
            throw new IOException("Archive file is null");
        }
        String lowerCaseName = file.getName().toLowerCase(Locale.ROOT);
        if (lowerCaseName.endsWith(".jar")
            || lowerCaseName.endsWith(".zip")
            || lowerCaseName.endsWith(".war")
            || lowerCaseName.endsWith(".ear")) {
            return ArchiveType.ZIP;
        }
        if (lowerCaseName.endsWith(".tar.gz")) {
            return ArchiveType.TAR_GZ;
        }
        if (lowerCaseName.endsWith(".tar.bz2")) {
            return ArchiveType.TAR_BZ2;
        }
        if (lowerCaseName.endsWith(".tar.xz")) {
            return ArchiveType.TAR_XZ;
        }
        if (lowerCaseName.endsWith(".7z")) {
            return ArchiveType.SEVEN_Z;
        }
        throw new IOException("Unsupported archive type: " + file.getName());
    }

    private static void visitZipEntries(File file, ArchiveEntryVisitor visitor) throws IOException {
        try (ZipFile zipFile = new ZipFile(file)) {
            for (ZipEntry entry : java.util.Collections.list(zipFile.entries())) {
                byte[] bytes = entry.isDirectory() ? new byte[0] : readAllBytes(zipFile.getInputStream(entry));
                visitor.visit(entry.getName(), entry.isDirectory(), bytes);
            }
        }
    }

    private static void visitCompressedTarEntries(File file, ArchiveEntryVisitor visitor, CompressionType compressionType) throws IOException {
        try (InputStream fileInputStream = new FileInputStream(file);
             InputStream compressedInputStream = wrapCompressedInputStream(fileInputStream, compressionType);
             TarArchiveInputStream tarInputStream = new TarArchiveInputStream(compressedInputStream)) {
            TarArchiveEntry entry;
            while ((entry = tarInputStream.getNextEntry()) != null) {
                byte[] bytes = entry.isDirectory() ? new byte[0] : IOUtils.toByteArray(tarInputStream);
                visitor.visit(entry.getName(), entry.isDirectory(), bytes);
            }
        }
    }

    private static InputStream wrapCompressedInputStream(InputStream inputStream, CompressionType compressionType) throws IOException {
        return switch (compressionType) {
            case GZIP -> new GzipCompressorInputStream(inputStream);
            case BZIP2 -> new BZip2CompressorInputStream(inputStream);
            case XZ -> new XZCompressorInputStream(inputStream);
        };
    }

    private static void visitSevenZEntries(File file, ArchiveEntryVisitor visitor) throws IOException {
        try (SevenZFile sevenZFile = new SevenZFile(file)) {
            SevenZArchiveEntry entry;
            while ((entry = sevenZFile.getNextEntry()) != null) {
                byte[] bytes = entry.isDirectory() ? new byte[0] : readSevenZEntryBytes(sevenZFile, entry);
                visitor.visit(entry.getName(), entry.isDirectory(), bytes);
            }
        }
    }

    private static byte[] readSevenZEntryBytes(SevenZFile sevenZFile, SevenZArchiveEntry entry) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(initialCapacity(entry.getSize()));
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        while ((read = sevenZFile.read(buffer)) != -1) {
            outputStream.write(buffer, 0, read);
        }
        return outputStream.toByteArray();
    }

    private static int initialCapacity(long size) {
        if (size <= 0 || size > Integer.MAX_VALUE) {
            return BUFFER_SIZE;
        }
        return (int) size;
    }

    private static byte[] readAllBytes(InputStream inputStream) throws IOException {
        try (InputStream in = inputStream) {
            return IOUtils.toByteArray(in);
        }
    }

    enum ArchiveType {
        ZIP,
        TAR_GZ,
        TAR_BZ2,
        TAR_XZ,
        SEVEN_Z
    }

    private enum CompressionType {
        GZIP,
        BZIP2,
        XZ
    }

    private interface ArchiveEntryVisitor {
        void visit(String name, boolean directory, byte[] bytes) throws IOException;
    }

    static final class ArchiveSnapshot {
        private final int entryCount;
        private final Map<String, ArchiveEntryData> entries;

        ArchiveSnapshot(int entryCount, Map<String, ArchiveEntryData> entries) {
            this.entryCount = entryCount;
            this.entries = entries;
        }

        int entryCount() {
            return entryCount;
        }

        Map<String, ArchiveEntryData> entries() {
            return entries;
        }
    }

    static final class ArchiveEntryData {
        private final long size;
        private final long checksum;

        ArchiveEntryData(long size, long checksum) {
            this.size = size;
            this.checksum = checksum;
        }

        long size() {
            return size;
        }

        long checksum() {
            return checksum;
        }
    }

    private static final class Counter {
        private int value;

        void increment() {
            value++;
        }

        int value() {
            return value;
        }
    }

    private static final class EntryBytesHolder {
        private byte[] value;

        void set(byte[] value) {
            this.value = value;
        }
    }
}
