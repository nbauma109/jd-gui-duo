/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.model.container;

import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.api.API;
import org.jd.gui.api.model.Container;
import org.jd.gui.model.container.entry.path.SimpleEntryPath;
import org.jd.gui.spi.ContainerFactory;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.nio.file.FileSystem;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class GenericContainer implements Container {
    protected static final long TIMESTAMP = System.currentTimeMillis();

    protected static AtomicLong tmpFileCounter = new AtomicLong(0);

    protected API api;
    protected int rootNameCount;
    protected Container.Entry root;

    public GenericContainer(API api, Container.Entry parentEntry, Path rootPath) {
        try {
            URI uri = parentEntry.getUri();

            this.api = api;
            this.rootNameCount = rootPath.getNameCount();
            this.root = new Entry(parentEntry, rootPath, new URI(uri.getScheme(), uri.getHost(), uri.getPath() + "!/", null)) {
                @Override
                public Entry newChildEntry(Path fsPath) {
                    return new Entry(parent, fsPath, null);
                }
            };
        } catch (URISyntaxException e) {
            assert ExceptionUtil.printStackTrace(e);
        }
    }

    @Override
    public String getType() { return "generic"; }
    @Override
    public Container.Entry getRoot() { return root; }

    protected class Entry implements Container.Entry {
        protected Container.Entry parent;
        protected Path fsPath;
        protected String strPath;
        protected URI uri;
        protected Boolean isDirectory;
        protected Map<Container.EntryPath, Container.Entry> children;

        public Entry(Container.Entry parent, Path fsPath, URI uri) {
            this.parent = parent;
            this.fsPath = fsPath;
            this.strPath = null;
            this.uri = uri;
            this.isDirectory = null;
            this.children = null;
        }

        public Entry newChildEntry(Path fsPath) { return new Entry(this, fsPath, null); }

        @Override
        public Container getContainer() { return GenericContainer.this; }
        @Override
        public Container.Entry getParent() { return parent; }

        @Override
        public URI getUri() {
            if (uri == null) {
                try {
                    URI rootUri = root.getUri();
                    uri = new URI(rootUri.getScheme(), rootUri.getHost(), rootUri.getPath() + getPath(), null);
                } catch (URISyntaxException e) {
                    assert ExceptionUtil.printStackTrace(e);
                }
            }
            return uri;
        }

        @Override
        public String getPath() {
            if (strPath == null) {
                int nameCount = fsPath.getNameCount();

                if (rootNameCount == nameCount) {
                    strPath = "";
                } else {
                    strPath = fsPath.subpath(rootNameCount, nameCount).toString().replace(fsPath.getFileSystem().getSeparator(), "/");

                    int strPathLength = strPath.length();

                    if ((strPathLength > 0) && strPath.charAt(strPathLength-1) == '/') {
                        // Cut last separator
                        strPath = strPath.substring(0, strPathLength-1);
                    }
                }
            }
            return strPath;
        }

        @Override
        public boolean isDirectory() {
            if (isDirectory == null) {
                isDirectory = Boolean.valueOf(Files.isDirectory(fsPath));
            }
            return isDirectory;
        }

        @Override
        public long length() {
            try {
                return Files.size(fsPath);
            } catch (IOException e) {
                assert ExceptionUtil.printStackTrace(e);
                return -1L;
            }
        }

        @Override
        public InputStream getInputStream() {
            try {
                return Files.newInputStream(fsPath);
            } catch (IOException e) {
                assert ExceptionUtil.printStackTrace(e);
                return null;
            }
        }

        @Override
        public Map<Container.EntryPath, Container.Entry> getChildren() {
            if (children == null) {
                try {
                    if (Files.isDirectory(fsPath)) {
                        children = loadChildrenFromDirectoryEntry();
                    } else {
                        children = loadChildrenFromFileEntry();
                    }
                } catch (IOException e) {
                    assert ExceptionUtil.printStackTrace(e);
                }
            }
            return children;
        }

        protected NavigableMap<Container.EntryPath, Container.Entry> loadChildrenFromDirectoryEntry() throws IOException {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(fsPath)) {
                NavigableMap<Container.EntryPath, Container.Entry> sortedChildren = new TreeMap<>(ContainerEntryComparator.COMPARATOR);
                int parentNameCount = fsPath.getNameCount();

                for (Path subPath : stream) {
                    if (subPath.getNameCount() > parentNameCount) {
                        Container.Entry newChildEntry = newChildEntry(subPath);
                        Container.EntryPath newChildEntryPath = new SimpleEntryPath(newChildEntry.getPath(), newChildEntry.isDirectory());
                        sortedChildren.put(newChildEntryPath, newChildEntry);
                    }
                }

                return Collections.unmodifiableNavigableMap(sortedChildren);
            }
        }

        @SuppressWarnings("resource")
        protected Map<Container.EntryPath, Container.Entry> loadChildrenFromFileEntry() throws IOException {
            StringBuilder suffix = new StringBuilder(".").append(TIMESTAMP).append('.').append(tmpFileCounter.getAndIncrement()).append('.').append(fsPath.getFileName().toString());
            File tmpFile = File.createTempFile("jd-gui.tmp.", suffix.toString());
            Path tmpPath = Paths.get(tmpFile.toURI());

            Files.delete(tmpFile.toPath());
            tmpFile.deleteOnExit();
            Files.copy(fsPath, tmpPath);

            @SuppressWarnings("all")
            // Resource leak : The file system cannot be closed until the application is shutdown
            FileSystem subFileSystem = FileSystems.newFileSystem(tmpPath, (ClassLoader)null);

            Iterator<Path> rootDirectories = subFileSystem.getRootDirectories().iterator();

            if (rootDirectories.hasNext()) {
                Path rootPath = rootDirectories.next();
                ContainerFactory containerFactory = api.getContainerFactory(rootPath);

                if (containerFactory != null) {
                    Container container = containerFactory.make(api, this, rootPath);

                    if (container != null) {
                        return container.getRoot().getChildren();
                    }
                }
            }

            Files.delete(tmpFile.toPath());
            return Collections.emptyMap();
        }
    }
}
