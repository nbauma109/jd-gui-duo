/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jdv1.gui.service.indexer;

import org.jd.gui.api.API;
import org.jd.gui.api.model.Container;
import org.jd.gui.api.model.Indexes;
import org.jd.gui.model.container.entry.path.DirectoryEntryPath;
import org.jd.gui.service.indexer.AbstractIndexerProvider;
import org.jd.gui.spi.Indexer;

import java.util.Collection;
import java.util.Map;

public class JavaModuleFileIndexerProvider extends AbstractIndexerProvider {

    @Override
    public String[] getSelectors() { return appendSelectors("*:file:*.jmod"); }

    @SuppressWarnings("rawtypes")
    @Override
    public void index(API api, Container.Entry entry, Indexes indexes) {
        DirectoryEntryPath classesDir = new DirectoryEntryPath("classes");
        Container.Entry e = entry.getChildren().get(classesDir);
        if (e != null) {
            Map<String, Collection> packageDeclarationIndex = indexes.getIndex("packageDeclarations");

            // Index module-info, packages and CLASS files
            index(api, e, indexes, packageDeclarationIndex);
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    protected static void index(API api, Container.Entry entry, Indexes indexes, Map<String, Collection> packageDeclarationIndex) {
        for (Container.Entry e : entry.getChildren().values()) {
            if (e.isDirectory()) {
                String path = e.getPath();

                if (!path.startsWith("classes/META-INF")) {
                    packageDeclarationIndex.get(path.substring(8)).add(e); // 8 = "classes/".length()
                }

                index(api, e, indexes, packageDeclarationIndex);
            } else {
                Indexer indexer = api.getIndexer(e);

                if (indexer != null) {
                    indexer.index(api, e, indexes);
                }
            }
        }
    }
}
