/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */
package org.jdv1.gui.service.mainpanel;

import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.api.API;
import org.jd.gui.api.feature.ContentIndexable;
import org.jd.gui.api.feature.SourcesSavable;
import org.jd.gui.api.feature.UriGettable;
import org.jd.gui.api.model.Container;
import org.jd.gui.api.model.Indexes;
import org.jd.gui.spi.Indexer;
import org.jd.gui.spi.PanelFactory;
import org.jd.gui.spi.SourceSaver;
import org.jd.gui.spi.TreeNodeFactory;
import org.jd.gui.view.component.panel.TreeTabbedPanel;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

import javax.swing.JComponent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

public class ContainerPanelFactoryProvider implements PanelFactory {
    protected static final String[] TYPES = { "default" };

    @Override
    public String[] getTypes() { return TYPES; }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends JComponent & UriGettable> T make(API api, Container container) {
        return (T)new ContainerPanel(api, container);
    }

    @SuppressWarnings("rawtypes")
    protected static class ContainerPanel extends TreeTabbedPanel implements ContentIndexable, SourcesSavable, Closeable {
        private static final long serialVersionUID = 1L;
        private transient Container.Entry entry;
        private transient Container container;

        public ContainerPanel(API api, Container container) {
            super(api, container.getRoot().getParent().getUri());

            this.container = container;
            this.entry = container.getRoot().getParent();

            DefaultMutableTreeNode root = new DefaultMutableTreeNode();

            TreeNodeFactory factory;
            for (Container.Entry nextEntry : container.getRoot().getChildren().values()) {
                factory = api.getTreeNodeFactory(nextEntry);
                if (factory != null) {
                    root.add(factory.make(api, nextEntry));
                }
            }

            tree.setModel(new DefaultTreeModel(root));
        }

        /** --- ContentIndexable --- */
        @Override
        public Indexes index(API api, DoubleSupplier getProgressFunction, DoubleConsumer setProgressFunction, BooleanSupplier isCancelledFunction) {
            Map<String, Map<String, Collection>> map = new HashMap<>();
            DelegatedMapMapWithDefault mapWithDefault = new DelegatedMapMapWithDefault(map);

            // Index populating value automatically
            Indexes indexesWithDefault = mapWithDefault::get;

            // Index entry
            Indexer indexer = api.getIndexer(entry);

            if (indexer != null && !isCancelledFunction.getAsBoolean()) {
                indexer.index(api, entry, indexesWithDefault, getProgressFunction, setProgressFunction, isCancelledFunction);
            }

            // To prevent memory leaks, return an index without the 'populate' behaviour
            return map::get;
        }

        /** --- SourcesSavable --- */
        @Override
        public String getSourceFileName() {
            SourceSaver saver = api.getSourceSaver(entry);
            if (saver != null) {
                return saver.getSourcePath(entry);
            }
            return null;
        }

        @Override
        public int getFileCount() {
            SourceSaver saver = api.getSourceSaver(entry);
            return saver != null ? saver.getFileCount(api, entry) : 0;
        }

        @Override
        public void save(API api, Controller controller, Listener listener, Path path) {
            try {
                Path parentPath = path.getParent();

                if (parentPath != null && !Files.exists(parentPath)) {
                    Files.createDirectories(parentPath);
                }

                URI uri = path.toUri();
                URI archiveUri = new URI("jar:" + uri.getScheme(), uri.getHost(), uri.getPath() + "!/", null);

                try (FileSystem archiveFs = FileSystems.newFileSystem(archiveUri, Collections.singletonMap("create", "true"))) {
                    Path archiveRootPath = archiveFs.getPath("/");
                    SourceSaver saver = api.getSourceSaver(entry);

                    if (saver != null) {
                        saver.saveContent(
                            api,
                            controller::isCancelled,
                            listener::pathSaved,
                            archiveRootPath, archiveRootPath, entry);
                    }
                }
            } catch (URISyntaxException|IOException e) {
                assert ExceptionUtil.printStackTrace(e);
            }
        }

        @Override
        public void close() throws IOException {
            if (container instanceof Closeable c) {
                c.close();
            }
        }
    }

    protected static class DelegatedMap<K, V> implements Map<K, V> {

        private Map<K, V> map;

        public DelegatedMap(Map<K, V> map) { this.map = map; }

        @Override
        public int size() { return map.size(); }
        @Override
        public boolean isEmpty() { return map.isEmpty(); }
        @Override
        public boolean containsKey(Object o) { return map.containsKey(o); }
        @Override
        public boolean containsValue(Object o) { return map.containsValue(o); }
        @Override
        public V get(Object o) { return map.get(o); }
        @Override
        public V put(K k, V v) { return map.put(k, v); }
        @Override
        public V remove(Object o) { return map.remove(o); }
        @Override
        public void putAll(Map<? extends K, ? extends V> map) { this.map.putAll(map); }
        @Override
        public void clear() { map.clear(); }
        @Override
        public Set<K> keySet() { return map.keySet(); }
        @Override
        public Collection<V> values() { return map.values(); }
        @Override
        public Set<Entry<K, V>> entrySet() { return map.entrySet(); }
        @Override
        public boolean equals(Object o) { return map.equals(o); }
        @Override
        public int hashCode() { return map.hashCode(); }
    }

    @SuppressWarnings("rawtypes")
    protected static class DelegatedMapWithDefault extends DelegatedMap<String, Collection> {

        public DelegatedMapWithDefault(Map<String, Collection> map) { super(map); }

        @Override
        public Collection get(Object o) {
            Collection value = super.get(o);
            if (value == null) {
                String key = o.toString();
                value=new ArrayList<>();
                put(key, value);
            }
            return value;
        }
    }

    @SuppressWarnings("rawtypes")
    protected static class DelegatedMapMapWithDefault extends DelegatedMap<String, Map<String, Collection>> {

        private Map<String, Map<String, Collection>> wrappers = new HashMap<>();

        public DelegatedMapMapWithDefault(Map<String, Map<String, Collection>> map) { super(map); }

        @Override
        public Map<String, Collection> get(Object o) {
            Map<String, Collection> value = wrappers.get(o);

            if (value == null) {
                String key = o.toString();
                Map<String, Collection> m = new HashMap<>();
                put(key, m);
                value=new DelegatedMapWithDefault(m);
                wrappers.put(key, value);
            }

            return value;
        }

        @Override
        public int hashCode() {
            int result = super.hashCode();
            return 31 * result + (wrappers == null ? 0 : wrappers.hashCode());
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!super.equals(obj) || getClass() != obj.getClass()) {
                return false;
            }
            DelegatedMapMapWithDefault other = (DelegatedMapMapWithDefault) obj;
            return Objects.equals(wrappers, other.wrappers);
        }
    }
}
