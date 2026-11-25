/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.controller;

import org.jd.gui.api.API;
import org.jd.gui.api.model.Container;
import org.jd.gui.api.model.Indexes;
import org.jd.gui.model.container.entry.path.DirectoryEntryPath;
import org.jd.gui.model.container.entry.path.FileEntryPath;
import org.jd.gui.util.decompiler.GuiPreferences;
import org.jd.gui.view.component.CompiledEntries;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.awt.GraphicsEnvironment;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;

import javax.swing.SwingUtilities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jd.gui.view.SearchInConstantPoolsView.SEARCH_CONSTRUCTOR;
import static org.jd.gui.view.SearchInConstantPoolsView.SEARCH_DECLARATION;
import static org.jd.gui.view.SearchInConstantPoolsView.SEARCH_FIELD;
import static org.jd.gui.view.SearchInConstantPoolsView.SEARCH_METHOD;
import static org.jd.gui.view.SearchInConstantPoolsView.SEARCH_MODULE;
import static org.jd.gui.view.SearchInConstantPoolsView.SEARCH_REFERENCE;
import static org.jd.gui.view.SearchInConstantPoolsView.SEARCH_STRING;
import static org.jd.gui.view.SearchInConstantPoolsView.SEARCH_TYPE;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** The search in the indexes of the opened archives: what matches, and where a result leads. The dialog is not shown. */
class ConstantPoolSearchTest {

    @TempDir
    static Path directory;

    private static Map<String, byte[]> classes;

    @BeforeAll
    static void compile() throws IOException {
        assumeFalse(GraphicsEnvironment.isHeadless());
        classes = CompiledEntries.compile(directory, Map.of("demo/Outer.java",
                "package demo; public class Outer { public static class Inner { public void greet() {} } public void hello() { new Inner().greet(); } }"));
    }

    /** An entry of the tree: an archive file, its root, a folder or a file. */
    private static final class Node implements Container.Entry {
        private final String path;
        private final URI uri;
        private final byte[] bytes;
        private Node parent;
        private Container container;
        private final Map<Container.EntryPath, Container.Entry> children = new LinkedHashMap<>();

        Node(String path, String uri, byte[] bytes) {
            this.path = path;
            this.uri = URI.create(uri);
            this.bytes = bytes;
        }

        public boolean isDirectory() { return bytes == null; }
        public String getPath() { return path; }
        public Map<Container.EntryPath, Container.Entry> getChildren() { return children; }
        public Container getContainer() { return container; }
        public Container.Entry getParent() { return parent; }
        public URI getUri() { return uri; }
        public long length() { return bytes == null ? 0 : bytes.length; }
        public long compressedLength() { return length(); }
        public InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
    }

    private static Container container(Container.Entry root) {
        return new Container() {
            public String getType() { return "jar"; }
            public Entry getRoot() { return root; }
        };
    }


    private static final class Archive {
        final Node file = new Node("x.jar", "file:/x.jar", new byte[0]);
        final Node root = new Node("", "file:/x.jar!/", null);
        final Node folder = new Node("demo", "file:/x.jar!/demo", null);
        final Node outer = new Node("demo/Outer.class", "file:/x.jar!/demo/Outer.class", classes.get("demo/Outer.class"));
        final Node inner = new Node("demo/Outer$Inner.class", "file:/x.jar!/demo/Outer$Inner.class", classes.get("demo/Outer$Inner.class"));
        final Map<String, Map<String, Collection<Container.Entry>>> index = new HashMap<>();
        final Indexes indexes;

        Archive() {
            file.container = container(null);
            Container jar = container(root);
            root.parent = file;
            root.container = jar;
            for (Node node : List.of(folder, outer, inner)) {
                node.container = jar;
            }
            folder.parent = root;
            root.children.put(new DirectoryEntryPath("demo"), folder);
            for (Node node : List.of(outer, inner)) {
                node.parent = folder;
                folder.children.put(new FileEntryPath(node.path), node);
            }
            put("typeDeclarations", "demo/Outer", outer);
            put("typeDeclarations", "demo/Outer$Inner", inner);
            put("typeReferences", "demo/Outer$Inner", outer);
            put("constructorDeclarations", "demo/Outer", outer);
            put("constructorReferences", "demo/Outer$Inner", outer);
            put("methodDeclarations", "greet", inner);
            put("methodReferences", "greet", outer);
            put("fieldDeclarations", "count", outer);
            put("fieldReferences", "count", inner);
            put("strings", "a text", outer);
            put("javaModuleDeclarations", "demo.module", outer);
            put("javaModuleReferences", "java.base", inner);
            @SuppressWarnings({"unchecked", "rawtypes"})
            Indexes all = name -> (Map) index.get(name);
            indexes = all;
        }

        private void put(String name, String key, Container.Entry entry) {
            index.computeIfAbsent(name, n -> new HashMap<>()).computeIfAbsent(key, k -> new ArrayList<>()).add(entry);
        }
    }

    private final List<URI> opened = new ArrayList<>();

    private SearchInConstantPoolsController controller() {
        Map<String, String> preferences = new HashMap<>();
        preferences.put(GuiPreferences.ERROR_BACKGROUND_COLOR, "0xFF6666");
        API api = (API) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {API.class},
                (proxy, method, args) -> "getPreferences".equals(method.getName()) ? preferences : null);
        // what is given to the executor is done at once
        ScheduledExecutorService executor = (ScheduledExecutorService) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {ScheduledExecutorService.class}, (proxy, method, args) -> {
                    if ("execute".equals(method.getName())) {
                        ((Runnable) args[0]).run();
                    }
                    return null;
                });
        return new SearchInConstantPoolsController(api, executor, null);
    }

    private static void set(SearchInConstantPoolsController controller, String name, Object value) throws ReflectiveOperationException {
        Field field = SearchInConstantPoolsController.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(controller, value);
    }

    private static List<String> filter(SearchInConstantPoolsController controller, Archive archive, String pattern, int flags) {
        Set<Container.Entry> found = new HashSet<>();
        controller.filter(archive.indexes, pattern, flags, found);
        return found.stream().map(Container.Entry::getPath).map(path -> path.substring(path.indexOf('/') + 1).replace(".class", "")).sorted().toList();
    }

    @ParameterizedTest(name = "\"{0}\" as {1} {2}")
    @CsvSource(delimiter = '|', value = {
            "Outer     | TYPE        | DECLARATION | Outer",
            "In        | TYPE        | DECLARATION | Outer$Inner",
            "I         | TYPE        | REFERENCE   | Outer",
            "*         | TYPE        | DECLARATION | Outer,Outer$Inner",
            "Out       | CONSTRUCTOR | DECLARATION | Outer",
            "In?er     | CONSTRUCTOR | REFERENCE   | Outer",
            "gre       | METHOD      | DECLARATION | Outer$Inner",
            "g         | METHOD      | REFERENCE   | Outer",
            "count     | FIELD       | DECLARATION | Outer",
            "c         | FIELD       | REFERENCE   | Outer$Inner",
            "a t       | STRING      | REFERENCE   | Outer",
            "a text    | STRING      | DECLARATION | ''",
            "demo.     | MODULE      | DECLARATION | Outer",
            "java      | MODULE      | REFERENCE   | Outer$Inner",
            "nothing   | METHOD      | DECLARATION | ''",
            "''        | TYPE        | DECLARATION | ''"})
    void eachKindOfSearchLooksInItsIndex(String pattern, String kind, String where, String expected) throws ReflectiveOperationException {
        int flags = (int) org.jd.gui.view.SearchInConstantPoolsView.class.getField("SEARCH_" + kind).get(null)
                | (int) org.jd.gui.view.SearchInConstantPoolsView.class.getField("SEARCH_" + where).get(null);

        assertThat(filter(controller(), new Archive(), pattern, flags)).containsExactlyElementsOf(expected.isEmpty() ? List.of() : List.of(expected.split(",")));
    }

    @Test
    void aLongerPatternIsSearchedAmongTheResultsOfTheShorterOne() {
        SearchInConstantPoolsController controller = controller();
        Archive archive = new Archive();
        int flags = SEARCH_METHOD | SEARCH_DECLARATION;
        assertThat(filter(controller, archive, "gr", flags)).containsExactly("Outer$Inner");

        // a name added since the shorter search is not looked at
        archive.put("methodDeclarations", "great", archive.outer);

        assertThat(filter(controller, archive, "gre", flags)).containsExactly("Outer$Inner");
        assertThat(filter(controller, archive, "grea", flags)).isEmpty();
        // an index that the archive does not have
        archive.index.clear();
        assertThat(filter(controller, archive, "x", SEARCH_TYPE | SEARCH_DECLARATION)).isEmpty();
    }

    @Test
    void aResultLeadsToItsEntryWithWhatToHighlight() throws Exception {
        SearchInConstantPoolsController controller = controller();
        Archive archive = new Archive();
        Collection<Future<Indexes>> indexes = List.of(CompletableFuture.completedFuture(archive.indexes), new CompletableFuture<>());
        set(controller, "collectionOfFutureIndexes", indexes);
        java.util.function.Consumer<URI> callback = opened::add;
        set(controller, "openCallback", callback);
        int all = SEARCH_DECLARATION | SEARCH_REFERENCE | SEARCH_TYPE | SEARCH_CONSTRUCTOR | SEARCH_METHOD | SEARCH_FIELD | SEARCH_STRING | SEARCH_MODULE;

        SwingUtilities.invokeAndWait(() -> controller.updateTree("greet", all));
        SwingUtilities.invokeAndWait(() -> { });
        // the tree of the results reads the entries that it shows, as it is unfolded
        Field field = SearchInConstantPoolsController.class.getDeclaredField("delegatingFilterContainers");
        field.setAccessible(true);
        Set<?> containers = (Set<?>) field.get(controller);
        assertThat(containers).hasSize(1);
        unfold(((Container) containers.iterator().next()).getRoot());
        controller.onTypeSelected(URI.create("file:/x.jar!/demo/Outer.class#demo/Outer-hello-()V"), "greet", all);
        controller.onTypeSelected(URI.create("file:/x.jar!/demo/Outer.class"), "greet", 0);
        controller.onTypeSelected(URI.create("file:/x.jar!/demo/Missing.class"), "greet", all);

        assertThat(opened).hasSize(2);
        assertThat(opened.get(0).getQuery()).isEqualTo("highlightPattern=greet&highlightFlags=drtcmfsM");
        assertThat(opened.get(0).getFragment()).isEqualTo("demo/Outer-hello-()V");
        assertThat(opened.get(1).getQuery()).isEqualTo("highlightPattern=greet&highlightFlags=");
    }

    private static void unfold(Container.Entry entry) {
        entry.getChildren().values().forEach(ConstantPoolSearchTest::unfold);
    }

    @Test
    void anInnerClassIsShownAsItsOuterClass() {
        Archive archive = new Archive();

        Set<Container.Entry> outer = controller().getOuterEntries(Set.of(archive.inner));

        assertThat(outer).containsExactly(archive.outer);
    }
}
