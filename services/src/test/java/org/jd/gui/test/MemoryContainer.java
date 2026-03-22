package org.jd.gui.test;

import org.jd.gui.api.model.Container;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MemoryContainer implements Container {
    private final MemoryEntry root = new MemoryEntry(this, null, "", true);
    private final Map<String, MemoryEntry> entriesByPath = new LinkedHashMap<>();

    public MemoryContainer() {
        entriesByPath.put("", root);
    }

    public void addFile(String path) {
        String[] segments = path.split("/");
        MemoryEntry parent = root;
        StringBuilder currentPath = new StringBuilder();

        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                currentPath.append('/');
            }
            currentPath.append(segments[i]);

            String entryPath = currentPath.toString();
            boolean directory = i < segments.length - 1;
            MemoryEntry entry = entriesByPath.get(entryPath);

            if (entry == null) {
                entry = new MemoryEntry(this, parent, entryPath, directory);
                parent.children.put(new MemoryEntryPath(entryPath, directory), entry);
                entriesByPath.put(entryPath, entry);
            }

            parent = entry;
        }
    }

    public Container.Entry getEntry(String path) {
        return entriesByPath.get(path);
    }

    @Override
    public String getType() {
        return "test";
    }

    @Override
    public Container.Entry getRoot() {
        return root;
    }

    private static final class MemoryEntry implements Container.Entry {
        private final MemoryContainer container;
        private final MemoryEntry parent;
        private final String path;
        private final boolean directory;
        private final Map<Container.EntryPath, Container.Entry> children = new LinkedHashMap<>();

        private MemoryEntry(MemoryContainer container, MemoryEntry parent, String path, boolean directory) {
            this.container = container;
            this.parent = parent;
            this.path = path;
            this.directory = directory;
        }

        @Override
        public Container getContainer() {
            return container;
        }

        @Override
        public MemoryEntry getParent() {
            return parent;
        }

        @Override
        public URI getUri() {
            return URI.create("memory:/" + path);
        }

        @Override
        public long length() {
            return 0;
        }

        @Override
        public long compressedLength() {
            return 0;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public Map<Container.EntryPath, Container.Entry> getChildren() {
            return children;
        }

        @Override
        public boolean isDirectory() {
            return directory;
        }

        @Override
        public String getPath() {
            return path;
        }
    }

    private record MemoryEntryPath(String path, boolean directory) implements Container.EntryPath {
        @Override
        public boolean isDirectory() {
            return directory;
        }

        @Override
        public String getPath() {
            return path;
        }
    }
}
