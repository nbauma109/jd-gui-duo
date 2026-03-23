package org.jd.gui.service.fileloader;

import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.api.API;
import org.jd.gui.model.container.ArchiveContainer;
import org.jd.gui.util.archive.ArchiveIO;

import java.io.File;

public class ArchiveFileLoaderProvider extends AbstractFileLoaderProvider {

    @Override
    public String[] getExtensions() {
        return ArchiveIO.OPENABLE_ARCHIVE_EXTENSIONS;
    }

    @Override
    public String getDescription() {
        return ArchiveIO.OPENABLE_ARCHIVE_DESCRIPTION;
    }

    @Override
    public boolean accept(API api, File file) {
        return file.exists() && file.isFile() && file.canRead() && ArchiveIO.hasSupportedOpenArchiveExtension(file.getName());
    }

    @Override
    public boolean load(API api, File file) {
        try {
            ContainerEntry parentEntry = new ContainerEntry(file);
            ArchiveContainer container = new ArchiveContainer(parentEntry, ArchiveIO.readArchive(file));
            return load(api, file, container, parentEntry) != null;
        } catch (Exception e) {
            assert ExceptionUtil.printStackTrace(e);
            return false;
        }
    }
}
