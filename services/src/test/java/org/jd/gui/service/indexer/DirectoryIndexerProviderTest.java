/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.indexer;

import org.jd.gui.api.API;
import org.jd.gui.api.model.Container;
import org.jd.gui.api.model.Indexes;
import org.jd.gui.spi.Indexer;
import org.junit.jupiter.api.Test;
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
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/** The depth limit of the exploration of a directory: a file N folders down is indexed when the depth is greater than N. */
class DirectoryIndexerProviderTest {

    private static final String KEY = "DirectoryIndexerPreferences.maximumDepth";

    /** A file or a folder of a fake container. */
    private static final class FakeEntry implements Container.Entry {
        private final String path;
        private final boolean directory;
        private final Map<Container.EntryPath, Container.Entry> children = new LinkedHashMap<>();

        FakeEntry(String path, boolean directory) {
            this.path = path;
            this.directory = directory;
        }

        FakeEntry add(FakeEntry child) {
            children.put(child, child);
            return this;
        }

        @Override
        public boolean isDirectory() {
            return directory;
        }

        @Override
        public String getPath() {
            return path;
        }

        @Override
        public Map<Container.EntryPath, Container.Entry> getChildren() {
            return children;
        }

        @Override
        public Container getContainer() {
            return null;
        }

        @Override
        public Container.Entry getParent() {
            return null;
        }

        @Override
        public URI getUri() {
            return URI.create("file:/" + path);
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
            return InputStream.nullInputStream();
        }
    }

    /** root: f0, a/f1, a/b/f2, a/b/c/f3. */
    private static FakeEntry tree() {
        FakeEntry c = new FakeEntry("a/b/c", true).add(new FakeEntry("a/b/c/f3", false));
        FakeEntry b = new FakeEntry("a/b", true).add(new FakeEntry("a/b/f2", false)).add(c);
        FakeEntry a = new FakeEntry("a", true).add(new FakeEntry("a/f1", false)).add(b);
        return new FakeEntry("", true).add(new FakeEntry("f0", false)).add(a);
    }

    private final List<String> indexed = new ArrayList<>();

    private API api(Map<String, String> preferences) {
        Indexer recorder = (Indexer) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Indexer.class}, (proxy, method, args) -> {
            if ("index".equals(method.getName())) {
                indexed.add(((Container.Entry) args[1]).getPath());
            }
            return null;
        });
        return (API) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {API.class}, (proxy, method, args) -> switch (method.getName()) {
            case "getPreferences" -> preferences;
            case "getIndexer" -> recorder;
            default -> null;
        });
    }

    private void index(Map<String, String> preferences, Integer depth) {
        DoubleSupplier progress = () -> 0;
        DoubleConsumer setProgress = value -> { };
        Indexes indexes = name -> null;
        if (depth == null) {
            new DirectoryIndexerProvider().index(api(preferences), tree(), indexes, progress, setProgress, () -> false);
        } else {
            new DirectoryIndexerProvider().index(api(preferences), tree(), indexes, progress, setProgress, () -> false, depth);
        }
    }

    @ParameterizedTest(name = "depth {0} indexes {1}")
    @CsvSource({"0, ''", "1, f0", "2, 'f0,a/f1'", "3, 'f0,a/f1,a/b/f2'", "4, 'f0,a/f1,a/b/f2,a/b/c/f3'", "30, 'f0,a/f1,a/b/f2,a/b/c/f3'"})
    void aFileIsIndexedWhenTheDepthIsGreaterThanItsFolderLevel(int depth, String expected) {
        index(new HashMap<>(), depth);

        assertThat(indexed).containsExactlyElementsOf(expected.isEmpty() ? List.of() : List.of(expected.split(",")));
    }

    @Test
    void theDepthComesFromThePreferences() {
        index(Map.of(KEY, "2"), null);

        assertThat(indexed).containsExactly("f0", "a/f1");
    }

    @ParameterizedTest(name = "preference \"{0}\" falls back to the default depth")
    @CsvSource(value = {"abc", "''", "1.5"})
    void aMissingOrUnreadableDepthFallsBackToFifteen(String preference) {
        index(new HashMap<>(Map.of(KEY, preference)), null);

        assertThat(indexed).containsExactly("f0", "a/f1", "a/b/f2", "a/b/c/f3");
    }

    @Test
    void aMissingPreferenceFallsBackToFifteen() {
        index(new HashMap<>(), null);

        assertThat(indexed).hasSize(4);
    }
}
