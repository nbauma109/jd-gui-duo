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
                "Mode => demo/Sample$Mode#null#null", "ON => demo/Sample$Mode#null#null", "Comparable => java/lang/Comparable#null#null");
    }

    @Test
    void fieldsAndMethodsAreLinkedToTheirDeclaringTypeWithTheirDescriptor() {
        assertThat(links("demo/Sample.java", SOURCE)).contains(
                "values => demo/Sample#values#?", "field => demo/Sample$Nested#field#?", "length => java/lang/String#length#()I",
                "max => java/lang/Math#max#(II)I", "valueOf => java/lang/String#valueOf#(I)Ljava/lang/String;",
                "out => java/io/PrintStream#null#null", "println => java/io/PrintStream#println#(Ljava/lang/String;)V",
                "add => java/util/List#add#(Ljava/lang/String;)Z", "run => java/lang/Runnable#run#()V", "size => java/util/ArrayList#size#()I",
                "toUpperCase => java/lang/String#toUpperCase#()Ljava/lang/String;");
    }

    @Test
    void lambdasMethodReferencesCatchClausesAndNestedTypesAreLinked() {
        assertThat(links("demo/Other.java", SOURCE2)).contains(
                "java.io.IOException => java/io/IOException#null#null", "Function => java/util/function/Function#null#null",
                "Callable => java/util/concurrent/Callable#null#null", "Number => java/lang/Number#null#null",
                "valueOf => java/lang/String#valueOf#(Ljava/lang/Object;)Ljava/lang/String;",
                "Inner => demo/Other$Inner#null#null", "A => demo/Other$Inner$Kind#null#null",
                "new IllegalArgumentException(NAME) => java/lang/IllegalArgumentException#<init>#(Ljava/lang/String;)V",
                "IllegalStateException => java/lang/IllegalStateException#null#null", "NullPointerException => java/lang/NullPointerException#null#null",
                "currentThread => java/lang/Thread#currentThread#()Ljava/lang/Thread;", "interrupt => java/lang/Thread#interrupt#()V",
                "length => java/lang/String#length#()I");
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

        assertThat(listener.getDeclarations().keySet()).contains("demo/Over-f-(Ljava/lang/String;)V", "demo/Over-f-([Ljava/lang/String;)V", "demo/Over-f-([Ldemo/Over;)V");
        assertThat(listener.getDeclarations().keySet().stream().filter(key -> key.contains("-f-"))).hasSize(12);
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
