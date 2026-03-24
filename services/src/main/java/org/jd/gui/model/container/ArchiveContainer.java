package org.jd.gui.model.container;

import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.api.model.Container;
import org.jd.gui.model.container.entry.path.SimpleEntryPath;
import org.jd.gui.util.archive.ArchiveIO;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

public final class ArchiveContainer implements Container {

    private static final String TYPE = "generic";
    private static final String ROOT_PATH = "";
    private static final String ARCHIVE_SUFFIX = "!/";
    private static final byte[] EMPTY_BYTES = new byte[0];

    private final ArchiveEntry root;
    private final URI rootUri;
    private final Map<String, ArchiveEntry> entriesByPath = new LinkedHashMap<>();

    public ArchiveContainer(Container.Entry parentEntry, ArchiveIO.ArchiveSnapshot snapshot) {
        this.rootUri = createRootUri(parentEntry.getUri());
        this.root = new ArchiveEntry(this, parentEntry, ROOT_PATH, true, EMPTY_BYTES, 0L, rootUri);
        this.entriesByPath.put(ROOT_PATH, root);
        snapshot.entries().forEach(this::addEntry);
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public Container.Entry getRoot() {
        return root;
    }

    private void addEntry(String path, ArchiveIO.ArchiveItem item) {
        if (path.isEmpty()) {
            return;
        }
        ArchiveEntry parent = ensureDirectory(parentPath(path));
        ArchiveEntry existing = entriesByPath.get(path);

        if (existing == null) {
            ArchiveEntry entry = new ArchiveEntry(
                this,
                parent,
                path,
                item.directory(),
                item.directory() ? EMPTY_BYTES : item.bytes(),
                item.compressedLength(),
                createChildUri(path)
            );
            entriesByPath.put(path, entry);
            parent.addChild(entry);
        }
    }

    private ArchiveEntry ensureDirectory(String path) {
        if (path.isEmpty()) {
            return root;
        }
        ArchiveEntry existing = entriesByPath.get(path);
        if (existing != null) {
            return existing;
        }

        ArchiveEntry parent = ensureDirectory(parentPath(path));
        ArchiveEntry entry = new ArchiveEntry(this, parent, path, true, EMPTY_BYTES, 0L, createChildUri(path));
        entriesByPath.put(path, entry);
        parent.addChild(entry);
        return entry;
    }

    private String parentPath(String path) {
        int lastSlashIndex = path.lastIndexOf('/');
        return lastSlashIndex == -1 ? ROOT_PATH : path.substring(0, lastSlashIndex);
    }

    private URI createChildUri(String path) {
        try {
            return new URI(rootUri.getScheme(), rootUri.getHost(), rootUri.getPath() + path, null);
        } catch (URISyntaxException e) {
            assert ExceptionUtil.printStackTrace(e);
            // Fallback to a safe URI to avoid IllegalArgumentException from URI.create(...)
            return rootUri;
        }
    }

    private static URI createRootUri(URI parentUri) {
        try {
            return new URI(parentUri.getScheme(), parentUri.getHost(), parentUri.getPath() + ARCHIVE_SUFFIX, null);
        } catch (URISyntaxException e) {
            assert ExceptionUtil.printStackTrace(e);
            try {
                return URI.create(parentUri.toString() + ARCHIVE_SUFFIX);
            } catch (IllegalArgumentException ex) {
                assert ExceptionUtil.printStackTrace(ex);
                // Fallback to a safe URI to avoid propagating IllegalArgumentException
                return parentUri;
            }
        }
    }

    private static final class ArchiveEntry implements Container.Entry {
        private final ArchiveContainer container;
        private final Container.Entry parent;
        private final String path;
        private final boolean directory;
        private final byte[] bytes;
        private final long compressedLength;
        private final URI uri;

        private Map<Container.EntryPath, Container.Entry> children;
        private boolean nestedChildrenLoaded;

        private ArchiveEntry(
            ArchiveContainer container,
            Container.Entry parent,
            String path,
            boolean directory,
            byte[] bytes,
            long compressedLength,
            URI uri
        ) {
            this.container = container;
            this.parent = parent;
            this.path = path;
            this.directory = directory;
            this.bytes = bytes;
            this.compressedLength = compressedLength;
            this.uri = uri;
            this.children = directory ? new TreeMap<>(ContainerEntryComparator.COMPARATOR) : Collections.emptyMap();
            this.nestedChildrenLoaded = directory;
        }

        @Override
        public Container getContainer() {
            return container;
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
        public long length() {
            return bytes.length;
        }

        @Override
        public long compressedLength() {
            return compressedLength > 0 ? compressedLength : length();
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public Map<Container.EntryPath, Container.Entry> getChildren() {
            if (!directory && !nestedChildrenLoaded) {
                nestedChildrenLoaded = true;
                if (ArchiveIO.hasSupportedArchiveExtension(path)) {
                    try {
                        ArchiveIO.ArchiveSnapshot snapshot = ArchiveIO.readArchive(fileName(), bytes);
                        children = new ArchiveContainer(this, snapshot).getRoot().getChildren();
                    } catch (IOException e) {
                        assert ExceptionUtil.printStackTrace(e);
                        children = Collections.emptyMap();
                    }
                }
            }
            return directory ? Collections.unmodifiableMap(children) : children;
        }

        @Override
        public boolean isDirectory() {
            return directory;
        }

        @Override
        public String getPath() {
            return path;
        }

        private void addChild(ArchiveEntry child) {
            @SuppressWarnings("unchecked")
            NavigableMap<Container.EntryPath, Container.Entry> mutableChildren =
                (NavigableMap<Container.EntryPath, Container.Entry>) children;
            mutableChildren.put(new SimpleEntryPath(child.getPath(), child.isDirectory()), child);
        }

        private String fileName() {
            int lastSlashIndex = path.lastIndexOf('/');
            return lastSlashIndex == -1 ? path : path.substring(lastSlashIndex + 1);
        }
    }
}
