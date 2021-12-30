/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jdv1.gui.model.container;

import org.jd.gui.api.model.Container;
import org.jd.gui.model.container.entry.path.SimpleEntryPath;

import java.io.InputStream;
import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class DelegatingFilterContainer implements Container {
    protected static final URI DEFAULT_ROOT_URI = URI.create("file:.");

    private final Container container;
    private final DelegatedEntry root;

    private final Set<URI> validEntries = new HashSet<>();
    private final Map<URI, DelegatedEntry> uriToDelegatedEntry = new HashMap<>();
    private final Map<URI, DelegatedContainer> uriToDelegatedContainer = new HashMap<>();

    public DelegatingFilterContainer(Container container, Collection<Entry> entries) {
        this.container = container;
        this.root = getDelegatedEntry(container.getRoot());

        for (Entry entry : entries) {
            while (entry != null && !validEntries.contains(entry.getUri())) {
                validEntries.add(entry.getUri());
                entry = entry.getParent();
            }
        }
    }

    @Override
    public String getType() { return container.getType(); }
    @Override
    public Container.Entry getRoot() { return root; }

    public Container.Entry getEntry(URI uri) { return uriToDelegatedEntry.get(uri); }
    public Set<URI> getUris() { return validEntries; }

    protected DelegatedEntry getDelegatedEntry(Container.Entry entry) {
        URI uri = entry.getUri();
        return uriToDelegatedEntry.computeIfAbsent(uri, k -> new DelegatedEntry(entry));
    }

    protected DelegatedContainer getDelegatedContainer(Container container) {
        Entry localRoot = container.getRoot();
        URI uri = localRoot == null ? DEFAULT_ROOT_URI : localRoot.getUri();
        return uriToDelegatedContainer.computeIfAbsent(uri, k -> new DelegatedContainer(container));
    }

    protected class DelegatedEntry implements Entry, Comparable<DelegatedEntry> {
        private final Entry entry;
        private Map<Container.EntryPath, Container.Entry> children;

        public DelegatedEntry(Entry entry) {
            this.entry = entry;
        }

        @Override
        public Container getContainer() { return getDelegatedContainer(entry.getContainer()); }
        @Override
        public Entry getParent() { return getDelegatedEntry(entry.getParent()); }
        @Override
        public URI getUri() { return entry.getUri(); }
        @Override
        public String getPath() { return entry.getPath(); }
        @Override
        public boolean isDirectory() { return entry.isDirectory(); }
        @Override
        public long length() { return entry.length(); }
        @Override
        public long compressedLength() { return entry.compressedLength(); }
        @Override
        public InputStream getInputStream() { return entry.getInputStream(); }

        @Override
        public Map<Container.EntryPath, Container.Entry> getChildren() {
            if (children == null) {
                children = entry.getChildren().values().stream().filter(child -> validEntries.contains(child.getUri()))
                        .collect(Collectors.toMap(SimpleEntryPath::new, this::getDelegEntry));
            }
            return children;
        }

        private DelegatedEntry getDelegEntry(Container.Entry entry) {
            return getDelegatedEntry(entry);
        }

        @Override
        public int compareTo(DelegatedEntry other) {
            if (entry.isDirectory()) {
                if (!other.isDirectory()) {
                    return -1;
                }
            } else if (other.isDirectory()) {
                return 1;
            }
            return entry.getPath().compareTo(other.getPath());
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            return obj instanceof DelegatedEntry de && compareTo(de) == 0;
        }

        @Override
        public int hashCode() {
            return Objects.hash(entry.isDirectory(), entry.getPath());
        }
    }

    protected class DelegatedContainer implements Container {
        private final Container container;

        public DelegatedContainer(Container container) {
            this.container = container;
        }

        @Override
        public String getType() { return container.getType(); }
        @Override
        public Entry getRoot() { return getDelegatedEntry(container.getRoot()); }
    }
}
