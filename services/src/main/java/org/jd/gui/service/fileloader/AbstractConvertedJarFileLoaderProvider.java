/*
 * Copyright (c) 2008-2026 Emmanuel Dupuy and other contributors.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.fileloader;

import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.api.API;
import org.jd.gui.util.conversion.DexToJarConversionKit;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Iterator;

public abstract class AbstractConvertedJarFileLoaderProvider extends AbstractFileLoaderProvider {

    @Override
    @SuppressWarnings("all")
    public boolean load(API api, File file) {
        try {
            File convertedJar = DexToJarConversionKit.getOrCreate(file);
            URI fileUri = convertedJar.toURI();
            URI jarUri = new URI("jar:" + fileUri.getScheme(), fileUri.getHost(), fileUri.getPath() + "!/", null);

            FileSystem fileSystem;
            try {
                fileSystem = FileSystems.getFileSystem(jarUri);
            } catch (FileSystemNotFoundException e) {
                fileSystem = FileSystems.newFileSystem(jarUri, Collections.emptyMap());
            }

            if (fileSystem != null) {
                Iterator<Path> rootDirectories = fileSystem.getRootDirectories().iterator();
                if (rootDirectories.hasNext()) {
                    return load(api, file, rootDirectories.next()) != null;
                }
            }
        } catch (URISyntaxException | IOException e) {
            assert ExceptionUtil.printStackTrace(e);
        }

        return false;
    }
}
