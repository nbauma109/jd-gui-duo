/*
 * Copyright (c) 2008-2026 Emmanuel Dupuy and other contributors.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.fileloader;

import org.jd.gui.api.API;

import java.io.File;
import java.util.Locale;

public abstract class AbstractAndroidFileLoaderProvider extends AbstractConvertedJarFileLoaderProvider {

    private final String extension;
    private final String description;

    protected AbstractAndroidFileLoaderProvider(String extension, String description) {
        this.extension = extension;
        this.description = description;
    }

    @Override
    public String[] getExtensions() {
        return new String[] { extension };
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public boolean accept(API api, File file) {
        return file.exists()
            && file.isFile()
            && file.canRead()
            && file.getName().toLowerCase(Locale.ROOT).endsWith("." + extension);
    }
}
