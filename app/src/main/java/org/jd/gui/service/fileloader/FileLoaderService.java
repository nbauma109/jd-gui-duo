/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.fileloader;

import org.jd.gui.service.extension.ExtensionService;
import org.jd.gui.spi.FileLoader;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class FileLoaderService {
    protected static final FileLoaderService FILE_LOADER_SERVICE = new FileLoaderService();

    public static FileLoaderService getInstance() { return FILE_LOADER_SERVICE; }

    private final Collection<FileLoader> providers = ExtensionService.getInstance().load(FileLoader.class);

    private final Map<String, FileLoader> mapProviders = new HashMap<>();

    protected FileLoaderService() {
        for (FileLoader provider : providers) {
            for (String extension : provider.getExtensions()) {
                mapProviders.put(extension, provider);
            }
        }
    }

    public FileLoader get(File file) {
        String name = file.getName();
        int lastDot = name.lastIndexOf('.');
        if (lastDot == -1 || lastDot == name.length() - 1) {
            return null;
        }

        // Try multi-part extensions first (e.g., .tar.gz, .tar.xz, .tar.bz2)
        int secondLastDot = name.lastIndexOf('.', lastDot - 1);
        if (secondLastDot != -1) {
            String multiPartExtension = name.substring(secondLastDot + 1).toLowerCase(Locale.ROOT);
            FileLoader loader = mapProviders.get(multiPartExtension);
            if (loader != null) {
                return loader;
            }
        }

        // Fall back to single-part extension
        String extension = name.substring(lastDot + 1).toLowerCase(Locale.ROOT);
        return mapProviders.get(extension);
    }

    public Map<String, FileLoader> getMapProviders() {
        return mapProviders;
    }
}
