/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.fileloader;

import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.api.API;
import org.jd.gui.api.model.ArchiveFormat;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Iterator;

public class ArchiveFileLoaderProvider extends AbstractFileLoaderProvider {
    private static final ArchiveFormat[] SUPPORTED_FORMATS = {
        ArchiveFormat.TAR_GZ,
        ArchiveFormat.TAR_XZ,
        ArchiveFormat.TAR_BZ2,
        ArchiveFormat.SEVEN_ZIP
    };
    private static final String[] EXTENSIONS = ArchiveFormat.extensionsOf(SUPPORTED_FORMATS);
    private static final String DESCRIPTION = "Archive files (*.tar.gz, *.tgz, *.tar.xz, *.txz, *.tar.bz2, *.tbz2, *.tar.bz, *.7z)";

    @Override
    public String[] getExtensions() {
        return EXTENSIONS;
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public boolean accept(API api, File file) {
        return file.exists() && file.isFile() && file.canRead() && ArchiveFormat.matchesAny(file.getName(), SUPPORTED_FORMATS);
    }

    @Override
    public boolean load(API api, File file) {
        try {
            URI uri = URI.create("smartnio:" + file.toURI());
            FileSystem fileSystem;

            try {
                fileSystem = FileSystems.getFileSystem(uri);
            } catch (FileSystemNotFoundException e) {
                // Resource leak : file system cannot be closed until the application is closed
                fileSystem = FileSystems.newFileSystem(uri, Collections.emptyMap());
            }

            if (fileSystem != null) {
                Iterator<Path> rootDirectories = fileSystem.getRootDirectories().iterator();
                if (rootDirectories.hasNext()) {
                    return load(api, file, rootDirectories.next()) != null;
                }
            }
        } catch (IOException e) {
            assert ExceptionUtil.printStackTrace(e);
        }

        return false;
    }
}
