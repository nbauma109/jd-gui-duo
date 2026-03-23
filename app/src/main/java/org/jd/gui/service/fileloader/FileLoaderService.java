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
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (name.isEmpty()) {
            return null;
        }
        FileLoader matchedLoader = null;
        int matchedLength = -1;
        for (Map.Entry<String, FileLoader> entry : mapProviders.entrySet()) {
            String extension = entry.getKey().toLowerCase(Locale.ROOT);
            if (name.endsWith("." + extension) && extension.length() > matchedLength) {
                matchedLoader = entry.getValue();
                matchedLength = extension.length();
            }
        }
        return matchedLoader;
    }

    public Map<String, FileLoader> getMapProviders() {
        return mapProviders;
    }
}
