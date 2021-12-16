/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */
package org.jdv0.gui.service.mainpanel;

import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.api.API;
import org.jd.gui.api.feature.*;
import org.jd.gui.api.model.Container;
import org.jd.gui.api.model.Indexes;
import org.jd.gui.spi.*;
import org.jd.gui.view.component.panel.TreeTabbedPanel;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.*;

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
    protected class ContainerPanel extends TreeTabbedPanel implements ContentIndexable, SourcesSavable {
        private static final long serialVersionUID = 1L;
        protected transient Container.Entry entry;

        public ContainerPanel(API api, Container container) {
            super(api, container.getRoot().getParent().getUri());

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
        public Indexes index(API api) {
            Map<String, Map<String, Collection>> map = new HashMap<>();
            MapMapCollectionWithDefault mapWithDefault = new MapMapCollectionWithDefault(map);

            // Index populating value automatically
            Indexes indexesWithDefault = mapWithDefault::get;

            // Index entry
            Indexer indexer = api.getIndexer(entry);

            if (indexer != null) {
                indexer.index(api, entry, indexesWithDefault);
            }

            // To prevent memory leaks, return an index without the 'populate' behaviour
            return map::get;
        }

        /** --- SourcesSavable --- */
        @Override
        public String getSourceFileName() {
            SourceSaver saver = api.getSourceSaver(entry);

            if (saver != null) {
                String path = saver.getSourcePath(entry);
                int index = path.lastIndexOf('/');
                return path.substring(index+1);
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
    }

    protected static class MapWrapper<K, V> implements Map<K, V> {
        protected Map<K, V> map;

        public MapWrapper(Map<K, V> map) { this.map = map; }

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
    protected static class MapCollectionWithDefault extends MapWrapper<String, Collection> {
        public MapCollectionWithDefault(Map<String, Collection> map) { super(map); }

        @Override
        public Collection get(Object o) {
            Collection value = map.get(o);
            if (value == null) {
                String key = o.toString();
                value=new ArrayList();
                map.put(key, value);
            }
            return value;
        }
    }

    @SuppressWarnings("rawtypes")
    protected static class MapMapCollectionWithDefault extends MapWrapper<String, Map<String, Collection>> {
        protected Map<String, Map<String, Collection>> wrappers = new HashMap<>();

        public MapMapCollectionWithDefault(Map<String, Map<String, Collection>> map) { super(map); }

        @Override
        public Map<String, Collection> get(Object o) {
            Map<String, Collection> value = wrappers.get(o);

            if (value == null) {
                String key = o.toString();
                Map<String, Collection> m = new HashMap<>();
                map.put(key, m);
                value=new MapCollectionWithDefault(m);
                wrappers.put(key, value);
            }

            return value;
        }

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = super.hashCode();
			result = prime * result + (wrappers == null ? 0 : wrappers.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (!super.equals(obj) || getClass() != obj.getClass()) {
				return false;
			}
			MapMapCollectionWithDefault other = (MapMapCollectionWithDefault) obj;
			return Objects.equals(wrappers, other.wrappers);
		}
    }
}
