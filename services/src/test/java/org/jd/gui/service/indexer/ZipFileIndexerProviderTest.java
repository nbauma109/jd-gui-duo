/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.indexer;

import org.jd.gui.api.API;
import org.jd.gui.api.model.Container;
import org.jd.gui.model.container.entry.path.DirectoryEntryPath;
import org.jd.gui.model.container.entry.path.FileEntryPath;
import org.jd.gui.spi.Indexer;
import org.jd.gui.util.decompiler.GuiPreferences;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The files of an archive are indexed down to the depth of folders that the preferences set. */
class ZipFileIndexerProviderTest {

    private static final class FakeEntry implements Container.Entry {
        final String path;
        final boolean directory;
        final Map<Container.EntryPath, Container.Entry> children = new LinkedHashMap<>();

        FakeEntry(String path, boolean directory) {
            this.path = path;
            this.directory = directory;
        }

        FakeEntry add(FakeEntry child) {
            children.put(child.directory ? new DirectoryEntryPath(child.path) : new FileEntryPath(child.path), child);
            return this;
        }

        public boolean isDirectory() { return directory; }
        public String getPath() { return path; }
        public Map<Container.EntryPath, Container.Entry> getChildren() { return children; }
        public Container getContainer() { return null; }
        public Container.Entry getParent() { return null; }
        public URI getUri() { return URI.create("file:/x.jar!/" + path); }
        public long length() { return 0; }
        public long compressedLength() { return 0; }
        public InputStream getInputStream() { return InputStream.nullInputStream(); }
    }

    /** An archive with a file at its root and one in each of 17 folders, each folder inside the one before. */
    private static FakeEntry archive() {
        FakeEntry folder = null;
        for (int level = 17; level > 0; level--) {
            String path = "d/".repeat(level - 1) + "d";
            FakeEntry current = new FakeEntry(path, true).add(new FakeEntry(path + "/" + level, false));
            if (folder != null) {
                current.add(folder);
            }
            folder = current;
        }
        return new FakeEntry("x.jar", false).add(new FakeEntry("0", false)).add(folder);
    }

    @ParameterizedTest(name = "with a depth of {0}, the files are indexed down to level {1}")
    @CsvSource({"1, 1", "3, 3", "30, 17", ", 15", "deep, 15"})
    void theFilesAreIndexedDownToTheDepth(String preference, int deepest) {
        Map<String, String> preferences = new HashMap<>();
        preferences.put(GuiPreferences.MAXIMUM_DEPTH_KEY, preference);
        List<Integer> levels = new ArrayList<>();
        Indexer recorder = (Indexer) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Indexer.class}, (proxy, method, args) -> {
            if ("index".equals(method.getName())) {
                String path = ((Container.Entry) args[1]).getPath();
                levels.add(Integer.parseInt(path.substring(path.lastIndexOf('/') + 1)));
            }
            return null;
        });
        API api = (API) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {API.class}, (proxy, method, args) -> switch (method.getName()) {
            case "getIndexer" -> recorder;
            case "getPreferences" -> preferences;
            default -> null;
        });

        new ZipFileIndexerProvider().index(api, archive(), name -> null, () -> 0, value -> { }, () -> false);

        assertThat(levels).hasSize(deepest + 1).allMatch(level -> level <= deepest).contains(0, deepest);
    }
}
