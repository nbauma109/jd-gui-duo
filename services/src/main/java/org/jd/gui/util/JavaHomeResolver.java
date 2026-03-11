/*
 * Copyright (c) 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.util;

import java.io.File;

public final class JavaHomeResolver {

    private static final String RELEASE = "release";

    private JavaHomeResolver() {
    }

    public static File normalizeJavaHome(File directory) {
        if (directory == null || !directory.isDirectory()) {
            return null;
        }

        File directReleaseFile = new File(directory, RELEASE);
        if (directReleaseFile.isFile()) {
            return directory;
        }

        File parentDirectory = directory.getParentFile();
        if (parentDirectory == null) {
            return null;
        }

        File parentReleaseFile = new File(parentDirectory, RELEASE);
        if (parentReleaseFile.isFile()) {
            return parentDirectory;
        }

        return null;
    }

    public static File findReleaseFile(File directory) {
        File javaHome = normalizeJavaHome(directory);
        if (javaHome == null) {
            return null;
        }

        return new File(javaHome, RELEASE);
    }
}
