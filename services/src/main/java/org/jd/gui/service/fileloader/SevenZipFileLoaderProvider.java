/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.fileloader;

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.api.API;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public class SevenZipFileLoaderProvider extends AbstractFileLoaderProvider {
    protected static final String[] EXTENSIONS = { "7z" };

    @Override
    public String[] getExtensions() { return EXTENSIONS; }

    @Override
    public String getDescription() { return "7-Zip archives (*.7z)"; }

    @Override
    public boolean accept(API api, File file) {
        if (!file.exists() || !file.isFile() || !file.canRead()) {
            return false;
        }
        String name = file.getName().toLowerCase();
        return name.endsWith(".7z");
    }

    @Override
    public boolean load(API api, File file) {
        Path tempDir = null;
        try {
            // Create temporary directory
            tempDir = Files.createTempDirectory("jd-gui-7z-");

            // Extract 7z to temp directory
            try (SevenZFile sevenZFile = SevenZFile.builder().setFile(file).get()) {
                SevenZArchiveEntry entry;
                while ((entry = sevenZFile.getNextEntry()) != null) {
                    Path extractPath = tempDir.resolve(entry.getName());

                    if (entry.isDirectory()) {
                        Files.createDirectories(extractPath);
                    } else {
                        Files.createDirectories(extractPath.getParent());
                        try (FileOutputStream fos = new FileOutputStream(extractPath.toFile())) {
                            byte[] buffer = new byte[8192];
                            int bytesRead;
                            while ((bytesRead = sevenZFile.read(buffer)) != -1) {
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
