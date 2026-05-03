/*
 * © 2008-2026 Emmanuel Dupuy
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.fileloader;

public class ApkFileLoaderProvider extends AbstractAndroidFileLoaderProvider {

    public ApkFileLoaderProvider() {
        super("apk", "Android package files (*.apk)");
    }
}
