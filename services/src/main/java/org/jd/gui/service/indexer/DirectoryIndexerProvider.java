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
import org.jd.gui.spi.Indexer;
import org.jd.gui.util.decompiler.GuiPreferences;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

public class DirectoryIndexerProvider extends AbstractIndexerProvider {

    @Override
    public String[] getSelectors() { return appendSelectors("*:dir:*"); }

    @Override
    public void index(API api, Container.Entry entry, Indexes indexes, DoubleSupplier getProgressFunction, DoubleConsumer setProgressFunction, BooleanSupplier isCancelledFunction) {
        int depth = 15;

        try {
            depth = Integer.parseInt(api.getPreferences().get(GuiPreferences.MAXIMUM_DEPTH_KEY));
        } catch (NumberFormatException e) {
            assert ExceptionUtil.printStackTrace(e);
        }

        index(api, entry, indexes, getProgressFunction, setProgressFunction, isCancelledFunction, depth);
    }

    public void index(API api, Container.Entry entry, Indexes indexes, DoubleSupplier getProgressFunction, DoubleConsumer setProgressFunction, BooleanSupplier isCancelledFunction, int depth) {
        if (depth-- > 0) {
            for (Container.Entry e : entry.getChildren().values()) {
                if (e.isDirectory()) {
                    index(api, e, indexes, getProgressFunction, setProgressFunction, isCancelledFunction, depth);
                } else {
                    Indexer indexer = api.getIndexer(e);

                    if (indexer != null) {
                        indexer.index(api, e, indexes, getProgressFunction, setProgressFunction, isCancelledFunction);
                    }
                }
            }
        }
    }
}
