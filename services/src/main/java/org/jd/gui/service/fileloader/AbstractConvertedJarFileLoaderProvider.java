/*
 * © 2008-2026 Emmanuel Dupuy
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.fileloader;

import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.api.API;
import org.jd.gui.model.container.ConvertedJarContainer;
import org.jd.gui.util.conversion.DexToJarConversionKit;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public abstract class AbstractConvertedJarFileLoaderProvider extends AbstractFileLoaderProvider {

    @Override
    @SuppressWarnings("all")
    public boolean load(API api, File file) {
        try {
            Map<String, byte[]> classFiles = DexToJarConversionKit.getOrCreate(file);
            ContainerEntry parentEntry = new ContainerEntry(file);
            ConvertedJarContainer container = new ConvertedJarContainer(parentEntry, classFiles);
            return load(api, file, container, parentEntry) != null;
        } catch (IOException e) {
            assert ExceptionUtil.printStackTrace(e);
        }

        return false;
    }
}
