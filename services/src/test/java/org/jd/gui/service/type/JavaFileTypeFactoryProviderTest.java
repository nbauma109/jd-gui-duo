/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.type;

import org.jd.gui.api.API;
import org.jd.gui.api.model.Container;
import org.jd.gui.api.model.Type;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

class JavaFileTypeFactoryProviderTest {

    static final String SOURCE = """
            package demo.model;

            public class Shapes<T> extends java.util.ArrayList<T> implements Runnable {
                public static final int MAX = 3;
                private String label;
                protected int[] counts;
                java.util.Map<String, long[]> map;
                public Shapes() {}
                public Shapes(String label, int... n) {}
                public void run() {}
                static <E> E pick(java.util.List<E> items, long index) throws Exception { return null; }
                public static class Nested { void inner() {} class Deep {} }
                enum Color { RED, GREEN; void paint() {} }
                interface Visitor { void visit(); }
                @interface Marker { String value(); }
                record Pair(int a, String b) {}
            }

            class Helper {
                int x;
            }
            """;

    static final class FakeContainer implements Container {
        final FakeEntry root;
        FakeContainer(FakeEntry root) { this.root = root; }
        public String getType() { return "jar"; }
        public Entry getRoot() { return root; }
    }

    static final class FakeEntry implements Container.Entry {
        final String path;
        final byte[] bytes;
        final URI uri;
        Container container;
        Container.Entry parent;
        final Map<Container.EntryPath, Container.Entry> children = new LinkedHashMap<>();

        FakeEntry(String path, byte[] bytes, URI uri) { this.path = path; this.bytes = bytes; this.uri = uri; }
        public boolean isDirectory() { return bytes == null; }
        public String getPath() { return path; }
        public Map<Container.EntryPath, Container.Entry> getChildren() { return children; }
        public Container getContainer() { return container; }
        public Container.Entry getParent() { return parent; }
        public URI getUri() { return uri; }
        public long length() { return bytes == null ? 0 : bytes.length; }
        public long compressedLength() { return length(); }
        public InputStream getInputStream() { return new ByteArrayInputStream(bytes == null ? new byte[0] : bytes); }
    }

    static API api() {
        return (API) Proxy.newProxyInstance(JavaFileTypeFactoryProviderTest.class.getClassLoader(), new Class<?>[] {API.class},
                (proxy, method, args) -> "getPreferences".equals(method.getName()) ? new HashMap<String, String>() : null);
    }

    static FakeEntry sourceEntry() {
        return sourceEntry("demo/model/Shapes.java", SOURCE);
    }

    static FakeEntry sourceEntry(String path, String text) {
        FakeEntry jar = new FakeEntry("", null, URI.create("file:/nonexistent/fixture.jar"));
        FakeEntry root = new FakeEntry("", null, URI.create("jar:file:/nonexistent/fixture.jar!/"));
        root.parent = jar;
        FakeContainer container = new FakeContainer(root);
        root.container = container;
        FakeEntry source = new FakeEntry(path, text.getBytes(StandardCharsets.UTF_8), URI.create("jar:file:/nonexistent/fixture.jar!/" + path));
        source.container = container;
        source.parent = root;
        return source;
    }

    private static List<String> names(Collection<Type> types) {
        return types.stream().map(Type::getName).toList();
    }

    private static List<String> fields(Type type) {
        return type.getFields().stream().map(Type.Field::getDisplayName).toList();
    }

    private static List<String> methods(Type type) {
        return type.getMethods().stream().map(Type.Method::getDisplayName).toList();
    }

    private static Type innerType(Type type, String displayName) {
        return type.getInnerTypes().stream().filter(t -> displayName.equals(t.getDisplayTypeName())).findFirst().orElseThrow();
    }

    @Test
    void aSourceFileGivesItsTopLevelTypesInOrder() {
        Collection<Type> types = new JavaFileTypeFactoryProvider().make(api(), sourceEntry());

        assertThat(names(types)).containsExactly("demo/model/Shapes", "demo/model/Helper");
    }

    @Test
    void aTopLevelTypeIsDescribedByItsNameItsSuperClassItsFieldsAndItsMethods() {
        Type shapes = new JavaFileTypeFactoryProvider().make(api(), sourceEntry(), null);

        assertThat(shapes.getName()).isEqualTo("demo/model/Shapes");
        assertThat(shapes.getSuperName()).isEqualTo("java/util/ArrayList");
        assertThat(shapes.getOuterName()).isNull();
        assertThat(shapes.getDisplayPackageName()).isEqualTo("demo.model");
        assertThat(shapes.getDisplayTypeName()).isEqualTo("Shapes");
        assertThat(shapes.getDisplayInnerTypeName()).isNull();
        assertThat(shapes.getFlags()).isEqualTo(Opcodes.ACC_PUBLIC);
        assertThat(shapes.getIcon()).isNotNull();
        assertThat(fields(shapes)).containsExactly("MAX : int", "label : String", "counts : int[]", "map : Map");
        assertThat(shapes.getFields()).extracting(Type.Field::getName, Type.Field::getFlags)
                .containsExactly(tuple("MAX", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL), tuple("label", Opcodes.ACC_PRIVATE),
                        tuple("counts", Opcodes.ACC_PROTECTED), tuple("map", 0));
        assertThat(methods(shapes)).contains("Shapes()", "run() : void", "pick(List, long) : E");
        assertThat(shapes.getMethods()).extracting(Type.Method::getName, Type.Method::getFlags)
                .contains(tuple("run", Opcodes.ACC_PUBLIC), tuple("pick", Opcodes.ACC_STATIC));
        assertThat(shapes.getMethods()).isNotEmpty().allSatisfy(method -> assertThat(method.getIcon()).isNotNull());
        assertThat(shapes.getFields()).isNotEmpty().allSatisfy(field -> assertThat(field.getIcon()).isNotNull());
    }

    @Test
    void theNestedTypesAreListedAtEveryLevel() {
        Type shapes = new JavaFileTypeFactoryProvider().make(api(), sourceEntry(), null);

        assertThat(shapes.getInnerTypes()).extracting(Type::getDisplayTypeName).containsExactly("Shapes.Nested", "Shapes.Color", "Shapes.Visitor", "Shapes.Marker", "Shapes.Pair");
        Type nested = innerType(shapes, "Shapes.Nested");
        assertThat(nested.getName()).isEqualTo("demo/model/Shapes$Nested");
        assertThat(nested.getOuterName()).isEqualTo("demo/model/Shapes");
        assertThat(nested.getDisplayInnerTypeName()).isEqualTo("Nested");
        assertThat(nested.getFlags()).isEqualTo(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC);
        assertThat(methods(nested)).containsExactly("inner() : void");
        assertThat(nested.getInnerTypes()).extracting(Type::getName).containsExactly("demo/model/Shapes$Nested$Deep");
        assertThat(nested.getInnerTypes().iterator().next().getOuterName()).isEqualTo("demo/model/Shapes$Nested");
    }

    @Test
    void anEnumAndAnInterfaceAndAnAnnotationAreRecognizedFromTheirSource() {
        Type shapes = new JavaFileTypeFactoryProvider().make(api(), sourceEntry(), null);

        Type color = innerType(shapes, "Shapes.Color");
        Type visitor = innerType(shapes, "Shapes.Visitor");
        Type marker = innerType(shapes, "Shapes.Marker");
        assertThat(color.getFlags() & Opcodes.ACC_ENUM).isNotZero();
        assertThat(methods(color)).containsExactly("paint() : void");
        assertThat(visitor.getFlags() & Opcodes.ACC_INTERFACE).isNotZero();
        assertThat(methods(visitor)).containsExactly("visit() : void");
        assertThat(marker.getFlags() & Opcodes.ACC_ANNOTATION).isNotZero();
        assertThat(java.util.List.of(color, visitor, marker)).hasSize(3).allSatisfy(type -> assertThat(type.getIcon()).isNotNull());
    }

    @Test
    void aVariableArityParameterIsAnArrayInTheDescriptor() {
        Type shapes = new JavaFileTypeFactoryProvider().make(api(), sourceEntry(), null);

        // Shapes(String label, int... n) is <init>(String, int[]) like in the class file
        assertThat(shapes.getMethods()).extracting(Type.Method::getName, Type.Method::getDescriptor)
                .contains(tuple("<init>", "()V"), tuple("<init>", "(Ljava/lang/String;[I)V"));
    }

    @Test
    void anInterfaceAndAnAnnotationHaveNoSuperClassAsInTheModelOfAClassFile() {
        Type shapes = new JavaFileTypeFactoryProvider().make(api(), sourceEntry(), null);

        assertThat(innerType(shapes, "Shapes.Visitor").getSuperName()).isNull();
        assertThat(innerType(shapes, "Shapes.Marker").getSuperName()).isNull();
        assertThat(innerType(shapes, "Shapes.Nested").getSuperName()).isEqualTo("java/lang/Object");
    }

    @Test
    void aRecordIsATypeWithItsComponentsItsAccessorsAndItsCanonicalConstructor() {
        Type shapes = new JavaFileTypeFactoryProvider().make(api(), sourceEntry(), null);

        Type pair = innerType(shapes, "Shapes.Pair");

        assertThat(pair.getName()).isEqualTo("demo/model/Shapes$Pair");
        assertThat(pair.getSuperName()).isEqualTo("java/lang/Record");
        assertThat(pair.getFlags() & Opcodes.ACC_FINAL).isNotZero();
        assertThat(pair.getIcon()).isNotNull();
        assertThat(pair.getFields()).extracting(Type.Field::getName, Type.Field::getDescriptor, Type.Field::getFlags)
                .containsExactly(tuple("a", "I", Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL), tuple("b", "Ljava/lang/String;", Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL));
        assertThat(pair.getMethods()).extracting(Type.Method::getName, Type.Method::getDescriptor)
                .containsExactlyInAnyOrder(tuple("a", "()I"), tuple("b", "()Ljava/lang/String;"), tuple("<init>", "(ILjava/lang/String;)V"));
    }

    @Test
    void aRecordWhichDeclaresSomeOfItsMembersIsNotGivenThemTwice() {
        String text = """
                package demo;

                public record Range(int lo, int hi, String... tags) {
                    public Range {
                        if (lo > hi) {
                            throw new IllegalArgumentException();
                        }
                    }

                    public int lo() {
                        return lo;
                    }

                    static Range of(int n) {
                        return new Range(n, n);
                    }
                }
                """;

        Type range = new JavaFileTypeFactoryProvider().make(api(), sourceEntry("demo/Range.java", text), null);

        assertThat(range.getSuperName()).isEqualTo("java/lang/Record");
        assertThat(range.getFlags() & Opcodes.ACC_PUBLIC).isNotZero();
        assertThat(range.getFields()).extracting(Type.Field::getName, Type.Field::getDescriptor)
                .containsExactly(tuple("lo", "I"), tuple("hi", "I"), tuple("tags", "[Ljava/lang/String;"));
        assertThat(range.getMethods()).extracting(Type.Method::getName, Type.Method::getDescriptor)
                .containsExactlyInAnyOrder(tuple("<init>", "(II[Ljava/lang/String;)V"), tuple("lo", "()I"), tuple("of", "(I)Ldemo/Range;"),
                        tuple("hi", "()I"), tuple("tags", "()[Ljava/lang/String;"));
    }

    @Test
    void aFragmentSelectsATypeOfTheFileAndIgnoresTheMemberAfterTheDash() {
        JavaFileTypeFactoryProvider provider = new JavaFileTypeFactoryProvider();
        FakeEntry entry = sourceEntry();

        assertThat(provider.make(api(), entry, "demo/model/Shapes$Nested").getName()).isEqualTo("demo/model/Shapes$Nested");
        assertThat(provider.make(api(), entry, "demo/model/Shapes$Color").getName()).isEqualTo("demo/model/Shapes$Color");
        assertThat(provider.make(api(), entry, "demo/model/Helper").getName()).isEqualTo("demo/model/Helper");
        assertThat(provider.make(api(), entry, "demo/model/Shapes-run-()V").getName()).isEqualTo("demo/model/Shapes");
        assertThat(provider.make(api(), entry, "demo/model/Nope")).isNull();
        assertThat(provider.make(api(), entry, "").getName()).isEqualTo("demo/model/Shapes"); // no fragment: the main type
    }

    @Test
    void theParsedFileIsCachedPerEntryAndTheProviderDeclaresWhatItHandles() {
        JavaFileTypeFactoryProvider provider = new JavaFileTypeFactoryProvider();
        FakeEntry entry = sourceEntry();

        Type first = provider.make(api(), entry, null);

        assertThat(provider.make(api(), entry, null)).isSameAs(first);
        assertThat(provider.getSelectors()).containsExactly("*:file:*.java");
        assertThat(provider.getPathPattern()).isNull();
    }

    @Test
    void anEmptySourceHasNoType() {
        FakeEntry entry = sourceEntry();
        FakeEntry empty = new FakeEntry("demo/Empty.java", new byte[0], URI.create("jar:file:/nonexistent/fixture.jar!/demo/Empty.java"));
        empty.container = entry.container;
        empty.parent = entry.parent;
        JavaFileTypeFactoryProvider provider = new JavaFileTypeFactoryProvider();

        assertThat(provider.make(api(), empty)).isEmpty();
        assertThat(provider.make(api(), empty, null)).isNull();
    }
}
