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

    /**
     * Where each call of {@code f} navigates to, as TypePage does it: the key of the reference (type, name and descriptor) is looked up
     * among the declarations. Gives "call line -> declaration line", or "call line -> none" when no declaration has that key.
     */
    private static java.util.List<String> targetsOfCalls(String path, String text, String method) {
        Container.Entry entry = entry(path);
        ReferenceListener listener = new ReferenceListener(entry);
        SortedMap<Integer, HyperlinkData> hyperlinks = new TreeMap<>();
        listener.setHyperlinks(hyperlinks);
        char[] source = text.toCharArray();
        URI jarURI = entry.getContainer().getRoot().getParent().getUri();
        ASTParserFactory.getInstance().newASTParser(api(), source, path, jarURI).createAST(null).accept(listener.getDeclarationListener());
        listener.init();
        ASTParserFactory.getInstanceWithBindings().newASTParser(api(), source, path, jarURI).createAST(null).accept(listener);
        java.util.List<String> targets = new java.util.ArrayList<>();
        for (Map.Entry<Integer, HyperlinkData> hyperlink : hyperlinks.entrySet()) {
            if (hyperlink.getValue() instanceof HyperlinkReferenceData data && method.equals(data.getReference().getName())) {
                jd.core.links.DeclarationData target = listener.getDeclarations()
                        .get(data.getReference().getTypeName() + "-" + method + "-" + data.getReference().getDescriptor());
                targets.add(lineOf(text, hyperlink.getKey()) + " -> " + (target == null ? "none" : String.valueOf(lineOf(text, target.getStartPosition()))));
            }
        }
        return targets;
    }

    private static int lineOf(String text, int offset) {
        return (int) text.substring(0, offset).chars().filter(c -> c == '\n').count() + 1;
    }

    private static final String OVERLOADS = """
            package demo;

            import java.util.List;

            public class Over {
                void f(int a) {}
                void f(long a) {}
                void f(Object a) {}
                void f(String a) {}
                void f(List<String> a) {}
                <T> void f(T a, int b) {}
                void f(String... a) {}
                void f(int[] a) {}
                void f(Inner a) {}
                void f(char a, double b) {}
                void f(Integer a) {}
                void f(Over... a) {}
                static class Inner {}

                void calls(List<String> list, int[] arr, Inner inner) {
                    f(1);
                    f(1L);
                    f((Object) "x");
                    f("s");
                    f(list);
                    f("t", 2);
                    f("a", "b");
                    f(arr);
                    f(inner);
                    f('c', 1.0);
                    f(Integer.valueOf(3));
                    f(new Over(), new Over());
                    f();
                }
            }
            """;

    @Test
    void everyOverloadIsLinkedToItsOwnDeclaration() {
        java.util.List<String> targets = targetsOfCalls("demo/Over.java", OVERLOADS, "f");

        // f("s") must reach f(String) (line 9), not the variable arity f(String...) (line 12) that lost its "..." and so had the same key
        assertThat(targets).contains("21 -> 6", "22 -> 7", "23 -> 8", "24 -> 9", "27 -> 12", "28 -> 13", "30 -> 15", "31 -> 16", "32 -> 17", "33 -> 12");
    }

    @Test
    void theOverloadsOfVariableArityAndOfTheSameParameterTypeKeepDistinctDeclarations() {
        Container.Entry entry = entry("demo/Over.java");
        ReferenceListener listener = new ReferenceListener(entry);
        URI jarURI = entry.getContainer().getRoot().getParent().getUri();
        ASTParserFactory.getInstance().newASTParser(api(), OVERLOADS.toCharArray(), "demo/Over.java", jarURI).createAST(null).accept(listener.getDeclarationListener());

        assertThat(listener.getDeclarations()).containsKeys("demo/Over-f-(Ljava/lang/String;)V", "demo/Over-f-([Ljava/lang/String;)V", "demo/Over-f-([Ldemo/Over;)V");
        assertThat(listener.getDeclarations().keySet().stream().filter(key -> key.contains("-f-"))).hasSize(12);
    }

    @Test
    void aCompactConstructorIsDeclaredWithTheParametersOfTheComponentsOfItsRecord() {
        String text = """
                package demo;

                public record Range(int lo, int hi, String... tags) {
                    public Range {
                        if (lo > hi) {
                            throw new IllegalArgumentException();
                        }
                    }
                }
                """;
        Container.Entry entry = entry("demo/Range.java");
        ReferenceListener listener = new ReferenceListener(entry);
        URI jarURI = entry.getContainer().getRoot().getParent().getUri();
        ASTParserFactory.getInstance().newASTParser(api(), text.toCharArray(), "demo/Range.java", jarURI).createAST(null).accept(listener.getDeclarationListener());

        assertThat(listener.getDeclarations()).containsKey("demo/Range-<init>-(II[Ljava/lang/String;)V").doesNotContainKey("demo/Range-<init>-()V");
    }

    private static final String RECORDS = """
            package demo;

            public class Recs {
                record Pt(int x, int y) { int sum() { return x + y; } }
                record Range(int lo, int hi) {
                    Range { }
                    public int lo() { return lo; }
                }

                void go(Pt pt, Range r) {
                    pt.x();
                    pt.y();
                    r.lo();
                    r.hi();
                    new Pt(1, 2);
                    new Range(1, 2);
                }
            }
            """;

    @Test
    void theMembersThatARecordDeclaresByItsHeaderAreDeclaredAtItsComponentsAndAtItsName() {
        Container.Entry entry = entry("demo/Recs.java");
        ReferenceListener listener = new ReferenceListener(entry);
        URI jarURI = entry.getContainer().getRoot().getParent().getUri();
        ASTParserFactory.getInstance().newASTParser(api(), RECORDS.toCharArray(), "demo/Recs.java", jarURI).createAST(null).accept(listener.getDeclarationListener());

        assertThat(listener.getDeclarations()).containsKeys(
                "demo/Recs$Pt-x-I", "demo/Recs$Pt-x-()I", "demo/Recs$Pt-y-I", "demo/Recs$Pt-y-()I", "demo/Recs$Pt-<init>-(II)V");
        // the members that the body declares itself are at their own place: the accessor lo() and the compact constructor
        assertThat(lineOf(RECORDS, listener.getDeclarations().get("demo/Recs$Range-lo-()I").getStartPosition())).isEqualTo(7);
        assertThat(lineOf(RECORDS, listener.getDeclarations().get("demo/Recs$Range-hi-()I").getStartPosition())).isEqualTo(5);
        assertThat(lineOf(RECORDS, listener.getDeclarations().get("demo/Recs$Range-<init>-(II)V").getStartPosition())).isEqualTo(6);
        assertThat(lineOf(RECORDS, listener.getDeclarations().get("demo/Recs$Pt-<init>-(II)V").getStartPosition())).isEqualTo(4);
    }

    @Test
    void callsOfTheMembersOfARecordNavigateToThem() {
        assertThat(targetsOfCalls("demo/Recs.java", RECORDS, "x")).contains("11 -> 4");
        assertThat(targetsOfCalls("demo/Recs.java", RECORDS, "y")).contains("12 -> 4");
        assertThat(targetsOfCalls("demo/Recs.java", RECORDS, "lo")).contains("13 -> 7");
        assertThat(targetsOfCalls("demo/Recs.java", RECORDS, "hi")).contains("14 -> 5");
        assertThat(targetsOfCalls("demo/Recs.java", RECORDS, "<init>")).containsExactly("15 -> 4", "16 -> 6");
    }

    private static final String GENERIC_CALLS = """
            package demo;

            import java.util.*;
            import java.util.function.*;
            import java.util.stream.*;

            public class GenericCalls {
                static <T> T first(List<? extends T> l) { return l.get(0); }
                static <T extends Comparable<T>> T max(Collection<? extends T> c) { return null; }
                static void takesLong(long l) { }
                static void takesObj(Object o) { }
                static void takesBox(Integer i) { }
                static void takesList(List<? extends Number> l) { }
                static void vs(String fmt, Object... args) { }
                static void run(Runnable r) { }
                static <A, B> B apply(Function<A, B> f, A a) { return f.apply(a); }
                GenericCalls(Collection<String> c) { }
                GenericCalls(int... xs) { }

                void go(List<String> ls, ArrayList<Integer> al, Map<String, List<Integer>> m, int i, Integer boxed, char c, String s) {
                    first(ls);
                    max(al);
                    takesLong(i);
                    takesLong(c);
                    takesObj(i);
                    takesObj(ls);
                    takesBox(i);
                    takesList(al);
                    vs("x");
                    vs("x", 1, "two", ls);
                    vs("x", (Object[]) new String[0]);
                    run(() -> { });
                    run(this::go2);
                    apply(String::length, s);
                    apply(x -> x + 1, i);
                    new GenericCalls(ls);
                    new GenericCalls(1, 2, 3);
                    new GenericCalls();
                    Stream.of(1, 2).map(x -> x * 2).collect(Collectors.toList());
                    ls.stream().filter(x -> !x.isEmpty()).forEach(System.out::println);
                    Collections.sort(ls);
                    Arrays.asList(1, 2, 3);
                    String.format("%d", i);
                    Math.max(i, boxed);
                    new HashMap<String, List<Integer>>().put("k", al.subList(0, 1));
                    Objects.requireNonNull(s);
                    m.computeIfAbsent("k", k -> new ArrayList<>()).add(i);
                }

                void go2() { }
            }
            """;

    @Test
    void validCallsOfGenericMethodsLambdasAndVarargsAreNeverTakenForUnresolved() {
        // a hint (~) in a link would mean that the binding was refused: not for code that compiles
        assertThat(links("demo/GenericCalls.java", GENERIC_CALLS)).isNotEmpty().noneMatch(link -> link.contains("~"));
    }

    @Test
    void theHintsOfAnUnresolvedCallAreErasedAndWellFormed() {
        String text = """
                package demo;

                import java.util.*;

                public class Hints {
                    void go(Hints other, List<String> ls, ArrayList<Integer> al, String[][] grid, Map.Entry<String, Integer> entry) {
                        other.nothing(ls, al, grid, entry, 'c', 1.5f, null);
                        other.nothing(Optional.of(ls), new Object() { }, other);
                    }
                }
                """;

        assertThat(links("demo/Hints.java", text)).contains(
                "nothing => demo/Hints#nothing#(~Ljava/util/List;~Ljava/util/ArrayList;~[[Ljava/lang/String;~Ljava/util/Map$Entry;~C~F?)?",
                "nothing => demo/Hints#nothing#(~Ljava/util/Optional;?~Ldemo/Hints;)?");
    }

    private static final String UNKNOWN_TYPES = """
            package demo;

            public class Unknown {
                void takes(Missing m, String s) { }
                void takesArray(Missing[] m) { }
                void takesInt(int i) { }

                void go(Missing missing, Missing[] many, Unknown other) {
                    takes(missing, "s");
                    takes(null, "s");
                    takes("x", "s");
                    takesArray(many);
                    other.takesInt(missing);
                    other.takesInt(undefinedVariable);
                    other.nothing(missing, undefinedVariable, many, null);
                }
            }
            """;

    @Test
    void anArgumentOrAParameterOfAnUnresolvedTypeIsGivenTheBenefitOfTheDoubt() {
        java.util.List<String> links = links("demo/Unknown.java", UNKNOWN_TYPES);

        // the parameter cannot be resolved: whatever the argument is, the binding is kept
        assertThat(links).filteredOn(link -> link.startsWith("takes =>")).hasSize(3).noneMatch(link -> link.contains("~"));
        assertThat(links).contains("takesArray => demo/Unknown#takesArray#([LMissing;)V");
        // the argument cannot be resolved (the binding is kept for takesInt) and is a "?" in the call that has no binding
        assertThat(links).filteredOn(link -> link.startsWith("takesInt =>")).hasSize(2).noneMatch(link -> link.contains("~"));
        // all four arguments are unknown: an unresolved variable, a missing type, an array of it and null
        assertThat(links).contains("nothing => demo/Unknown#nothing#(????)?");
    }

    @Test
    void anArgumentOfATypeVariableWithSeveralBoundsFitsTheParameterOfAnyOfThem() {
        String text = """
                package demo;

                public class Bounds {
                    static void takesRunnable(Runnable r) { }
                    static void takesNumber(Number n) { }
                    static void takesString(String s) { }

                    static <T extends Number & Runnable> void go(T value) {
                        takesRunnable(value);
                        takesNumber(value);
                        takesString(value);
                    }
                }
                """;

        java.util.List<String> links = links("demo/Bounds.java", text);

        // the exact bindings: no hint for what the bounds cover; takesString does not fit and has only hints
        assertThat(links).contains("takesRunnable => demo/Bounds#takesRunnable#(Ljava/lang/Runnable;)V", "takesNumber => demo/Bounds#takesNumber#(Ljava/lang/Number;)V")
                .doesNotContain("takesString => demo/Bounds#takesString#(Ljava/lang/String;)V");
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

        // a reference has no arguments: (*) stands for any parameters; Missing::new: the type cannot be resolved; int[]::new: an array is not a constructor
        assertThat(links).contains("nothing => demo/Refs$Foo#nothing#(*)?", "missing => demo/Refs$Foo#missing#(*)?", "zzz => demo/Refs$Foo#zzz#(*)?")
                .noneMatch(link -> link.contains("Missing") && link.contains("<init>")).noneMatch(link -> link.contains("[I"));
    }

    @Test
    void aReferenceToAConstructorWithGenericParametersNavigatesToItsDeclaration() {
        String text = """
                package demo;

                import java.util.List;
                import java.util.Map;
                import java.util.function.BiFunction;
                import java.util.function.Function;

                public class CtorRefs {
                    static class Foo {
                        Foo(List<String> values) { }
                        Foo(Map<String, List<Integer>> map, int n) { }
                    }

                    void go() {
                        Function<List<String>, Foo> a = Foo::new;
                        BiFunction<Map<String, List<Integer>>, Integer, Foo> b = Foo::new;
                    }
                }
                """;

        // "new" of each reference: the line of the call -> the line of the constructor that the target type selects
        assertThat(targetsOfCalls("demo/CtorRefs.java", text, "<init>")).containsExactly("15 -> 10", "16 -> 11");
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
