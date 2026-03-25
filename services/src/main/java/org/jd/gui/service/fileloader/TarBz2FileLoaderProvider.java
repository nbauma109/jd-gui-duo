/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.fileloader;

public class TarBz2FileLoaderProvider extends AbstractArchiveFileLoaderProvider {
    protected static final String[] EXTENSIONS = { "tar.bz2", "tbz2", "tar.bz" };

    @Override
    public String[] getExtensions() { return EXTENSIONS; }

    @Override
    public String getDescription() { return "Tar BZ2 archives (*.tar.bz2, *.tbz2, *.tar.bz)"; }
}
