/*
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.indexer;

import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.api.API;
import org.jd.gui.api.model.Container;
import org.jd.gui.api.model.Indexes;
import org.jd.gui.service.indexer.AbstractIndexerProvider;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

public class ImageFileIndexerProvider extends AbstractIndexerProvider {

    @Override
    public String[] getSelectors() {
        return appendSelectors("*:file:*.jpg", "*:file:*.jpeg", "*:file:*.png", "*:file:*.gif", "*:file:*.bmp");
    }

    @SuppressWarnings("unchecked")
    @Override
    public void index(API api, Container.Entry entry, Indexes indexes, DoubleSupplier getProgressFunction, DoubleConsumer setProgressFunction, BooleanSupplier isCancelledFunction) {
        try (InputStream inputStream = entry.getInputStream()) {
            String entryPath = entry.getPath();
            int startIndex = entryPath.lastIndexOf('/');
            int endIndex = entryPath.lastIndexOf('.');
            if (startIndex != -1 && endIndex != -1) {
                String imageFileName = entryPath.substring(startIndex + 1, endIndex);
                String extension = entryPath.substring(endIndex + 1);
                @SuppressWarnings("rawtypes")
                Map<String, Collection> strings = indexes.getIndex("strings");
                strings.get(imageFileName).add(entry);
                strings.get(extension).add(entry);
            }
            updateProgress(entry, getProgressFunction, setProgressFunction);
        } catch (IOException e) {
            assert ExceptionUtil.printStackTrace(e);
        }
    }
}
