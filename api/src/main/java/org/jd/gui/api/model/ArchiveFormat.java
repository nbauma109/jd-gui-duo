/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.api.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public enum ArchiveFormat {
    JAR("jar"),
    ZIP("zip"),
    WAR("war"),
    EAR("ear"),
    TAR_GZ("tar.gz", "tgz"),
    TAR_XZ("tar.xz", "txz"),
    TAR_BZ2("tar.bz2", "tbz2", "tar.bz"),
    SEVEN_ZIP("7z");

    private final String[] extensions;

    ArchiveFormat(String... extensions) {
        this.extensions = extensions;
    }

    public String[] getExtensions() {
        return extensions.clone();
    }

    public boolean matches(String fileName) {
        if (fileName == null) {
            return false;
        }

        String lowerCaseName = fileName.toLowerCase(Locale.ROOT);

        for (String extension : extensions) {
            if (lowerCaseName.endsWith("." + extension)) {
                return true;
            }
        }

        return false;
    }

    public static boolean matchesAny(String fileName, ArchiveFormat... formats) {
        if (formats == null) {
            return false;
        }

        for (ArchiveFormat format : formats) {
            if (format != null && format.matches(fileName)) {
                return true;
            }
        }

        return false;
    }

    public static String[] extensionsOf(ArchiveFormat... formats) {
        List<String> extensions = new ArrayList<>();

        if (formats != null) {
            for (ArchiveFormat format : formats) {
                if (format != null) {
                    for (String extension : format.extensions) {
                        extensions.add(extension);
                    }
                }
            }
        }

        return extensions.toArray(String[]::new);
    }

    public static String[] selectorsOf(ArchiveFormat... formats) {
        List<String> selectors = new ArrayList<>();

        if (formats != null) {
            for (ArchiveFormat format : formats) {
                if (format != null) {
                    for (String extension : format.extensions) {
                        selectors.add("*:file:*." + extension);
                    }
                }
            }
        }

        return selectors.toArray(String[]::new);
    }
}
