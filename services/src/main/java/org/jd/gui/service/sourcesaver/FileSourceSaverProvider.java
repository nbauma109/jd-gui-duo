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
import org.jd.gui.util.ProgressUtil;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

public class FileSourceSaverProvider extends AbstractSourceSaverProvider {

    @Override
    public String[] getSelectors() { return appendSelectors("*:file:*"); }

    @Override
    public String getSourcePath(Container.Entry entry) { return entry.getPath(); }

    @Override
    public int getFileCount(API api, Container.Entry entry) { return 1; }

    @Override
    public void save(API api, Path rootPath, Container.Entry entry, DoubleSupplier getProgressFunction, DoubleConsumer setProgressFunction, BooleanSupplier isCancelledFunction) {
        saveContent(api, rootPath, rootPath.resolve(entry.getPath()), entry, getProgressFunction, setProgressFunction, isCancelledFunction);
    }

    @Override
    public void saveContent(API api, Path rootPath, Path path, Container.Entry entry, DoubleSupplier getProgressFunction, DoubleConsumer setProgressFunction, BooleanSupplier isCancelledFunction) {

        try (InputStream is = entry.getInputStream()) {
            Files.copy(is, path, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            assert ExceptionUtil.printStackTrace(e);

            try (BufferedWriter writer = Files.newBufferedWriter(path, Charset.defaultCharset())) {
                writer.write("// INTERNAL ERROR //");
            } catch (IOException ee) {
                assert ExceptionUtil.printStackTrace(ee);
            }
        }

        try {
            ProgressUtil.updateProgress(entry, getProgressFunction, setProgressFunction);
        } catch (IOException e) {
            assert ExceptionUtil.printStackTrace(e);
        }
    }
}
