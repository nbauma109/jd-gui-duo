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
import org.jd.gui.model.container.entry.path.DirectoryEntryPath;
import org.jd.gui.model.container.entry.path.FileEntryPath;
import org.jd.gui.spi.Indexer;
import org.jd.gui.util.decompiler.GuiPreferences;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** A jmod keeps its classes in a "classes" folder: the packages and the files below it are indexed, not META-INF. */
class JavaModuleFileIndexerProviderTest {

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
        public URI getUri() { return URI.create("jmod:/" + path); }
        public long length() { return 0; }
        public long compressedLength() { return 0; }
        public InputStream getInputStream() { return new ByteArrayInputStream(new byte[0]); }
    }

    private final List<String> indexed = new ArrayList<>();
    private final Map<String, String> preferences = new HashMap<>();

    @SuppressWarnings("rawtypes")
    private final Map<String, Collection> packages = new HashMap<>() {
        private static final long serialVersionUID = 1L;

        @Override
        public Collection get(Object key) {
            return computeIfAbsent((String) key, k -> new ArrayList<>());
        }
    };

    private API api() {
        Indexer recorder = (Indexer) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Indexer.class}, (proxy, method, args) -> {
            if ("index".equals(method.getName())) {
                indexed.add(((Container.Entry) args[1]).getPath());
            }
            return null;
        });
        return (API) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {API.class}, (proxy, method, args) -> switch (method.getName()) {
            case "getIndexer" -> recorder;
            case "getPreferences" -> preferences;
            default -> null;
        });
    }

    private void index(FakeEntry jmod) {
        Indexes indexes = name -> "packageDeclarations".equals(name) ? packages : null;
        new JavaModuleFileIndexerProvider().index(api(), jmod, indexes, () -> 0, value -> { }, () -> false);
    }

    @Test
    void thePackagesAndTheFilesBelowTheClassesFolderAreIndexed() {
        FakeEntry classes = new FakeEntry("classes", true)
                .add(new FakeEntry("classes/module-info.class", false))
                .add(new FakeEntry("classes/demo", true)
                        .add(new FakeEntry("classes/demo/A.class", false))
                        .add(new FakeEntry("classes/demo/sub", true).add(new FakeEntry("classes/demo/sub/B.class", false))))
                .add(new FakeEntry("classes/META-INF", true).add(new FakeEntry("classes/META-INF/services", false)));

        index(new FakeEntry("", true).add(new FakeEntry("lib", true)).add(classes));

        // the packages are the directories of classes/, without their "classes/" prefix and not META-INF
        assertThat(packages).containsOnlyKeys("demo", "demo/sub");
        assertThat(indexed).containsExactlyInAnyOrder("classes/module-info.class", "classes/demo/A.class", "classes/demo/sub/B.class", "classes/META-INF/services");
    }

    @Test
    void theFoldersAreIndexedDownToTheDepthOfThePreferences() {
        preferences.put(GuiPreferences.MAXIMUM_DEPTH_KEY, "1");
        FakeEntry classes = new FakeEntry("classes", true)
                .add(new FakeEntry("classes/demo", true)
                        .add(new FakeEntry("classes/demo/A.class", false))
                        .add(new FakeEntry("classes/demo/sub", true).add(new FakeEntry("classes/demo/sub/B.class", false))));

        index(new FakeEntry("", true).add(classes));

        assertThat(packages).containsOnlyKeys("demo");
        assertThat(indexed).containsExactly("classes/demo/A.class");
    }

    @Test
    void aModuleWithoutAClassesFolderIndexesNothing() {
        index(new FakeEntry("", true).add(new FakeEntry("lib", true).add(new FakeEntry("lib/native.so", false))));

        assertThat(packages).isEmpty();
        assertThat(indexed).isEmpty();
    }

    @Test
    void theSelectorIsTheJmodExtension() {
        assertThat(new JavaModuleFileIndexerProvider().getSelectors()).contains("*:file:*.jmod");
    }
}
