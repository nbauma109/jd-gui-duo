/*
 * © 2008-2019 Emmanuel Dupuy
 * © 2022-2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.indexer;

import org.jd.gui.api.API;
import org.jd.gui.api.model.Container;
import org.jd.gui.api.model.Indexes;
import org.jd.gui.spi.Indexer;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

public class ZipFileIndexerProvider extends AbstractIndexerProvider {

    @Override
    public String[] getSelectors() { return appendSelectors("*:file:*.zip", "*:file:*.jar", "*:file:*.war", "*:file:*.ear", "*:file:*.aar", "*:file:*.apk", "*:file:*.dex", "*:file:*.kar"); }

    @Override
    public void index(API api, Container.Entry entry, Indexes indexes, DoubleSupplier getProgressFunction, DoubleConsumer setProgressFunction, BooleanSupplier isCancelledFunction) {
        index(api, entry, indexes, getProgressFunction, setProgressFunction, isCancelledFunction, maximumDepth(api));
    }

    /** Indexes the files of the entry, and of its folders down to the depth. */
    private static void index(API api, Container.Entry entry, Indexes indexes, DoubleSupplier getProgressFunction, DoubleConsumer setProgressFunction, BooleanSupplier isCancelledFunction, int depth) {
        for (Container.Entry e : entry.getChildren().values()) {
            if (e.isDirectory()) {
                if (depth > 0) {
                    index(api, e, indexes, getProgressFunction, setProgressFunction, isCancelledFunction, depth - 1);
                }
            } else {
                Indexer indexer = api.getIndexer(e);

                if (indexer != null && !isCancelledFunction.getAsBoolean()) {
                    indexer.index(api, e, indexes, getProgressFunction, setProgressFunction, isCancelledFunction);
                }
            }
        }
    }
}
