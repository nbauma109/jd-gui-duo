package org.jd.gui.service.fileloader;

import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.api.API;
import org.jd.gui.util.archive.ArchiveExtraction;

import java.io.File;
import java.nio.file.Path;

public class ArchiveFileLoaderProvider extends AbstractFileLoaderProvider {

    @Override
    public String[] getExtensions() {
        return ArchiveExtraction.EXTENSIONS;
    }

    @Override
    public String getDescription() {
        return ArchiveExtraction.DESCRIPTION;
    }

    @Override
    public boolean accept(API api, File file) {
        return file.exists() && file.isFile() && file.canRead() && ArchiveExtraction.hasSupportedExtension(file.getName());
    }

    @Override
    public boolean load(API api, File file) {
        try {
            Path extractedRoot = ArchiveExtraction.extractToTemporaryDirectory(file.toPath());
            return load(api, file, extractedRoot) != null;
        } catch (Exception e) {
            assert ExceptionUtil.printStackTrace(e);
            return false;
        }
    }
}
