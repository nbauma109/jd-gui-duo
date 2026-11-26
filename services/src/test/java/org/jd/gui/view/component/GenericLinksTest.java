/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.view.component;

import org.fife.ui.rtextarea.DocumentRange;
import org.jd.gui.api.API;
import org.jd.gui.api.model.Container;
import org.jd.gui.util.parser.jdt.ASTParserFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import jd.core.links.HyperlinkData;
import jd.core.links.HyperlinkReferenceData;
import jd.core.links.ReferenceData;

import static org.assertj.core.api.Assertions.assertThat;

/** The links of a source to its own members, when their types are type variables. */
class GenericLinksTest {

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

    private static final String SOURCE = """
            package demo;

            import java.util.List;

            public class Box<T, N extends Number, C extends Comparable<C>, S extends N> {
                T value;
                N number;
                T[] values;

                Box() {}
                Box(T first) {}

                void put(T item) {}
                void add(N amount) {}
                void sort(C key) {}
                void deep(S key) {}
                void all(T... items) {}
                T get() { return value; }
                <E> void generic(E element, int count) {}
                <E extends CharSequence> E text(E element) { return element; }
                <T extends Thread> void shadow(T thread) {}
                <A extends B, B extends Exception> void chain(A failure) {}
                void list(List<T> items) {}
                static <E extends A, A> void forward(E element) {}

                class Inner {
                    void inner(T item) {}
                }

                static class Other<T extends String> {
                    void other(T item) {}
                }

                class Base { class T {} }
                class Derived extends Base { void hidden(T item) {} }
                class Further extends Derived { void further(T item) {} }
                interface Marked { class N {} }
                class Implementer implements Marked { void marked(N item) {} }
                class Secret { private class C {} }
                class Heir extends Secret { void heir(C key) {} }

                void use(Box<String, Integer, String, Integer> box, Other<String> other, Inner inner, Derived derived, Further further,
                        Implementer implementer, Heir heir) {
                    derived.hidden(null);
                    further.further(null);
                    implementer.marked(null);
                    heir.heir(null);
                    box.put("a");
                    box.add(1);
                    box.sort("b");
                    box.deep(2);
                    box.all("c", "d");
                    box.get();
                    box.generic("e", 1);
                    box.text("f");
                    box.shadow(null);
                    box.chain(new Exception());
                    box.list(null);
                    forward("g");
                    inner.inner(null);
                    other.other("h");
                    Object o = box.value;
                    Number n = box.number;
                    Object[] v = box.values;
                    new Box<String, Integer, String, Integer>("i");
                }
            }
            """;

    private static API api() {
        return (API) Proxy.newProxyInstance(GenericLinksTest.class.getClassLoader(), new Class<?>[] {API.class},
                (proxy, method, args) -> "getPreferences".equals(method.getName()) ? new HashMap<String, String>() : null);
    }

    @Test
    void aLinkToAMemberWithTypeVariablesFindsItsDeclaration() {
        FakeEntry jar = new FakeEntry("", URI.create("file:/nonexistent/fixture.jar"), null, null);
        FakeEntry root = new FakeEntry("", URI.create("jar:file:/nonexistent/fixture.jar!/"), null, jar);
        Container container = new Container() {
            public String getType() { return "jar"; }
            public Entry getRoot() { return root; }
        };
        Container.Entry entry = new FakeEntry("demo/Box.java", URI.create("jar:file:/nonexistent/fixture.jar!/demo/Box.java"), container, root);
        ReferenceListener listener = new ReferenceListener(entry);
        SortedMap<Integer, HyperlinkData> hyperlinks = new TreeMap<>();
        listener.setHyperlinks(hyperlinks);
        char[] source = SOURCE.toCharArray();
        ASTParserFactory.getInstance().newASTParser(api(), source, entry.getPath(), jar.uri()).createAST(null).accept(listener.getDeclarationListener());
        listener.init();
        ASTParserFactory.getInstanceWithBindings().newASTParser(api(), source, entry.getPath(), jar.uri()).createAST(null).accept(listener);

        List<String> followed = new ArrayList<>();
        List<String> dead = new ArrayList<>();
        int use = SOURCE.indexOf("void use(");
        for (HyperlinkData hyperlink : hyperlinks.values()) {
            ReferenceData reference = ((HyperlinkReferenceData) hyperlink).getReference();
            if (hyperlink.getStartPosition() < use || reference.getName() == null || !reference.getTypeName().startsWith("demo/")) {
                continue;
            }
            String fragment = reference.getTypeName() + "-" + reference.getName() + "-" + reference.getDescriptor();
            List<DocumentRange> ranges = new ArrayList<>();
            TypePage.matchFragmentAndAddDocumentRange(fragment, listener.getDeclarationListener().getDeclarations(), ranges);
            String linked = SOURCE.substring(hyperlink.getStartPosition(), hyperlink.getEndPosition());
            // the link of a constructor call covers the whole call, and leads to the constructor
            String shown = linked.startsWith("new ") ? "Box" : linked;
            if (ranges.size() == 1 && SOURCE.substring(ranges.get(0).getStartOffset(), ranges.get(0).getEndOffset()).equals(shown)
                    && ranges.get(0).getStartOffset() < use) {
                followed.add(shown);
            } else {
                dead.add(shown + " " + fragment);
            }
        }

        assertThat(dead).isEmpty();
        assertThat(followed).contains("put", "add", "sort", "deep", "all", "get", "generic", "text", "shadow", "chain", "list", "forward", "inner",
                "other", "value", "number", "values", "Box", "hidden", "further", "marked", "heir");
    }
}
