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
import org.jd.gui.model.container.entry.path.FileEntryPath;
import org.junit.jupiter.api.BeforeAll;
import org.objectweb.asm.Opcodes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

class ClassFileTypeFactoryProviderTest {

    static final class FakeEntry implements Container.Entry {
        final String path;
        final byte[] bytes;
        FakeEntry parent;
        final Map<Container.EntryPath, Container.Entry> children = new LinkedHashMap<>();

        FakeEntry(String path, byte[] bytes) {
            this.path = path;
            this.bytes = bytes;
        }

        public boolean isDirectory() { return bytes == null; }
        public String getPath() { return path; }
        public Map<Container.EntryPath, Container.Entry> getChildren() { return children; }
        public Container getContainer() { return null; }
        public Container.Entry getParent() { return parent; }
        public URI getUri() { return URI.create("jar:file:/fixture.jar!/" + path); }
        public long length() { return bytes == null ? 0 : bytes.length; }
        public long compressedLength() { return length(); }
        public InputStream getInputStream() { return new ByteArrayInputStream(bytes == null ? new byte[0] : bytes); }
    }

    static FakeEntry root;
    static final Map<String, FakeEntry> ENTRIES = new LinkedHashMap<>();

    @BeforeAll
    static void compile(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("src/demo/model");
        Files.createDirectories(src);
        Files.writeString(src.resolve("Sample.java"), """
                package demo.model;
                public abstract class Sample<T> extends java.util.AbstractList<String> implements Comparable<Sample<T>>, java.io.Serializable {
                    public static final int CONSTANT = 1;
                    private final String name = "n";
                    protected long[] numbers;
                    int[][] grid;
                    java.util.List<String> list;
                    boolean flag; byte b; short s; char c; float f; double d;
                    public Sample() {}
                    private Sample(String name, int... counts) {}
                    public static void util(String[] args, boolean flag, byte b, short s, char c, float f, double d, long l, Object o) {}
                    protected synchronized int[] arrays(long[][] x) throws Exception { return null; }
                    abstract void hidden();
                    public <E> E generic(E e) { return e; }
                    public static class Nested {}
                    class Inner { class Deeper {} }
                    enum Mode { ON, OFF }
                    interface Callback { void done(); }
                    @interface Marker {}
                }
                """);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path out = Files.createDirectories(dir.resolve("out"));
        compiler.run(null, null, null, "-d", out.toString(), src.resolve("Sample.java").toString());
        root = new FakeEntry("", null);
        try (Stream<Path> files = Files.walk(out)) {
            for (Path f : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                String path = out.relativize(f).toString().replace('\\', '/');
                FakeEntry e = new FakeEntry(path, Files.readAllBytes(f));
                e.parent = root;
                root.children.put(new FileEntryPath(path), e);
                ENTRIES.put(path, e);
            }
        }
    }

    private static Type make(String path) {
        return new ClassFileTypeFactoryProvider().make((API) null, ENTRIES.get(path), null);
    }

    private static List<String> fields(Type type) {
        return type.getFields().stream().map(Type.Field::getDisplayName).toList();
    }

    private static List<String> methods(Type type) {
        return type.getMethods().stream().map(Type.Method::getDisplayName).toList();
    }

    private static List<String> innerTypes(Type type) {
        return type.getInnerTypes() == null ? List.of() : type.getInnerTypes().stream().map(Type::getDisplayTypeName).toList();
    }

    @Test
    void aClassIsDescribedByItsNameItsSuperClassItsFieldsAndItsMethods() {
        Type sample = make("demo/model/Sample.class");

        assertThat(sample.getName()).isEqualTo("demo/model/Sample");
        assertThat(sample.getSuperName()).isEqualTo("java/util/AbstractList");
        assertThat(sample.getOuterName()).isNull();
        assertThat(sample.getDisplayPackageName()).isEqualTo("demo.model");
        assertThat(sample.getDisplayTypeName()).isEqualTo("Sample");
        assertThat(sample.getDisplayInnerTypeName()).isNull();
        assertThat(sample.getFlags()).isEqualTo(Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER | Opcodes.ACC_ABSTRACT);
        assertThat(sample.getIcon()).isNotNull();
        assertThat(fields(sample)).containsExactly("CONSTANT : int", "name : String", "numbers : long[]", "grid : int[][]", "list : List",
                "flag : boolean", "b : byte", "s : short", "c : char", "f : float", "d : double");
        assertThat(methods(sample)).containsExactly("Sample()", "Sample(String, int...)", "util(String[], boolean, byte, short, char, float, double, long, Object) : void",
                "arrays(long[][]) : int[]", "hidden() : void", "generic(Object) : Object");
        assertThat(sample.getFields()).allSatisfy(field -> assertThat(field.getIcon()).isNotNull());
        assertThat(sample.getMethods()).allSatisfy(method -> assertThat(method.getIcon()).isNotNull());
    }

    @Test
    void theModifiersOfTheMembersAreKept() {
        Type sample = make("demo/model/Sample.class");

        assertThat(sample.getFields()).extracting(Type.Field::getName, Type.Field::getFlags)
                .contains(tuple("CONSTANT", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL), tuple("name", Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL),
                        tuple("numbers", Opcodes.ACC_PROTECTED), tuple("grid", 0));
        assertThat(sample.getMethods()).extracting(Type.Method::getName, Type.Method::getFlags)
                .contains(tuple("util", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC), tuple("arrays", Opcodes.ACC_PROTECTED | Opcodes.ACC_SYNCHRONIZED),
                        tuple("hidden", Opcodes.ACC_ABSTRACT));
        assertThat(sample.getMethods()).extracting(Type.Method::getDescriptor).contains("([Ljava/lang/String;ZBSCFDJLjava/lang/Object;)V", "([[J)[I");
    }

    @Test
    void theInnerTypesOfAClassAreListedAtEveryLevel() {
        Type sample = make("demo/model/Sample.class");

        assertThat(innerTypes(sample)).containsExactlyInAnyOrder("Sample.Marker", "Sample.Callback", "Sample.Mode", "Sample.Inner", "Sample.Nested");
        Type inner = sample.getInnerTypes().stream().filter(t -> "Sample.Inner".equals(t.getDisplayTypeName())).findFirst().orElseThrow();
        assertThat(innerTypes(inner)).containsExactly("Sample.Inner.Deeper");
        assertThat(inner.getOuterName()).isEqualTo("demo/model/Sample");
        assertThat(inner.getDisplayInnerTypeName()).isEqualTo("Inner");
        assertThat(methods(inner)).containsExactly("Inner()"); // a constructor of an inner class shows the name of the inner class
    }

    @Test
    void anInnerClassKnowsItsOuterClassAndItsOwnInnerClasses() {
        Type deeper = make("demo/model/Sample$Inner$Deeper.class");
        Type inner = make("demo/model/Sample$Inner.class");

        assertThat(deeper.getOuterName()).isEqualTo("demo/model/Sample$Inner");
        assertThat(deeper.getDisplayTypeName()).isEqualTo("Sample.Inner.Deeper");
        assertThat(deeper.getDisplayInnerTypeName()).isEqualTo("Deeper");
        assertThat(innerTypes(inner)).containsExactly("Sample.Inner.Deeper");
    }

    @Test
    void anEnumAnAnInterfaceAndAnAnnotationAreRecognized() {
        Type mode = make("demo/model/Sample$Mode.class");
        Type callback = make("demo/model/Sample$Callback.class");
        Type marker = make("demo/model/Sample$Marker.class");

        assertThat(mode.getSuperName()).isEqualTo("java/lang/Enum");
        assertThat(mode.getFlags() & Opcodes.ACC_ENUM).isNotZero();
        assertThat(methods(mode)).containsExactly("values() : Sample.Mode[]", "valueOf(String) : Sample.Mode", "Mode(int)", "{...}"); // {...} is the static initializer
        assertThat(fields(mode)).isEmpty(); // the constants of an enum are not listed as fields
        assertThat(callback.getSuperName()).isNull(); // an interface has no super class to show
        assertThat(callback.getFlags() & Opcodes.ACC_INTERFACE).isNotZero();
        assertThat(methods(callback)).containsExactly("done() : void");
        assertThat(marker.getFlags() & Opcodes.ACC_ANNOTATION).isNotZero();
        assertThat(java.util.List.of(mode, callback, marker)).allSatisfy(type -> assertThat(type.getIcon()).isNotNull());
    }

    @Test
    void aFragmentNamingAnotherTypeOfTheContainerOpensThatType() {
        ClassFileTypeFactoryProvider provider = new ClassFileTypeFactoryProvider();
        FakeEntry sample = ENTRIES.get("demo/model/Sample.class");

        assertThat(provider.make((API) null, sample, "demo/model/Sample").getName()).isEqualTo("demo/model/Sample");
        assertThat(new ClassFileTypeFactoryProvider().make((API) null, sample, "demo/model/Sample$Mode").getName()).isEqualTo("demo/model/Sample$Mode");
        // what follows the first dash is a member: only the type name counts
        assertThat(new ClassFileTypeFactoryProvider().make((API) null, sample, "demo/model/Sample$Callback-done-()V").getName()).isEqualTo("demo/model/Sample$Callback");
        assertThat(new ClassFileTypeFactoryProvider().make((API) null, sample, "demo/model/DoesNotExist")).isNull();
        assertThat(new ClassFileTypeFactoryProvider().make((API) null, sample, "")).isNotNull();
    }

    @Test
    void aFragmentIsResolvedInContainersWhoseEntriesHaveAPrefixOrAreTruncated() {
        FakeEntry prefixedRoot = new FakeEntry("", null);
        for (Map.Entry<String, FakeEntry> entry : ENTRIES.entrySet()) {
            String path = "BOOT-INF/classes/" + entry.getKey();
            FakeEntry copy = new FakeEntry(path, entry.getValue().bytes);
            copy.parent = prefixedRoot;
            prefixedRoot.children.put(new FileEntryPath(path), copy);
        }
        FakeEntry prefixed = (FakeEntry) prefixedRoot.children.get(new FileEntryPath("BOOT-INF/classes/demo/model/Sample.class"));

        Type type = new ClassFileTypeFactoryProvider().make((API) null, prefixed, null);
        assertThat(innerTypes(type)).hasSize(5); // the inner classes are found next to the outer one, below the same prefix
        assertThat(new ClassFileTypeFactoryProvider().make((API) null, prefixed, "demo/model/Sample$Mode").getName()).isEqualTo("demo/model/Sample$Mode");

        // the package folders can be cut off: "model/Sample.class" instead of "demo/model/Sample.class"
        FakeEntry truncatedRoot = new FakeEntry("", null);
        for (Map.Entry<String, FakeEntry> entry : ENTRIES.entrySet()) {
            String path = entry.getKey().substring("demo/".length());
            FakeEntry copy = new FakeEntry(path, entry.getValue().bytes);
            copy.parent = truncatedRoot;
            truncatedRoot.children.put(new FileEntryPath(path), copy);
        }
        FakeEntry truncated = (FakeEntry) truncatedRoot.children.get(new FileEntryPath("model/Sample.class"));
        assertThat(new ClassFileTypeFactoryProvider().make((API) null, truncated, "demo/model/Sample$Mode").getName()).isEqualTo("demo/model/Sample$Mode");
        assertThat(new ClassFileTypeFactoryProvider().make((API) null, truncated, "other/pkg/Missing")).isNull();
    }

    @Test
    void innerClassesRootRelativeInAPrefixedContainerAreStillFound() {
        FakeEntry mixedRoot = new FakeEntry("", null);
        FakeEntry outer = new FakeEntry("lib/demo/model/Sample.class", ENTRIES.get("demo/model/Sample.class").bytes);
        outer.parent = mixedRoot;
        mixedRoot.children.put(new FileEntryPath("lib/demo/model/Sample.class"), outer);
        for (Map.Entry<String, FakeEntry> entry : ENTRIES.entrySet()) {
            if (!entry.getKey().equals("demo/model/Sample.class")) {
                FakeEntry copy = new FakeEntry(entry.getKey(), entry.getValue().bytes);
                copy.parent = mixedRoot;
                mixedRoot.children.put(new FileEntryPath(entry.getKey()), copy); // not below "lib/"
            }
        }

        Type type = new ClassFileTypeFactoryProvider().make((API) null, outer, null);

        assertThat(innerTypes(type)).hasSize(5);
    }

    @Test
    void theResultIsCachedPerEntryAndTheProviderDeclaresWhatItHandles() {
        ClassFileTypeFactoryProvider provider = new ClassFileTypeFactoryProvider();
        FakeEntry sample = ENTRIES.get("demo/model/Sample.class");

        Type first = provider.make((API) null, sample, null);

        assertThat(provider.make((API) null, sample, null)).isSameAs(first);
        assertThat(provider.make((API) null, sample)).containsExactly(first);
        assertThat(provider.getSelectors()).containsExactly("*:file:*.class");
        assertThat(provider.getPathPattern()).isNull();
    }
}
