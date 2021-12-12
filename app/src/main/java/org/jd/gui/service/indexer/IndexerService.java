/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.indexer;

import org.jd.gui.api.model.Container;
import org.jd.gui.service.extension.ExtensionService;
import org.jd.gui.spi.Indexer;

import java.util.*;

public class IndexerService {
    protected static final IndexerService INDEXER_SERVICE = new IndexerService();

    public static IndexerService getInstance() { return INDEXER_SERVICE; }

    protected Map<String, Indexers> mapProviders = new HashMap<>();

    protected IndexerService() {
        Collection<Indexer> providers = ExtensionService.getInstance().load(Indexer.class);

        for (Indexer provider : providers) {
            for (String selector : provider.getSelectors()) {
                mapProviders.computeIfAbsent(selector, k -> new Indexers()).add(provider);
            }
        }
    }

    public Indexer get(Container.Entry entry) {
        Indexer indexer = get(entry.getContainer().getType(), entry);
        return indexer != null ? indexer : get("*", entry);
    }

    protected Indexer get(String containerType, Container.Entry entry) {
        String path = entry.getPath();
        String type = entry.isDirectory() ? "dir" : "file";
        String prefix = containerType + ':' + type;
        Indexer indexer = null;
        Indexers indexers = mapProviders.get(prefix + ':' + path);

        if (indexers != null) {
            indexer = indexers.match(path);
        }

        if (indexer == null) {
            int lastSlashIndex = path.lastIndexOf('/');
            String name = path.substring(lastSlashIndex+1);

            indexers = mapProviders.get(prefix + ":*/" + name);
            if (indexers != null) {
                indexer = indexers.match(path);
            }

            if (indexer == null) {
                int index = name.lastIndexOf('.');

                if (index != -1) {
                    String extension = name.substring(index + 1);

                    indexers = mapProviders.get(prefix + ":*." + extension);
                    if (indexers != null) {
                        indexer = indexers.match(path);
                    }
                }

                if (indexer == null) {
                    indexers = mapProviders.get(prefix + ":*");
                    if (indexers != null) {
                        indexer = indexers.match(path);
                    }
                }
            }
        }

        return indexer;
    }

    protected static class Indexers {
        protected Map<String, Indexer> indexerMap = new HashMap<>();
        protected Indexer defaultIndexer;

        public void add(Indexer indexer) {
            if (indexer.getPathPattern() != null) {
                indexerMap.put(indexer.getPathPattern().pattern(), indexer);
            } else {
                defaultIndexer = indexer;
            }
        }

        public Indexer match(String path) {
            for (Indexer indexer : indexerMap.values()) {
                if (indexer.getPathPattern().matcher(path).matches()) {
                    return indexer;
                }
            }
            return defaultIndexer;
        }
    }
}
