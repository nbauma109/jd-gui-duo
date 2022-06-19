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
import org.jd.gui.spi.SourceSaver;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DirectorySourceSaverProvider extends AbstractSourceSaverProvider {

    @Override
    public String[] getSelectors() { return appendSelectors("*:dir:*"); }

    @Override
    public String getSourcePath(Container.Entry entry) {
        File file = new File(entry.getUri());
        Pattern jarPattern = Pattern.compile("\\.jar$");
        Matcher jarMatcher = jarPattern.matcher(file.getAbsolutePath());
        if(jarMatcher.find()) {
            return jarMatcher.replaceFirst("-sources.jar");
        }
        return entry.getPath() + ".src.zip";
    }

    @Override
    public int getFileCount(API api, Container.Entry entry) { return getFileCount(api, entry.getChildren().values()); }

    protected int getFileCount(API api, Collection<Container.Entry> entries) {
        int count = 0;

        for (Container.Entry e : entries) {
            SourceSaver sourceSaver = api.getSourceSaver(e);

            if (sourceSaver != null) {
                count += sourceSaver.getFileCount(api, e);
            }
        }

        return count;
    }

    @Override
    public void save(API api, Path rootPath, Container.Entry entry, DoubleSupplier getProgressFunction, DoubleConsumer setProgressFunction, BooleanSupplier isCancelledFunction) {
        Path path = rootPath.resolve(entry.getPath());

        try {
            Files.createDirectories(path);
            saveContent(api, rootPath, path, entry, getProgressFunction, setProgressFunction, isCancelledFunction);
        } catch (IOException e) {
            assert ExceptionUtil.printStackTrace(e);
        }
    }

    @Override
    public void saveContent(API api, Path rootPath, Path path, Container.Entry entry, DoubleSupplier getProgressFunction, DoubleConsumer setProgressFunction, BooleanSupplier isCancelledFunction) {
        for (Container.Entry e : getChildren(entry)) {
            if (isCancelledFunction.getAsBoolean()) {
                break;
            }

            SourceSaver sourceSaver = api.getSourceSaver(e);

            if (sourceSaver != null) {
                sourceSaver.save(api, rootPath, e, getProgressFunction, setProgressFunction, isCancelledFunction);
            }
        }
    }

    protected Collection<Container.Entry> getChildren(Container.Entry entry) { return entry.getChildren().values(); }
}
