/*
 * © 2008-2019 Emmanuel Dupuy
 * © 2022-2026 Nicolas Baumann (@nbauma109)
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
        String extension = name.substring(lastDot + 1).toLowerCase(Locale.ROOT);
        return mapProviders.get(extension);
    }

    public Map<String, FileLoader> getMapProviders() {
        return mapProviders;
    }
}
