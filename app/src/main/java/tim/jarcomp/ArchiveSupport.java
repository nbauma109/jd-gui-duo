package tim.jarcomp;

import org.jd.core.v1.api.loader.Loader;
import org.jd.core.v1.util.StringConstants;
import org.jd.gui.util.archive.ArchiveIO;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.CRC32;

final class ArchiveSupport {

    private static final String INNER_CLASS_MARKER = "$";

    static final String[] SUPPORTED_ARCHIVE_SUFFIXES = ArchiveIO.ALL_ARCHIVE_EXTENSIONS;
    static final String SUPPORTED_ARCHIVE_DESCRIPTION = ArchiveIO.ALL_ARCHIVE_DESCRIPTION;

    private ArchiveSupport() {
    }

    static boolean isArchiveFile(File file) {
        return file != null && hasSupportedArchiveSuffix(file.getName());
    }

    static boolean hasSupportedArchiveSuffix(String fileName) {
        return ArchiveIO.hasSupportedArchiveExtension(fileName);
    }

    static String getSupportedArchiveLabel() {
        return ArchiveIO.ALL_ARCHIVE_LABEL;
    }

    static ArchiveSnapshot readSnapshot(File file) throws IOException {
        Map<String, ArchiveEntryData> entries = new LinkedHashMap<>();
        ArchiveIO.ArchiveSnapshot snapshot = ArchiveIO.readArchive(file);

        snapshot.entries().forEach((name, entry) -> {
            if (entry.directory() || name.contains(INNER_CLASS_MARKER)) {
                return;
            }
            entries.put(name, new ArchiveEntryData(entry.length(), computeCRC32(entry.bytes())));
        });

        return new ArchiveSnapshot(snapshot.entryCount(), entries);
    }

    static byte[] readEntryBytes(File file, String entryPath) throws IOException {
        byte[] bytes = ArchiveIO.readEntry(file, entryPath);
        if (bytes == null) {
            throw new IOException("Entry not found in archive: " + entryPath);
        }
        return bytes;
    }

    static Loader createClassLoader(File file) throws IOException {
        Map<String, byte[]> classEntries = new LinkedHashMap<>();
        ArchiveIO.readArchive(file).entries().forEach((name, entry) -> {
            if (!entry.directory() && name.endsWith(StringConstants.CLASS_FILE_SUFFIX)) {
                classEntries.put(name, entry.bytes());
            }
        });
        return new ArchiveClassLoader(classEntries);
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

    static record ArchiveSnapshot(int entryCount, Map<String, ArchiveEntryData> entries) {
    }

    static record ArchiveEntryData(long size, long checksum) {
    }

    private record ArchiveClassLoader(Map<String, byte[]> classEntries) implements Loader {
        @Override
        public boolean canLoad(String internalPath) {
            return classEntries.containsKey(toClassEntryPath(internalPath));
        }

        @Override
        public byte[] load(String internalName) {
            return classEntries.get(toClassEntryPath(internalName));
        }
    }
}
