/*
 * © 2008-2019 Emmanuel Dupuy
 * © 2022-2024 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.fileloader;

import org.apache.commons.io.FilenameUtils;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.api.API;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Iterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ZipFileLoaderProvider extends AbstractFileLoaderProvider {
    protected static final String[] EXTENSIONS = { "zip" };

    private boolean isValidScheme(String scheme) {
        return "file".equals(scheme);
    }

    @Override
    public String[] getExtensions() { return EXTENSIONS; }
    @Override
    public String getDescription() { return "Zip files (*.zip)"; }

    @Override
    public boolean accept(API api, File file) {
        return file.exists() && file.isFile() && file.canRead() && file.getName().toLowerCase().endsWith(".zip");
    }

    @Override
    @SuppressWarnings("all")
    public boolean load(API api, File file) {
        try {
            URI fileUri = file.toURI();
            if (!isValidScheme(fileUri.getScheme())) {
                throw new URISyntaxException(fileUri.toString(), "Invalid URI scheme");
            }
            URI uri = new URI("jar:" + fileUri.getScheme(), fileUri.getHost(), fileUri.getPath() + "!/", null);

            FileSystem fileSystem;

            try {
                fileSystem = FileSystems.getFileSystem(uri);
            } catch (FileSystemNotFoundException e) {
                // Resource leak : file system cannot be closed until the application is closed
                fileSystem = newFileSystem(uri, file);
            }

            if (fileSystem != null) {
                Iterator<Path> rootDirectories = fileSystem.getRootDirectories().iterator();
                if (rootDirectories.hasNext()) {
                    return load(api, file, rootDirectories.next()) != null;
                }
            }
        } catch (URISyntaxException|IOException e) {
            assert ExceptionUtil.printStackTrace(e);
        }

        return false;
    }

    private FileSystem newFileSystem(URI uri, File file) throws IOException {
        try {
            return FileSystems.newFileSystem(uri, Collections.emptyMap());
        } catch (ZipException _) {
            // Rewrite entries through ZipInputStream to discard malformed CEN metadata.
            Path userHome = Path.of(System.getProperty("user.home"));
            Path sanitizedArchive = Files.createTempFile(userHome, "jd-gui-duo-", getExtension(file));
            sanitizedArchive.toFile().deleteOnExit();

            try (ZipInputStream input = new ZipInputStream(new FileInputStream(file));
                 ZipOutputStream output = new ZipOutputStream(new FileOutputStream(sanitizedArchive.toFile()))) {
                ZipEntry entry;

                while ((entry = input.getNextEntry()) != null) {
                    output.putNextEntry(new ZipEntry(entry.getName()));
                    input.transferTo(output);
                    output.closeEntry();
                }
            }

            return FileSystems.newFileSystem(sanitizedArchive, (ClassLoader)null);
        }
    }

    private String getExtension(File file) {
        String extension = FilenameUtils.getExtension(file.getName());
        return extension.isEmpty() ? ".zip" : '.' + extension;
    }
}
