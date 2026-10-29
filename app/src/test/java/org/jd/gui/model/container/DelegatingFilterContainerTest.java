/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */
package org.jd.gui.model.container;

import org.jd.gui.api.model.Container;
import org.jd.gui.model.container.entry.path.FileEntryPath;
import org.jd.gui.model.container.entry.path.SimpleEntryPath;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class DelegatingFilterContainerTest {
    private static final String DIR = "com/example";
    private static final String FOO = DIR + "/Foo.class";
    private static final String FOO_BAR = DIR + "/Foo$Bar.class";
    private static final String OTHER = DIR + "/Other.class";

    private final Map<Container.EntryPath, Container.Entry> rootChildren = new TreeMap<>(Comparator.comparing(Container.EntryPath::getPath));
    private final Map<Container.EntryPath, Container.Entry> children = new TreeMap<>(Comparator.comparing(Container.EntryPath::getPath));
    private final Container.Entry root = entry("", null, true, rootChildren);
    private final Container.Entry dir = entry(DIR, root, true, children);
    private final Container.Entry foo = entry(FOO, dir, false, Map.of());
    private final Container.Entry fooBar = entry(FOO_BAR, dir, false, Map.of());
    private final Container.Entry other = entry(OTHER, dir, false, Map.of());

    DelegatingFilterContainerTest() {
        for (Container.Entry e : List.of(foo, fooBar, other)) {
            children.put(new SimpleEntryPath(e.getPath(), false), e);
        }
        rootChildren.put(new SimpleEntryPath(DIR, true), dir);
    }

    @Test
    void filteredChildrenCanBeLookedUpWithFileEntryPath() {
        DelegatingFilterContainer container = filtered(Set.of(fooBar.getUri()));

        Map<Container.EntryPath, Container.Entry> filteredChildren = container.getEntry(dir.getUri()).getChildren();

        assertNotNull(filteredChildren.get(new FileEntryPath(FOO)));
        assertNotNull(filteredChildren.get(new FileEntryPath(FOO_BAR)));
        assertNull(filteredChildren.get(new FileEntryPath(OTHER)));
        assertEquals(2, filteredChildren.size());
    }

    private DelegatingFilterContainer filtered(Set<URI> extraUris) {
        Container delegate = (Container) Proxy.newProxyInstance(Container.class.getClassLoader(), new Class<?>[] { Container.class }, (p, m, a) -> switch (m.getName()) {
            case "getRoot" -> root;
            case "getType" -> "generic";
            case "hashCode" -> System.identityHashCode(p);
            case "equals" -> p == a[0];
            default -> null;
        });
        DelegatingFilterContainer container = new DelegatingFilterContainer(delegate, List.of(foo), extraUris);
        container.getRoot().getChildren();
        return container;
    }

    private static Container.Entry entry(String path, Container.Entry parent, boolean directory, Map<Container.EntryPath, Container.Entry> kids) {
        URI uri = URI.create("file:///lib.jar!/" + path);
        return (Container.Entry) Proxy.newProxyInstance(Container.Entry.class.getClassLoader(), new Class<?>[] { Container.Entry.class }, (p, m, a) -> switch (m.getName()) {
            case "getUri" -> uri;
            case "getPath" -> path;
            case "getParent" -> parent;
            case "isDirectory" -> directory;
            case "getChildren" -> kids;
            case "hashCode" -> System.identityHashCode(p);
            case "equals" -> p == a[0];
            case "toString" -> path;
            default -> null;
        });
    }
}
