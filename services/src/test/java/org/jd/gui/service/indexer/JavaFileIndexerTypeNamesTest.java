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
import org.jd.gui.util.decompiler.GuiPreferences;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The names of the types that a source file writes in every way that Java allows. */
class JavaFileIndexerTypeNamesTest {

    private static final String SOURCE = """
            package demo;

            import java.util.List;
            import java.util.Map;

            public class Names<T> extends java.util.AbstractList<T> implements java.lang.Runnable, Map.Entry<String, int[][]> {
                public @interface Mark {}
                java.util.@Mark List<Integer> qualified;
                java.util.Map.@Mark Entry<String, Long> nested;
                java.lang.@Mark String annotated;
                Map.Entry<boolean[], byte[]> arrays;
                record Pair(char c, short s, float f, double d, long[][][] many) {
                    Pair {
                    }
                }
                void all(boolean z, byte b, char c, short s, int i, long j, float f, double d, String... rest) {}
                Unknown.Inner missing(java.io.IOException[] failures) { return null; }
            }
            """;

    @TempDir
    Path directory;

    private record FakeEntry(String path, byte[] bytes, URI uri, Container container, Container.Entry parent) implements Container.Entry {
        public boolean isDirectory() { return false; }
        public String getPath() { return path; }
        public Map<Container.EntryPath, Container.Entry> getChildren() { return Map.of(); }
        public Container getContainer() { return container; }
        public Container.Entry getParent() { return parent; }
        public URI getUri() { return uri; }
        public long length() { return bytes.length; }
        public long compressedLength() { return length(); }
        public InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
    }

    @Test
    void theTypesAreFoundHoweverTheyAreWritten() throws IOException {
        Path archive = Files.write(directory.resolve("x.jar"), new byte[10_000]);
        FakeEntry file = new FakeEntry(archive.toString(), new byte[0], archive.toUri(), null, null);
        FakeEntry root = new FakeEntry("", new byte[0], URI.create("file:/x.jar!/"), null, file);
        Container container = new Container() {
            public String getType() { return "jar"; }
            public Entry getRoot() { return root; }
        };
        FakeEntry source = new FakeEntry("demo/Names.java", SOURCE.getBytes(StandardCharsets.UTF_8), URI.create("file:/x.jar!/demo/Names.java"), container, root);
        Map<String, String> preferences = new HashMap<>();
        preferences.put(GuiPreferences.ERROR_BACKGROUND_COLOR, "0xFF6666");
        API api = (API) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {API.class},
                (proxy, method, args) -> "getPreferences".equals(method.getName()) ? preferences : null);
        Map<String, Map<String, Collection<Object>>> indexes = new HashMap<>();
        @SuppressWarnings({"unchecked", "rawtypes"})
        Indexes target = name -> (Map) indexes.computeIfAbsent(name, n -> new HashMap<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public Collection<Object> get(Object key) {
                return computeIfAbsent((String) key, k -> new ArrayList<>());
            }
        });

        new JavaFileIndexerProvider().index(api, source, target, () -> 0, value -> { }, () -> false);

        assertThat(indexes.get("typeDeclarations")).containsKeys("demo/Names", "demo/Names$Pair", "demo/Names$Mark");
        assertThat(indexes.get("typeReferences")).containsKeys("java/util/List", "java/lang/String", "java/lang/Integer", "java/lang/Long");
        assertThat(indexes.get("methodDeclarations")).containsKeys("all", "missing");
        assertThat(indexes.get("subTypeNames")).containsKeys("java/util/AbstractList", "java/lang/Runnable");
    }
}
