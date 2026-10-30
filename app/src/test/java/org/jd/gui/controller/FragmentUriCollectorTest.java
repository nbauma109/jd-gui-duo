/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */
package org.jd.gui.controller;

import org.jd.gui.api.model.Container;
import org.jd.gui.api.model.Type;
import org.jd.gui.model.container.entry.path.FileEntryPath;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.jd.gui.view.SearchInConstantPoolsView.SEARCH_CONSTRUCTOR;
import static org.jd.gui.view.SearchInConstantPoolsView.SEARCH_DECLARATION;
import static org.jd.gui.view.SearchInConstantPoolsView.SEARCH_FIELD;
import static org.jd.gui.view.SearchInConstantPoolsView.SEARCH_METHOD;
import static org.jd.gui.view.SearchInConstantPoolsView.SEARCH_REFERENCE;
import static org.jd.gui.view.SearchInConstantPoolsView.SEARCH_STRING;
import static org.jd.gui.view.SearchInConstantPoolsView.SEARCH_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FragmentUriCollectorTest {
    private static final String BASE = "file:///lib.jar!/com/example/";
    private static final String FOO = BASE + "Foo.class";

    private final Map<FileEntryPath, Container.Entry> siblings = new HashMap<>();
    private final Container.Entry parent = entry("com/example", null, siblings);
    private final Container.Entry foo = entry("com/example/Foo.class", parent, Map.of());
    private final Container.Entry fooBar = entry("com/example/Foo$Bar.class", parent, Map.of());

    private final Type fooType = type("com/example/Foo", Map.of("count", "I"), Map.of("<init>", "()V", "getCount", "()I", "<clinit>", "()V"));
    private final Type barType = type("com/example/Foo$Bar", Map.of("value", "I"), Map.of("<init>", "()V", "getValue", "()I"));
    private final Map<Container.Entry, Type> types = Map.of(foo, fooType, fooBar, barType);
    private final Function<Container.Entry, Collection<Type>> loader = e -> types.containsKey(e) ? List.of(types.get(e)) : List.of();

    FragmentUriCollectorTest() {
        siblings.put(new FileEntryPath("com/example/Foo.class"), foo);
        siblings.put(new FileEntryPath("com/example/Foo$Bar.class"), fooBar);
    }

    @Test
    void methodDeclarationShowsTypeAndOnlyMatchingMethod() {
        Set<URI> uris = collect(List.of(foo), "getCount", SEARCH_DECLARATION | SEARCH_METHOD);
        assertEquals(Set.of(uri(FOO, "com/example/Foo"), uri(FOO, "com/example/Foo-getCount-()I")), uris);
    }

    @Test
    void fieldDeclarationShowsTypeAndMatchingField() {
        Set<URI> uris = collect(List.of(foo), "cou*", SEARCH_DECLARATION | SEARCH_FIELD);
        assertEquals(Set.of(uri(FOO, "com/example/Foo"), uri(FOO, "com/example/Foo-count-I")), uris);
    }

    @Test
    void constructorDeclarationMatchesTypeSimpleName() {
        Set<URI> uris = collect(List.of(foo), "Foo", SEARCH_DECLARATION | SEARCH_CONSTRUCTOR);
        assertEquals(Set.of(uri(FOO, "com/example/Foo"), uri(FOO, "com/example/Foo-<init>-()V")), uris);
    }

    @Test
    void methodSearchDoesNotReturnConstructorsOrNonMatchingMembers() {
        Set<URI> uris = collect(List.of(foo), "*", SEARCH_DECLARATION | SEARCH_METHOD);
        assertTrue(uris.contains(uri(FOO, "com/example/Foo-getCount-()I")));
        assertTrue(uris.stream().noneMatch(u -> u.getFragment().contains("<init>")));
        assertEquals(2, uris.size());
    }

    @Test
    void innerTypeMatchIsMappedToOuterEntryWithAncestors() {
        Set<URI> uris = collect(List.of(fooBar), "getValue", SEARCH_DECLARATION | SEARCH_METHOD);
        assertEquals(Set.of(uri(FOO, "com/example/Foo"), uri(FOO, "com/example/Foo$Bar"), uri(FOO, "com/example/Foo$Bar-getValue-()I")), uris);
    }

    @Test
    void referenceMatchWithoutPinpointedMemberShowsOnlyTheTypeNode() {
        Set<URI> uris = collect(List.of(foo), "getCount", SEARCH_REFERENCE | SEARCH_METHOD);
        assertEquals(Set.of(uri(FOO, "com/example/Foo")), uris);
    }

    @Test
    void referenceMatchShowsTheMembersHoldingTheReference() {
        ReferenceScanner.Result scanned = new ReferenceScanner.Result(Set.of("getCount()I", "<clinit>()V", "unlisted()V"), Set.of("countI"));
        Set<URI> uris = FragmentUriCollector.collect(List.of(foo), loader, e -> scanned, "x", SEARCH_REFERENCE | SEARCH_METHOD);
        assertEquals(Set.of(uri(FOO, "com/example/Foo"), uri(FOO, "com/example/Foo-getCount-()I"), uri(FOO, "com/example/Foo-count-I")), uris);
    }

    @Test
    void referenceScanIsSkippedForDeclarationOnlySearch() {
        Set<URI> uris = FragmentUriCollector.collect(List.of(foo), loader, e -> {
            throw new AssertionError("must not scan");
        }, "getCount", SEARCH_DECLARATION | SEARCH_METHOD);
        assertEquals(Set.of(uri(FOO, "com/example/Foo"), uri(FOO, "com/example/Foo-getCount-()I")), uris);
    }

    @Test
    void stringMatchShowsOnlyTheTypeNode() {
        Set<URI> uris = collect(List.of(foo), "hello", SEARCH_DECLARATION | SEARCH_REFERENCE | SEARCH_STRING);
        assertEquals(Set.of(uri(FOO, "com/example/Foo")), uris);
    }

    @Test
    void typeDeclarationShowsTypeNodeOnly() {
        Set<URI> uris = collect(List.of(fooBar), "Bar", SEARCH_DECLARATION | SEARCH_TYPE);
        assertEquals(Set.of(uri(FOO, "com/example/Foo"), uri(FOO, "com/example/Foo$Bar")), uris);
    }

    @Test
    void innerTypesOfSourceFileTypesAreTraversed() {
        Type inner = type("com/example/Foo$Bar", Map.of(), Map.of("getValue", "()I"));
        Type source = type("com/example/Foo", Map.of(), Map.of(), List.of(inner));
        Set<URI> uris = FragmentUriCollector.collect(List.of(foo), e -> List.of(source), "getValue", SEARCH_DECLARATION | SEARCH_METHOD);
        assertEquals(Set.of(uri(FOO, "com/example/Foo"), uri(FOO, "com/example/Foo$Bar"), uri(FOO, "com/example/Foo$Bar-getValue-()I")), uris);
    }

    @Test
    void everyRootTypeOfASourceFileIsCollected() {
        Type second = type("com/example/Second", Map.of(), Map.of("run", "()V"));
        Set<URI> uris = FragmentUriCollector.collect(List.of(foo), e -> List.of(fooType, second), "run", SEARCH_DECLARATION | SEARCH_METHOD);
        assertTrue(uris.contains(uri(FOO, "com/example/Second-run-()V")));
        assertTrue(uris.contains(uri(FOO, "com/example/Foo")));
    }

    @Test
    void dollarInPackageNameIsNotTakenAsInnerTypeSeparator() {
        Container.Entry dir = entry("com/$generated", null, new HashMap<>());
        Container.Entry outer = entry("com/$generated/Outer.class", dir, Map.of());
        Container.Entry inner = entry("com/$generated/Outer$Inner.class", dir, Map.of());
        dir.getChildren().put(new FileEntryPath("com/$generated/Outer.class"), outer);
        Type innerType = type("com/$generated/Outer$Inner", Map.of(), Map.of("go", "()V"));
        String outerUri = "file:///lib.jar!/com/$generated/Outer.class";

        Set<URI> uris = FragmentUriCollector.collect(List.of(inner), e -> List.of(innerType), "go", SEARCH_DECLARATION | SEARCH_METHOD);

        assertEquals(Set.of(uri(outerUri, "com/$generated/Outer"), uri(outerUri, "com/$generated/Outer$Inner"), uri(outerUri, "com/$generated/Outer$Inner-go-()V")), uris);
    }

    @Test
    void entriesWithoutTypeAreIgnored() {
        Container.Entry other = entry("com/example/readme.txt", parent, Map.of());
        assertTrue(collect(List.of(other), "x", SEARCH_DECLARATION | SEARCH_METHOD).isEmpty());
    }

    @Test
    void innerEntryWithMissingOuterIsIgnored() {
        siblings.remove(new FileEntryPath("com/example/Foo.class"));
        assertTrue(collect(List.of(fooBar), "getValue", SEARCH_DECLARATION | SEARCH_METHOD).isEmpty());
    }

    private Set<URI> collect(Collection<Container.Entry> entries, String pattern, int flags) {
        return FragmentUriCollector.collect(entries, loader, pattern, flags);
    }

    private static URI uri(String base, String fragment) {
        URI b = URI.create(base);
        try {
            return new URI(b.getScheme(), b.getHost(), b.getPath(), fragment);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Container.Entry entry(String path, Container.Entry parent, Map<FileEntryPath, Container.Entry> children) {
        URI u = URI.create("file:///lib.jar!/" + path);
        return (Container.Entry) Proxy.newProxyInstance(Container.Entry.class.getClassLoader(), new Class<?>[] { Container.Entry.class }, (p, m, a) -> switch (m.getName()) {
            case "getUri" -> u;
            case "getPath" -> path;
            case "getParent" -> parent;
            case "getChildren" -> children;
            case "hashCode" -> System.identityHashCode(p);
            case "equals" -> p == a[0];
            case "toString" -> path;
            default -> null;
        });
    }

    private static Type type(String name, Map<String, String> fields, Map<String, String> methods) {
        return type(name, fields, methods, List.of());
    }

    private static Type type(String name, Map<String, String> fields, Map<String, String> methods, List<Type> innerTypes) {
        List<Type.Field> fieldList = fields.entrySet().stream().map(e -> member(Type.Field.class, e.getKey(), e.getValue())).toList();
        List<Type.Method> methodList = methods.entrySet().stream().map(e -> member(Type.Method.class, e.getKey(), e.getValue())).toList();
        return (Type) Proxy.newProxyInstance(Type.class.getClassLoader(), new Class<?>[] { Type.class }, (p, m, a) -> switch (m.getName()) {
            case "getName" -> name;
            case "getFields" -> fieldList;
            case "getMethods" -> methodList;
            case "getInnerTypes" -> innerTypes;
            default -> null;
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T member(Class<T> kind, String name, String descriptor) {
        return (T) Proxy.newProxyInstance(kind.getClassLoader(), new Class<?>[] { kind }, (p, m, a) -> switch (m.getName()) {
            case "getName" -> name;
            case "getDescriptor" -> descriptor;
            default -> null;
        });
    }
}
