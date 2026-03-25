package tim.jarcomp;

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.jd.gui.api.model.ArchiveFormat;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Helper class to read entries from different archive formats
 */
public class ArchiveReader implements AutoCloseable {

    private final File file;
    private final ArchiveFormat type;
    private ZipFile zipFile;
    private SevenZFile sevenZFile;

    public ArchiveReader(File file) throws IOException {
        this.file = file;
        this.type = detectType(file);

        if (type == ArchiveFormat.ZIP) {
            zipFile = new ZipFile(file);
        } else if (type == ArchiveFormat.SEVEN_ZIP) {
            sevenZFile = SevenZFile.builder().setFile(file).get();
        }
    }

    private static ArchiveFormat detectType(File file) {
        if (ArchiveFormat.TAR_GZ.matches(file.getName())) {
            return ArchiveFormat.TAR_GZ;
        }
        if (ArchiveFormat.TAR_XZ.matches(file.getName())) {
            return ArchiveFormat.TAR_XZ;
        }
        if (ArchiveFormat.TAR_BZ2.matches(file.getName())) {
            return ArchiveFormat.TAR_BZ2;
        }
        if (ArchiveFormat.SEVEN_ZIP.matches(file.getName())) {
            return ArchiveFormat.SEVEN_ZIP;
        }
        return ArchiveFormat.ZIP;
    }

    public List<ArchiveEntryInfo> getEntries() throws IOException {
        List<ArchiveEntryInfo> entries = new ArrayList<>();

        if (type == ArchiveFormat.ZIP) {
            zipFile.stream().forEach(zipEntry -> entries.add(new ArchiveEntryInfo(
                zipEntry.getName(),
                zipEntry.getSize(),
                zipEntry.getCrc(),
                zipEntry.isDirectory()
            )));
        } else if (type == ArchiveFormat.SEVEN_ZIP) {
            SevenZArchiveEntry entry;
            while ((entry = sevenZFile.getNextEntry()) != null) {
                entries.add(new ArchiveEntryInfo(
                    entry.getName(),
                    entry.getSize(),
                    entry.getCrcValue(),
                    entry.isDirectory()
                ));
            }
        } else {
            readTarEntries(entries, openCompressorStream());
        }

        return entries;
    }

    private void readTarEntries(List<ArchiveEntryInfo> entries, java.io.InputStream compressorStream)
            throws IOException {
        try (TarArchiveInputStream tis = new TarArchiveInputStream(compressorStream)) {
            TarArchiveEntry entry;
            while ((entry = tis.getNextEntry()) != null) {
                long crc = 0;
                if (!entry.isDirectory()) {
                    // Compute CRC32 for non-directory entries
                    CRC32 crc32 = new CRC32();
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = tis.read(buffer)) != -1) {
                        crc32.update(buffer, 0, bytesRead);
                    }
                    crc = crc32.getValue();
                }
                entries.add(new ArchiveEntryInfo(
                    entry.getName(),
                    entry.getSize(),
                    crc,
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
        if (type == ArchiveFormat.ZIP) {
            ZipEntry zipEntry = zipFile.getEntry(entryPath);
            if (zipEntry == null || zipEntry.isDirectory()) {
                return null;
            }
            try (InputStream is = zipFile.getInputStream(zipEntry)) {
                return readAllBytes(is);
            }
        }

        if (type == ArchiveFormat.SEVEN_ZIP) {
            return readSevenZEntryContent(entryPath);
        }

        return readTarEntryContent(openCompressorStream(), entryPath);
    }

    private InputStream openCompressorStream() throws IOException {
        BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(file));

        try {
            return switch (type) {
                case TAR_GZ -> new GzipCompressorInputStream(inputStream);
                case TAR_XZ -> new XZCompressorInputStream(inputStream);
                case TAR_BZ2 -> new BZip2CompressorInputStream(inputStream);
                default -> throw new IllegalStateException("Unsupported tar archive format: " + type);
            };
        } catch (IOException | RuntimeException e) {
            inputStream.close();
            throw e;
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
