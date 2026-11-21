/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.view.component;

import jd.core.links.HyperlinkData;
import jd.core.links.HyperlinkReferenceData;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.jd.gui.api.API;
import org.jd.gui.api.model.Container;
import org.jd.gui.util.parser.jdt.ASTParserFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

class ReferenceListenerTest {

    private record FakeEntry(String path, URI uri, Container container, Container.Entry parent) implements Container.Entry {
        public boolean isDirectory() { return false; }
        public String getPath() { return path; }
        public Map<Container.EntryPath, Container.Entry> getChildren() { return new LinkedHashMap<>(); }
        public Container getContainer() { return container; }
        public Container.Entry getParent() { return parent; }
        public URI getUri() { return uri; }
        public long length() { return 0; }
        public long compressedLength() { return 0; }
        public InputStream getInputStream() { return new ByteArrayInputStream(new byte[0]); }
    }

    private static API api() {
        return (API) Proxy.newProxyInstance(ReferenceListenerTest.class.getClassLoader(), new Class<?>[] {API.class},
                (proxy, method, args) -> "getPreferences".equals(method.getName()) ? new HashMap<String, String>() : null);
    }

    private static Container.Entry entry(String path) {
        FakeEntry jar = new FakeEntry("", URI.create("file:/nonexistent/fixture.jar"), null, null);
        FakeEntry root = new FakeEntry("", URI.create("jar:file:/nonexistent/fixture.jar!/"), null, jar);
        Container container = new Container() {
            public String getType() { return "jar"; }
            public Entry getRoot() { return root; }
        };
        return new FakeEntry(path, URI.create("jar:file:/nonexistent/fixture.jar!/" + path), container, root);
    }

    /** What TypePage does with a source: a pass for the declarations, then one with the bindings for the references. */
    private static java.util.List<String> links(String path, String text) {
        Container.Entry entry = entry(path);
        ReferenceListener listener = new ReferenceListener(entry);
        SortedMap<Integer, HyperlinkData> hyperlinks = new TreeMap<>();
        listener.setHyperlinks(hyperlinks);
        char[] source = text.toCharArray();
        URI jarURI = entry.getContainer().getRoot().getParent().getUri();
        ASTParser first = ASTParserFactory.getInstance().newASTParser(api(), source, path, jarURI);
        first.createAST(null).accept(listener.getDeclarationListener());
        listener.init();
        ASTParser second = ASTParserFactory.getInstanceWithBindings().newASTParser(api(), source, path, jarURI);
        ASTNode ast = second.createAST(null);
        ast.accept(listener);
        java.util.List<String> found = new java.util.ArrayList<>();
        for (Map.Entry<Integer, HyperlinkData> hyperlink : hyperlinks.entrySet()) {
            if (hyperlink.getValue() instanceof HyperlinkReferenceData data) {
                int start = hyperlink.getKey();
                String shown = text.substring(start, Math.min(text.length(), start + data.getEndPosition() - data.getStartPosition()));
                found.add(shown + " => " + data.getReference().getTypeName() + "#" + data.getReference().getName() + "#" + data.getReference().getDescriptor());
            }
        }
        return found;
    }

    private static final String SOURCE = """
            package demo;

            import java.util.List;
            import java.util.ArrayList;
            import java.util.Map.Entry;
            import java.util.*;
            import static java.lang.Math.max;

            public class Sample<T extends Comparable<T>> extends ArrayList<String> implements Runnable {
                public static final int LIMIT = 3;
                private int[] values = new int[LIMIT];
                private String[] names = new String[2];
                private List<String> list = new ArrayList<>();

                enum Mode { ON, OFF }

                @interface Marker { String value() default "x"; }

                @Marker("a")
                public Sample() {
                    super();
                }

                public Sample(String name) {
                    this();
                    this.values[0] = name.length();
                }

                @Override
                public void run() {
                    int n = values.length + names.length + LIMIT + max(1, 2);
                    String s = String.valueOf(n);
                    Object o = (Object) s;
                    if (o instanceof String text && text.length() > 0) {
                        System.out.println(text.toUpperCase());
                    }
                    Mode mode = Mode.ON;
                    Runnable r = new Runnable() {
                        @Override
                        public void run() {
                            list.add("a");
                        }
                    };
                    r.run();
                    Entry<String, Integer> entry = null;
                    Sample<String> other = new Sample<>("x");
                    other.values[0]++;
                    super.size();
                    this.size();
                    Sample.LIMIT.toString();
                }

                static class Nested { int field; void call() { new Nested().field = 1; } }
            }
            """;

    private static final String SOURCE2 = """
            package demo;

            import java.io.IOException;
            import java.util.function.Function;
            import java.util.concurrent.Callable;

            public interface Other extends Callable<String> {
                String NAME = "n";

                default <R extends Number> R convert(Function<? super String, ? extends R> f) throws IOException, IllegalStateException {
                    try {
                        Class<?> type = String.class;
                        Function<String, String> g = String::valueOf;
                        Callable<String> c = () -> NAME;
                        Runnable r = Other.Inner::new;
                        for (String each : new String[] {"a"}) {
                            Object[] objects = new Object[] {each, type, g, c, r};
                            int length = objects.length;
                        }
                        switch (Inner.Kind.A) {
                            case A -> System.out.println(Other.NAME);
                            default -> throw new IllegalArgumentException(NAME);
                        }
                        return f.apply(NAME);
                    } catch (IllegalStateException | NullPointerException e) {
                        throw e;
                    } finally {
                        Thread.currentThread().interrupt();
                    }
                }

                class Inner implements Runnable {
                    enum Kind { A, B }
                    Inner() { }
                    public void run() { Other.NAME.length(); }
                    static <X> X id(X x) { return x; }
                }
            }
            """;

    @Test
    void importsTypesAndConstructorCallsAreLinkedToTheirTypes() {
        assertThat(links("demo/Sample.java", SOURCE)).contains(
                "java.util.List => java/util/List#null#null", "Entry => java/util/Map$Entry#null#null", "Math => java/lang/Math#null#null",
                "ArrayList => java/util/ArrayList#null#null", "new ArrayList<>() => java/util/ArrayList#<init>#()V",
                "super => java/util/ArrayList#<init>#()V", "this => demo/Sample#<init>#()V",
                "new Sample<>(\"x\") => demo/Sample#<init>#(Ljava/lang/String;)V", "new Nested() => demo/Sample$Nested#<init>#()V",
                "Mode => demo/Sample$Mode#null#null", "ON => demo/Sample$Mode#ON#?", "Comparable => java/lang/Comparable#null#null");
    }

    @Test
    void fieldsAndMethodsAreLinkedToTheirDeclaringTypeWithTheirDescriptor() {
        assertThat(links("demo/Sample.java", SOURCE)).contains(
                "values => demo/Sample#values#?", "field => demo/Sample$Nested#field#?", "length => java/lang/String#length#()I",
                "max => java/lang/Math#max#(II)I", "valueOf => java/lang/String#valueOf#(I)Ljava/lang/String;",
                "System => java/lang/System#null#null", "out => java/lang/System#out#?", "println => java/io/PrintStream#println#(Ljava/lang/String;)V",
                "add => java/util/List#add#(Ljava/lang/String;)Z", "run => java/lang/Runnable#run#()V", "size => java/util/ArrayList#size#()I",
                "toUpperCase => java/lang/String#toUpperCase#()Ljava/lang/String;");
    }

    @Test
    void lambdasMethodReferencesCatchClausesAndNestedTypesAreLinked() {
        assertThat(links("demo/Other.java", SOURCE2)).contains(
                "java.io.IOException => java/io/IOException#null#null", "Function => java/util/function/Function#null#null",
                "Callable => java/util/concurrent/Callable#null#null", "Number => java/lang/Number#null#null",
                "valueOf => java/lang/String#valueOf#(Ljava/lang/Object;)Ljava/lang/String;",
                "Inner => demo/Other$Inner#null#null", "A => demo/Other$Inner$Kind#A#?", "NAME => demo/Other#NAME#?",
                "new IllegalArgumentException(NAME) => java/lang/IllegalArgumentException#<init>#(Ljava/lang/String;)V",
                "IllegalStateException => java/lang/IllegalStateException#null#null", "NullPointerException => java/lang/NullPointerException#null#null",
                "currentThread => java/lang/Thread#currentThread#()Ljava/lang/Thread;", "interrupt => java/lang/Thread#interrupt#()V",
                "length => java/lang/String#length#()I");
    }

    private static final String REPRO = """
            package demo;

            public class Repro {

                interface Consts {
                    String NAME = "n";
                    int SIZE = 3;
                }

                static final int LIMIT = 10;

                int[] values = new int[LIMIT];
                String[] names = {"a"};

                void run() {
                    int a = values.length;
                    int b = names.length;
                    int[] local = values;
                    int c = local.length;
                    values[0]++;
                    String d = Consts.NAME;
                    int e = Consts.SIZE;
                    int f = LIMIT;
                    String g = names[0].trim();
                }
            }
            """;

    @Test
    void constantsAndFieldsUsedByTheirNameOrQualifiedByTheirTypeAreLinkedToTheirDeclaration() {
        assertThat(links("demo/Repro.java", REPRO)).contains(
                "NAME => demo/Repro$Consts#NAME#?", "SIZE => demo/Repro$Consts#SIZE#?", "LIMIT => demo/Repro#LIMIT#?",
                "values => demo/Repro#values#?", "names => demo/Repro#names#?", "Consts => demo/Repro$Consts#null#null",
                "trim => java/lang/String#trim#()Ljava/lang/String;")
                .doesNotContain("NAME => java/lang/String#null#null", "SIZE => I#null#null");
    }

    @Test
    void theLengthOfAnArrayIsNotLinkedToAnything() {
        assertThat(links("demo/Repro.java", REPRO)).noneMatch(link -> link.startsWith("length =>")).noneMatch(link -> link.endsWith("=> I#null#null"));
    }

    @Test
    void fieldsOfGenericClassesAreLinkedToTheClassNotToItsParameterizedForm() {
        String generic = """
                package demo;

                public class Box<T> {
                    static int COUNT;
                    T value;

                    void m(Box<String> other, Box<Integer> third) {
                        other.value = null;
                        int c = Box.COUNT;
                        third.COUNT++;
                        COUNT = value == null ? 1 : 2;
                    }
                }
                """;

        java.util.List<String> links = links("demo/Box.java", generic);

        assertThat(links).contains("COUNT => demo/Box#COUNT#?", "value => demo/Box#value#?", "Box => demo/Box#null#null")
                .noneMatch(link -> link.substring(link.indexOf("=> ")).split("#")[0].contains("<"));
    }

    private static final String CALLS = """
            package demo;

            public class Calls {
                static class Foo {
                    Foo(String s) { }
                    Foo(int a, int b) { }
                    void m(String s) { }
                    void m(int a, int b) { }
                    static void s(long x) { }
                }

                static class Sub extends Foo {
                    Sub() { super(1, 2, 3); }
                    Sub(int x) { this(x, "y", null); }
                    Sub(String s) { super(s); }
                    Sub(String a, String b, String c) { super(a); }
                    void sup() { super.m("x"); super.m(1, 2, 3); }
                }

                void go(Foo foo) {
                    foo.m("a");
                    foo.m(1, 2, 3);
                    foo.m(1, "x", foo);
                    foo.nothing(null, 1);
                    m2(1);
                    new Foo(1, 2, 3);
                    new Foo(foo, null);
                    Foo.s("x");
                }
            }
            """;

    @Test
    void callsThatResolveKeepTheExactDescriptorOfTheirBinding() {
        assertThat(links("demo/Calls.java", CALLS)).contains(
                "m => demo/Calls$Foo#m#(Ljava/lang/String;)V", "super => demo/Calls$Foo#<init>#(Ljava/lang/String;)V")
                .doesNotContain("m => demo/Calls$Foo#m#(II)V", "new Foo(1, 2, 3) => demo/Calls$Foo#<init>#(Ljava/lang/String;)V");
    }

    @Test
    void callsWithoutApplicableMethodAreLinkedFromWhatTheirArgumentsTell() {
        assertThat(links("demo/Calls.java", CALLS)).contains(
                // methods, like constructors: the types of the arguments that are known are hints, ? stands for the others and for the return type
                "m => demo/Calls$Foo#m#(~I~I~I)?", "m => demo/Calls$Foo#m#(~I~Ljava/lang/String;~Ldemo/Calls$Foo;)?",
                "nothing => demo/Calls$Foo#nothing#(?~I)?", "m2 => demo/Calls#m2#(~I)?", "s => demo/Calls$Foo#s#(~Ljava/lang/String;)?",
                "new Foo(1, 2, 3) => demo/Calls$Foo#<init>#(~I~I~I)V", "new Foo(foo, null) => demo/Calls$Foo#<init>#(~Ldemo/Calls$Foo;?)V",
                "super => demo/Calls$Foo#<init>#(~I~I~I)V", "this => demo/Calls$Sub#<init>#(~I~Ljava/lang/String;?)V");
    }

    private static final String REFERENCES = """
            package demo;

            import java.util.function.*;

            public class Refs {
                static class Foo {
                    Foo() { }
                    Foo(String s) { }
                    void m(String s) { }
                    static int len(String s) { return 0; }
                }

                static class Sub extends Foo {
                    void go() {
                        Consumer<String> sup = super::m;
                    }
                }

                void go(java.util.List<String> list) {
                    Supplier<Foo> a = Foo::new;
                    Function<String, Foo> b = Foo::new;
                    Function<String, Integer> c = Foo::len;
                    Foo foo = new Foo();
                    Consumer<String> d = foo::m;
                    Function<String, Integer> e = String::length;
                    Runnable r = foo::nothing;
                    Runnable r2 = Foo::missing;
                    Supplier<Foo> missing = Foo::zzz;
                    Function<Integer, int[]> arr = int[]::new;
                    Supplier<Missing> m = Missing::new;
                    ToIntFunction<java.util.List<String>> size = java.util.List<String>::size;
                }
            }
            """;

    @Test
    void referencesToMethodsAndConstructorsAreLinkedThroughTheirBinding() {
        assertThat(links("demo/Refs.java", REFERENCES)).contains(
                // Type::new links "new" to the constructor that the target type selects
                "new => demo/Refs$Foo#<init>#()V", "new => demo/Refs$Foo#<init>#(Ljava/lang/String;)V",
                "len => demo/Refs$Foo#len#(Ljava/lang/String;)I", "m => demo/Refs$Foo#m#(Ljava/lang/String;)V",
                "length => java/lang/String#length#()I", "size => java/util/List#size#()I");
    }

    @Test
    void referencesWithoutBindingAreLinkedToAnyMethodOfThatNameAndUnknownTypesToNothing() {
        java.util.List<String> links = links("demo/Refs.java", REFERENCES);

        // a reference has no arguments: (*) stands for any parameters
        assertThat(links).contains("nothing => demo/Refs$Foo#nothing#(*)?", "missing => demo/Refs$Foo#missing#(*)?", "zzz => demo/Refs$Foo#zzz#(*)?");
        // Missing::new: the type cannot be resolved; int[]::new: an array is not a constructor
        assertThat(links).noneMatch(link -> link.contains("Missing") && link.contains("<init>")).noneMatch(link -> link.contains("[I"));
    }

    @Test
    void referencesToUnknownTypesAreSkippedWithoutFailing() {
        String broken = """
                package demo;

                import missing.Gone;

                public class Broken extends Missing implements AlsoMissing {
                    Unknown field = new Unknown();
                    void m(Unknown arg) {
                        arg.call();
                        Unknown.STATIC.run();
                        field.value++;
                        String ok = String.valueOf(1);
                    }
                }
                """;

        assertThat(links("demo/Broken.java", broken)).contains("valueOf => java/lang/String#valueOf#(I)Ljava/lang/String;");
    }
}
