/*
 * Copyright (c) 2008-2026 Emmanuel Dupuy and other contributors.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.fileloader;

import org.jd.gui.api.API;

import java.io.File;

public class ApkFileLoaderProvider extends AbstractConvertedJarFileLoaderProvider {
    protected static final String[] EXTENSIONS = { "apk" };

    @Override
    public String[] getExtensions() {
        return EXTENSIONS;
    }

    @Override
    public String getDescription() {
        return "Android package files (*.apk)";
    }

    @Override
    public boolean accept(API api, File file) {
        return file.exists() && file.isFile() && file.canRead() && file.getName().toLowerCase().endsWith(".apk");
    }

}
