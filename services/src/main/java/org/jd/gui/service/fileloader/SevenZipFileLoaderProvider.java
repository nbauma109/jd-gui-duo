/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.fileloader;

import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.api.API;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Iterator;

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
        try {
            FileSystem fileSystem;

            try {
                fileSystem = FileSystems.getFileSystem(file.toPath());
            } catch (FileSystemNotFoundException e) {
                // Resource leak : file system cannot be closed until the application is closed
                fileSystem = FileSystems.newFileSystem(file.toPath(), Collections.emptyMap());
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
