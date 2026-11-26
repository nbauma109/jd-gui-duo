/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.indexer;

import org.jd.gui.api.model.Container;
import org.jd.gui.api.model.Indexes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.tools.ToolProvider;

import static org.assertj.core.api.Assertions.assertThat;

/** What a class file declares and refers to, as it is searched. */
class ClassFileIndexerProviderTest {

    private static final String SOURCE = """
            import java.lang.annotation.*;
            import java.util.*;

            @Sample.Mark(kind = Sample.Kind.ONE, nested = @Sample.Inner(Integer.class))
            public class Sample<T extends Number> extends AbstractList<T> implements Runnable {
                @Retention(RetentionPolicy.RUNTIME) @interface Mark { Kind kind(); Inner nested(); }
                @Retention(RetentionPolicy.RUNTIME) @interface Inner { Class<?> value(); }
                @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE_USE) @interface Used {}
                @Retention(RetentionPolicy.RUNTIME) @interface Plain {}
                enum Kind { ONE }

                @Plain private @Used List<String> names = new ArrayList<>();

                public Sample() {}

                @Plain public <E extends Exception> @Used String join(@Plain Map<String, Set<Long>> values) throws E, java.io.IOException {
                    Object[] all = new StringBuilder[0][].clone();
                    System.out.println("a constant text " + String[].class.getName() + all.length + names.size());
                    return Math.abs(1) + "";
                }

                @Override public T get(int index) { return null; }
                @Override public int size() { return 0; }
                @Override public void run() {}
                static { System.gc(); }
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

    private Container.Entry entry(byte[] bytes) throws IOException {
        Path archive = Files.write(directory.resolve("x.jar"), new byte[100_000]);
        FakeEntry file = new FakeEntry("x.jar", new byte[0], archive.toUri(), null, null);
        FakeEntry root = new FakeEntry("", new byte[0], URI.create("file:/x.jar!/"), null, file);
        Container container = new Container() {
            public String getType() { return "jar"; }
            public Entry getRoot() { return root; }
        };
        return new FakeEntry("Sample.class", bytes, URI.create("file:/x.jar!/Sample.class"), container, root);
    }

    private final Map<String, Map<String, Collection<Object>>> indexes = new HashMap<>();
    private final List<Double> progress = new ArrayList<>();

    private void index(Container.Entry entry) {
        @SuppressWarnings({"unchecked", "rawtypes"})
        Indexes target = name -> (Map) indexes.computeIfAbsent(name, n -> new HashMap<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public Collection<Object> get(Object key) {
                return computeIfAbsent((String) key, k -> new ArrayList<>());
            }
        });
        new ClassFileIndexerProvider().index(null, entry, target, () -> 0, progress::add, () -> false);
    }

    private Collection<String> keys(String name) {
        return indexes.getOrDefault(name, Map.of()).keySet();
    }

    @Test
    void theDeclarationsAndTheReferencesOfAClassAreIndexed() throws IOException {
        Path source = Files.writeString(directory.resolve("Sample.java"), SOURCE);
        assertThat(ToolProvider.getSystemJavaCompiler().run(null, null, null, "-g", "-d", directory.toString(), source.toString())).isZero();

        index(entry(Files.readAllBytes(directory.resolve("Sample.class"))));

        assertThat(keys("typeDeclarations")).containsExactly("Sample");
        assertThat(keys("constructorDeclarations")).containsExactly("Sample");
        assertThat(keys("methodDeclarations")).contains("join", "get", "size", "run").doesNotContain("<clinit>", "<init>");
        assertThat(keys("fieldDeclarations")).containsExactly("names");
        assertThat(keys("subTypeNames")).containsExactlyInAnyOrder("java/util/AbstractList", "java/lang/Runnable");
        assertThat(keys("typeReferences")).contains(
                // annotations of the class, of the field, of the method, of its parameter and of types, and what they hold
                "Sample$Mark", "Sample$Kind", "Sample$Inner", "Sample$Used", "Sample$Plain",
                // generic signatures, thrown exceptions and array constants
                "java/util/List", "java/util/Map", "java/util/Set", "java/lang/Long", "java/io/IOException", "java/lang/StringBuilder", "java/lang/String");
        assertThat(keys("constructorReferences")).contains("java/util/ArrayList", "java/util/AbstractList");
        assertThat(keys("methodReferences")).contains("println", "abs", "gc", "getName");
        assertThat(keys("fieldReferences")).contains("out", "names");
        assertThat(keys("strings")).anySatisfy(text -> assertThat(text).startsWith("a constant text "));
        assertThat(progress).isNotEmpty();
    }

    @Test
    void whatIsNoClassFileIsNotIndexed() throws IOException {
        index(entry(new byte[] {1, 2, 3}));

        assertThat(indexes).isEmpty();
    }

    @Test
    void theClassesAreIndexedButNotTheDescriptorsOfModules() {
        ClassFileIndexerProvider indexer = new ClassFileIndexerProvider();

        assertThat(indexer.getSelectors()).contains("*:file:*.class");
        assertThat(indexer.getPathPattern().matcher("demo/A.class").matches()).isTrue();
        assertThat(indexer.getPathPattern().matcher("module-info.class").matches()).isFalse();
    }
}
