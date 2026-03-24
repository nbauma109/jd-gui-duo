package tim.jarcomp;

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Helper class to read entries from different archive formats
 */
public class ArchiveReader implements AutoCloseable {

    private final File file;
    private final ArchiveType type;
    private ZipFile zipFile;
    private SevenZFile sevenZFile;

    public enum ArchiveType {
        ZIP, TAR_GZ, TAR_XZ, TAR_BZ2, SEVEN_ZIP
    }

    public ArchiveReader(File file) throws IOException {
        this.file = file;
        this.type = detectType(file);

        if (type == ArchiveType.ZIP) {
            zipFile = new ZipFile(file);
        } else if (type == ArchiveType.SEVEN_ZIP) {
            sevenZFile = SevenZFile.builder().setFile(file).get();
        }
    }

    private static ArchiveType detectType(File file) {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".tar.gz") || name.endsWith(".tgz")) {
            return ArchiveType.TAR_GZ;
        } else if (name.endsWith(".tar.xz") || name.endsWith(".txz")) {
            return ArchiveType.TAR_XZ;
        } else if (name.endsWith(".tar.bz2") || name.endsWith(".tbz2") || name.endsWith(".tar.bz")) {
            return ArchiveType.TAR_BZ2;
        } else if (name.endsWith(".7z")) {
            return ArchiveType.SEVEN_ZIP;
        } else {
            return ArchiveType.ZIP;
        }
    }

    public List<ArchiveEntryInfo> getEntries() throws IOException {
        List<ArchiveEntryInfo> entries = new ArrayList<>();

        switch (type) {
            case ZIP:
                zipFile.stream().forEach(zipEntry -> {
                    entries.add(new ArchiveEntryInfo(
                        zipEntry.getName(),
                        zipEntry.getSize(),
                        zipEntry.getCrc(),
                        zipEntry.isDirectory()
                    ));
                });
                break;

            case TAR_GZ:
                readTarEntries(entries, new GzipCompressorInputStream(
                    new BufferedInputStream(new FileInputStream(file))));
                break;

            case TAR_XZ:
                readTarEntries(entries, new XZCompressorInputStream(
                    new BufferedInputStream(new FileInputStream(file))));
                break;

            case TAR_BZ2:
                readTarEntries(entries, new BZip2CompressorInputStream(
                    new BufferedInputStream(new FileInputStream(file))));
                break;

            case SEVEN_ZIP:
                SevenZArchiveEntry entry;
                while ((entry = sevenZFile.getNextEntry()) != null) {
                    entries.add(new ArchiveEntryInfo(
                        entry.getName(),
                        entry.getSize(),
                        entry.getCrcValue(),
                        entry.isDirectory()
                    ));
                }
                break;
        }

        return entries;
    }

    private void readTarEntries(List<ArchiveEntryInfo> entries, java.io.InputStream compressorStream)
            throws IOException {
        try (TarArchiveInputStream tis = new TarArchiveInputStream(compressorStream)) {
            TarArchiveEntry entry;
            while ((entry = tis.getNextEntry()) != null) {
                // TAR doesn't have CRC, so we use 0
                entries.add(new ArchiveEntryInfo(
                    entry.getName(),
                    entry.getSize(),
                    0,
                    entry.isDirectory()
                ));
            }
        }
    }

    /**
     * Get the content of a specific entry as a byte array
     *
     * @param entryPath path of the entry in the archive
     * @return byte array containing the entry content, or null if not found
     * @throws IOException if an error occurs reading the archive
     */
    public byte[] getEntryContent(String entryPath) throws IOException {
        switch (type) {
            case ZIP:
                ZipEntry zipEntry = zipFile.getEntry(entryPath);
                if (zipEntry == null || zipEntry.isDirectory()) {
                    return null;
                }
                try (InputStream is = zipFile.getInputStream(zipEntry)) {
                    return readAllBytes(is);
                }

            case TAR_GZ:
                return readTarEntryContent(new GzipCompressorInputStream(
                    new BufferedInputStream(new FileInputStream(file))), entryPath);

            case TAR_XZ:
                return readTarEntryContent(new XZCompressorInputStream(
                    new BufferedInputStream(new FileInputStream(file))), entryPath);

            case TAR_BZ2:
                return readTarEntryContent(new BZip2CompressorInputStream(
                    new BufferedInputStream(new FileInputStream(file))), entryPath);

            case SEVEN_ZIP:
                return readSevenZEntryContent(entryPath);

            default:
                return null;
        }
    }

    private byte[] readTarEntryContent(InputStream compressorStream, String entryPath) throws IOException {
        try (TarArchiveInputStream tis = new TarArchiveInputStream(compressorStream)) {
            TarArchiveEntry entry;
            while ((entry = tis.getNextEntry()) != null) {
                if (entry.getName().equals(entryPath) && !entry.isDirectory()) {
                    return readAllBytes(tis);
                }
            }
        }
        return null;
    }

    private byte[] readSevenZEntryContent(String entryPath) throws IOException {
        // Need to re-open the 7z file to read content
        try (SevenZFile sz = SevenZFile.builder().setFile(file).get()) {
            SevenZArchiveEntry entry;
            while ((entry = sz.getNextEntry()) != null) {
                if (entry.getName().equals(entryPath) && !entry.isDirectory()) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = sz.read(buffer)) != -1) {
                        baos.write(buffer, 0, bytesRead);
                    }
                    return baos.toByteArray();
                }
            }
        }
        return null;
    }

    private byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = is.read(buffer)) != -1) {
            baos.write(buffer, 0, bytesRead);
        }
        return baos.toByteArray();
    }

    @Override
    public void close() throws IOException {
        if (zipFile != null) {
            zipFile.close();
        }
        if (sevenZFile != null) {
            sevenZFile.close();
        }
    }

    public static class ArchiveEntryInfo {
        private final String name;
        private final long size;
        private final long crc;
        private final boolean isDirectory;

        public ArchiveEntryInfo(String name, long size, long crc, boolean isDirectory) {
            this.name = name;
            this.size = size;
            this.crc = crc;
            this.isDirectory = isDirectory;
        }

        public String getName() { return name; }
        public long getSize() { return size; }
        public long getCrc() { return crc; }
        public boolean isDirectory() { return isDirectory; }
    }
}
