/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.model.container;

import org.jd.gui.api.model.Container;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The container of the classes converted from another format (dex, apk...): packages become directories. */
class ConvertedJarContainerTest {

    private record Parent(URI uri) implements Container.Entry {
        public boolean isDirectory() { return false; }
        public String getPath() { return "app.apk"; }
        public Container getContainer() { return null; }
        public Container.Entry getParent() { return null; }
        public URI getUri() { return uri; }
        public long length() { return 0; }
        public long compressedLength() { return 0; }
        public InputStream getInputStream() { return new ByteArrayInputStream(new byte[0]); }
        public Map<Container.EntryPath, Container.Entry> getChildren() { return Map.of(); }
    }

    private static Map<String, byte[]> classes() {
        Map<String, byte[]> classes = new LinkedHashMap<>();
        classes.put("a/b/C.class", new byte[] {1, 2, 3});
        classes.put("a/b/D.class", new byte[] {4});
        classes.put("a/F.class", new byte[] {5, 6});
        classes.put("E.class", new byte[0]);
        return classes;
    }

    private static List<String> paths(Container.Entry directory) {
        return directory.getChildren().values().stream().map(Container.EntryPath::getPath).toList();
    }

    private static Container.Entry child(Container.Entry directory, String path) {
        return directory.getChildren().values().stream().filter(e -> e.getPath().equals(path)).findFirst().orElseThrow();
    }

    @Test
    void packagesBecomeNestedDirectoriesWithTheClassesInThem() {
        Parent parent = new Parent(URI.create("file:/tmp/app.apk"));
        ConvertedJarContainer container = new ConvertedJarContainer(parent, classes());

        assertThat(container.getType()).isEqualTo("jar");
        Container.Entry root = container.getRoot();
        assertThat(root.getPath()).isEmpty();
        assertThat(root.isDirectory()).isTrue();
        assertThat(root.getParent()).isSameAs(parent);
        assertThat(root.getContainer()).isSameAs(container);
        assertThat(root.getUri()).isEqualTo(URI.create("file:/tmp/app.apk!/"));
        assertThat(container.getRootUri()).isEqualTo(URI.create("file:/tmp/app.apk!/"));
        assertThat(root.length()).isZero();
        assertThat(root.compressedLength()).isZero();
        assertThat(paths(root)).containsExactly("a", "E.class");

        Container.Entry a = child(root, "a");
        assertThat(a.isDirectory()).isTrue();
        assertThat(a.getParent()).isSameAs(root);
        assertThat(paths(a)).containsExactly("a/b", "a/F.class");
        Container.Entry b = child(a, "a/b");
        assertThat(paths(b)).containsExactly("a/b/C.class", "a/b/D.class");
    }

    @Test
    void classEntriesGiveTheirBytesAndAUriInsideTheRoot() throws IOException {
        ConvertedJarContainer container = new ConvertedJarContainer(new Parent(URI.create("file:/tmp/app.apk")), classes());
        Container.Entry c = child(child(child(container.getRoot(), "a"), "a/b"), "a/b/C.class");

        assertThat(c.isDirectory()).isFalse();
        assertThat(c.length()).isEqualTo(3);
        assertThat(c.compressedLength()).isEqualTo(3);
        assertThat(c.getUri()).isEqualTo(URI.create("file:/tmp/app.apk!/a/b/C.class"));
        assertThat(c.getChildren()).isEmpty();
        assertThat(c.getParent().getPath()).isEqualTo("a/b");
        try (InputStream in = c.getInputStream()) {
            assertThat(in.readAllBytes()).containsExactly(1, 2, 3);
        }
        try (InputStream in = container.getRoot().getInputStream()) {
            assertThat(in.readAllBytes()).isEmpty();
        }
        try (InputStream in = child(container.getRoot(), "a").getInputStream()) {
            assertThat(in.readAllBytes()).isEmpty();
        }
        assertThat(child(container.getRoot(), "a").length()).isZero();
        assertThat(child(container.getRoot(), "a").getUri()).isEqualTo(URI.create("file:/tmp/app.apk!/a"));
    }

    @Test
    void theTreeCannotBeChangedOnceLoaded() {
        ConvertedJarContainer container = new ConvertedJarContainer(new Parent(URI.create("file:/tmp/app.apk")), classes());
        Map<Container.EntryPath, Container.Entry> rootChildren = container.getRoot().getChildren();
        Container.Entry any = rootChildren.values().iterator().next();

        assertThatThrownBy(() -> rootChildren.put(any, any)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> rootChildren.clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void anEmptyContainerHasNoChildren() {
        ConvertedJarContainer container = new ConvertedJarContainer(new Parent(URI.create("file:/tmp/empty.apk")), Map.of());

        assertThat(container.getRoot().getChildren()).isEmpty();
    }

    @Test
    void whenTheUriCannotBeBuiltTheParentsUriIsUsedInstead() {
        URI odd = URI.create("http://host");
        ConvertedJarContainer container = new ConvertedJarContainer(new Parent(odd), Map.of("p/Q.class", new byte[] {7}));

        assertThat(container.getRoot().getUri()).isEqualTo(odd);
        Container.Entry p = child(container.getRoot(), "p");
        assertThat(p.getUri()).isEqualTo(odd);
        assertThat(child(p, "p/Q.class").getUri()).isEqualTo(odd);
    }
}
