/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.fileloader;

public class SevenZipFileLoaderProvider extends AbstractArchiveFileLoaderProvider {
    protected static final String[] EXTENSIONS = { "7z" };

    @Override
    public String[] getExtensions() { return EXTENSIONS; }

    @Override
    public String getDescription() { return "7-Zip archives (*.7z)"; }
}
