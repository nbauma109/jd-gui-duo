/*
 * Copyright (c) 2008-2026 Emmanuel Dupuy and other contributors.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.model.container;

import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.api.model.Container;
import org.jd.gui.model.container.entry.path.SimpleEntryPath;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

public class ConvertedJarContainer implements Container {

    private final RootEntry root;

    public ConvertedJarContainer(Container.Entry parentEntry, Map<String, byte[]> classFiles) {
        this.root = new RootEntry(parentEntry, createRootUri(parentEntry));
        load(classFiles);
    }

    private static URI createRootUri(Container.Entry parentEntry) {
        try {
            URI fileUri = parentEntry.getUri();
            return new URI(fileUri.getScheme(), fileUri.getHost(), fileUri.getPath() + "!/", null);
        } catch (URISyntaxException e) {
            assert ExceptionUtil.printStackTrace(e);
            return parentEntry.getUri();
        }
    }

    private void load(Map<String, byte[]> classFiles) {
        for (Map.Entry<String, byte[]> classFile : classFiles.entrySet()) {
            addClassFile(classFile.getKey(), classFile.getValue());
        }
        root.freeze();
    }

    private void addClassFile(String path, byte[] bytes) {
        int lastSlash = path.lastIndexOf('/');
        Entry parent = root;

        if (lastSlash != -1) {
            StringBuilder packagePath = new StringBuilder();
            String[] segments = path.substring(0, lastSlash).split("/");
            for (String segment : segments) {
                if (!packagePath.isEmpty()) {
                    packagePath.append('/');
                }
                packagePath.append(segment);
                parent = parent.getOrCreateDirectory(packagePath.toString());
            }
        }

        parent.putChild(new ClassEntry(parent, path, bytes));
    }

    @Override
    public String getType() {
        return "jar";
    }

    @Override
    public Container.Entry getRoot() {
        return root;
    }

    private URI createChildUri(String entryPath) {
        if (entryPath.isEmpty()) {
            return root.uri;
        }
        try {
            return new URI(root.uri.getScheme(), root.uri.getHost(), root.uri.getPath() + entryPath, null);
        } catch (URISyntaxException e) {
            assert ExceptionUtil.printStackTrace(e);
            return root.uri;
        }
    }

    private abstract class Entry implements Container.Entry {
        private final Container.Entry parent;
        private final String path;
        private final boolean directory;
        private final URI uri;
        private Map<Container.EntryPath, Container.Entry> children = Collections.emptyMap();

        private Entry(Container.Entry parent, String path, boolean directory, URI uri) {
            this.parent = parent;
            this.path = path;
            this.directory = directory;
            this.uri = uri;
        }

        @Override
        public Container getContainer() {
            return ConvertedJarContainer.this;
        }

        @Override
        public Container.Entry getParent() {
            return parent;
        }

        @Override
        public URI getUri() {
            return uri;
        }

        @Override
        public String getPath() {
            return path;
        }

        @Override
        public boolean isDirectory() {
            return directory;
        }

        @Override
        public long compressedLength() {
            return length();
        }

        @Override
        public Map<Container.EntryPath, Container.Entry> getChildren() {
            return children;
        }

        private void setChildren(Map<Container.EntryPath, Container.Entry> children) {
            this.children = children;
        }

        private DirectoryEntry getOrCreateDirectory(String directoryPath) {
            Container.Entry existing = children.get(new SimpleEntryPath(directoryPath, true));
            if (existing instanceof DirectoryEntry directoryEntry) {
                return directoryEntry;
            }

            DirectoryEntry newEntry = new DirectoryEntry(this, directoryPath);
            putChild(newEntry);
            return newEntry;
        }

        private void putChild(Entry child) {
            NavigableMap<Container.EntryPath, Container.Entry> updatedChildren = new TreeMap<>(ContainerEntryComparator.COMPARATOR);
            updatedChildren.putAll(children);
            updatedChildren.put(new SimpleEntryPath(child), child);
            setChildren(updatedChildren);
        }

        void freeze() {
            if (children.isEmpty()) {
                children = Collections.emptyMap();
                return;
            }

            for (Container.Entry child : List.copyOf(children.values())) {
                if (child instanceof Entry entry) {
                    entry.freeze();
                }
            }

            NavigableMap<Container.EntryPath, Container.Entry> frozenChildren = new TreeMap<>(ContainerEntryComparator.COMPARATOR);
            frozenChildren.putAll(children);
            children = Collections.unmodifiableNavigableMap(frozenChildren);
        }
    }

    private final class RootEntry extends Entry {
        private final URI uri;

        private RootEntry(Container.Entry parent, URI uri) {
            super(parent, "", true, uri);
            this.uri = uri;
        }

        @Override
        public long length() {
            return 0L;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(new byte[0]);
        }
    }

    private final class DirectoryEntry extends Entry {
        private DirectoryEntry(Container.Entry parent, String path) {
            super(parent, path, true, createChildUri(path));
        }

        @Override
        public long length() {
            return 0L;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(new byte[0]);
        }
    }

    private final class ClassEntry extends Entry {
        private final byte[] bytes;

        private ClassEntry(Container.Entry parent, String path, byte[] bytes) {
            super(parent, path, false, createChildUri(path));
            this.bytes = bytes;
        }

        @Override
        public long length() {
            return bytes.length;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(bytes);
        }
    }
}
