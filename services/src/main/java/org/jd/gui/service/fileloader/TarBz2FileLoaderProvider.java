/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.fileloader;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.api.API;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public class TarBz2FileLoaderProvider extends AbstractFileLoaderProvider {
    protected static final String[] EXTENSIONS = { "tar.bz2", "tbz2", "tar.bz" };

    @Override
    public String[] getExtensions() { return EXTENSIONS; }

    @Override
    public String getDescription() { return "Tar BZ2 archives (*.tar.bz2, *.tbz2)"; }

    @Override
    public boolean accept(API api, File file) {
        if (!file.exists() || !file.isFile() || !file.canRead()) {
            return false;
        }
        String name = file.getName().toLowerCase();
        return name.endsWith(".tar.bz2") || name.endsWith(".tbz2") || name.endsWith(".tar.bz");
    }

    @Override
    public boolean load(API api, File file) {
        Path tempDir = null;
        try {
            // Create temporary directory
            tempDir = Files.createTempDirectory("jd-gui-tar-bz2-");

            // Extract tar.bz2 to temp directory
            try (FileInputStream fis = new FileInputStream(file);
                 BufferedInputStream bis = new BufferedInputStream(fis);
                 BZip2CompressorInputStream bzis = new BZip2CompressorInputStream(bis);
                 TarArchiveInputStream tis = new TarArchiveInputStream(bzis)) {

                TarArchiveEntry entry;
                while ((entry = tis.getNextEntry()) != null) {
                    Path extractPath = tempDir.resolve(entry.getName());

                    if (entry.isDirectory()) {
                        Files.createDirectories(extractPath);
                    } else {
                        Files.createDirectories(extractPath.getParent());
                        try (FileOutputStream fos = new FileOutputStream(extractPath.toFile())) {
                            byte[] buffer = new byte[8192];
                            int bytesRead;
                            while ((bytesRead = tis.read(buffer)) != -1) {
                                fos.write(buffer, 0, bytesRead);
                            }
                        }
                    }
                }
            }

            // Load the extracted directory
            boolean result = load(api, file, tempDir) != null;

            // Clean up temp directory on JVM exit
            final Path finalTempDir = tempDir;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    Files.walk(finalTempDir)
                        .sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                // Ignore cleanup errors
                            }
                        });
                } catch (IOException e) {
                    // Ignore cleanup errors
                }
            }));

            return result;
        } catch (IOException e) {
            assert ExceptionUtil.printStackTrace(e);
            // Clean up temp directory on error
            if (tempDir != null) {
                try {
                    final Path finalTempDir = tempDir;
                    Files.walk(finalTempDir)
                        .sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException ex) {
                                // Ignore cleanup errors
                            }
                        });
                } catch (IOException ex) {
                    // Ignore cleanup errors
                }
            }
            return false;
        }
    }
}
