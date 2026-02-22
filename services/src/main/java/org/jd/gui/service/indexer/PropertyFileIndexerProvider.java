/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.indexer;

import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.api.API;
import org.jd.gui.api.model.Container;
import org.jd.gui.api.model.Indexes;
import org.jd.gui.util.ProgressUtil;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

public class PropertyFileIndexerProvider extends AbstractIndexerProvider {

    @Override
    public String[] getSelectors() {
        return appendSelectors("*:file:*.properties");
    }

    @Override
    public void index(API api, Container.Entry entry, Indexes indexes, DoubleSupplier getProgressFunction, DoubleConsumer setProgressFunction, BooleanSupplier isCancelledFunction) {
        Set<String> stringSet = new HashSet<>();

        try (InputStream inputStream = entry.getInputStream()) {
            Properties props = new Properties();
            props.load(inputStream);
            for (Entry<Object, Object> property : props.entrySet()) {
                stringSet.add(property.getKey().toString());
                stringSet.add(property.getValue().toString());
            }
        } catch (Exception e) {
            assert ExceptionUtil.printStackTrace(e);
        }

        indexAll(entry, indexes, stringSet);

        try {
            ProgressUtil.updateProgress(entry, getProgressFunction, setProgressFunction);
        } catch (IOException e) {
            assert ExceptionUtil.printStackTrace(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void indexAll(Container.Entry entry, Indexes indexes, Set<String> stringSet) {
        @SuppressWarnings("rawtypes")
        Map<String, Collection> stringIndex = indexes.getIndex("strings");
        for (String string : stringSet) {
            if (string != null && !string.isEmpty()) {
                stringIndex.get(string).add(entry);
            }
        }
    }
}
