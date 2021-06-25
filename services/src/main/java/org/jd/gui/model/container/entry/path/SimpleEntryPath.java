package org.jd.gui.model.container.entry.path;

import org.jd.gui.api.model.Container.EntryPath;

import java.util.Objects;

public class SimpleEntryPath implements EntryPath {

    private String path;
    private boolean directory;

    public SimpleEntryPath(EntryPath entryPath) {
        this(entryPath.getPath(), entryPath.isDirectory());
    }

    public SimpleEntryPath(String path, boolean directory) {
        this.path = path;
        this.directory = directory;
    }

    @Override
    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    @Override
    public boolean isDirectory() {
        return directory;
    }

    public void setDirectory(boolean directory) {
        this.directory = directory;
    }

    @Override
    public int hashCode() {
        return Objects.hash(directory, path);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        SimpleEntryPath other = (SimpleEntryPath) obj;
        return directory == other.directory && Objects.equals(path, other.path);
    }

    @Override
    public String toString() {
        return "SimpleEntryPath [path=" + path + ", directory=" + directory + "]";
    }
}
