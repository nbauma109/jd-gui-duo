/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.fileloader;

public class TarGzFileLoaderProvider extends AbstractArchiveFileLoaderProvider {
    protected static final String[] EXTENSIONS = { "tar.gz", "tgz" };

    @Override
    public String[] getExtensions() { return EXTENSIONS; }

    @Override
    public String getDescription() { return "Tar GZ archives (*.tar.gz, *.tgz)"; }
}
