/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.sourcesaver;

import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.api.API;
import org.jd.gui.api.model.Container;

import java.io.File;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

public class ZipFileSourceSaverProvider extends DirectorySourceSaverProvider {

    @Override
    public String[] getSelectors() { return appendSelectors("*:file:*.zip", "*:file:*.jar", "*:file:*.war", "*:file:*.ear", "*:file:*.aar", "*:file:*.jmod", "*:file:*.kar"); }

    @Override
    public void save(API api, Path rootPath, Container.Entry entry, DoubleSupplier getProgressFunction, DoubleConsumer setProgressFunction, BooleanSupplier isCancelledFunction) {
        try {
            String sourcePath = getSourcePath(entry);
            Path path = rootPath.resolve(sourcePath);
            Path parentPath = path.getParent();

            if (parentPath != null && !Files.exists(parentPath)) {
                Files.createDirectories(parentPath);
            }

            File tmpSourceFile = api.loadSourceFile(entry);

            if (tmpSourceFile != null) {
                Files.copy(tmpSourceFile.toPath(), path);
            } else {
                File tmpFile = File.createTempFile("jd-gui.", ".tmp.zip");

                Files.delete(tmpFile.toPath());
                tmpFile.deleteOnExit();

                URI tmpFileUri = tmpFile.toURI();
                URI tmpArchiveUri = new URI("jar:" + tmpFileUri.getScheme(), tmpFileUri.getHost(), tmpFileUri.getPath() + "!/", null);

                Map<String, String> env = new HashMap<>();
                env.put("create", "true");

                try (FileSystem tmpArchiveFs = FileSystems.newFileSystem(tmpArchiveUri, env)) {
                    Path tmpArchiveRootPath = tmpArchiveFs.getPath("/");
                    saveContent(api, tmpArchiveRootPath, tmpArchiveRootPath, entry, getProgressFunction, setProgressFunction, isCancelledFunction);
                }

                Files.move(tmpFile.toPath(), path);
            }
        } catch (Exception e) {
            assert ExceptionUtil.printStackTrace(e);
        }
    }
}
