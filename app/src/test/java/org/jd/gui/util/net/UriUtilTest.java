/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.util.net;

import org.jd.gui.api.model.Container;
import org.jd.gui.api.model.Indexes;
import org.jd.gui.view.component.CompiledEntries;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/** The URI of an entry: the one of an inner class leads to the file of its outer class. */
class UriUtilTest {

    @TempDir
    static Path directory;

    private static Map<String, byte[]> classes;

    @BeforeAll
    static void compile() throws IOException {
        classes = CompiledEntries.compile(directory, Map.of("demo/Outer.java", "package demo; public class Outer { public static class Inner {} }"));
    }

    private static Collection<Future<Indexes>> indexes(String type, Container.Entry... entries) {
        Map<String, Collection<Container.Entry>> declarations = Map.of(type, List.of(entries));
        @SuppressWarnings({"unchecked", "rawtypes"})
        Indexes indexes = name -> (Map) declarations;
        return List.of(new CompletableFuture<>(), CompletableFuture.completedFuture(indexes));
    }

    private static URI uri(Collection<Future<Indexes>> indexes, Container.Entry entry) {
        return UriUtil.createURI(CompiledEntries.api(CompiledEntries.preferences()), indexes, entry, "position=3", "demo/Outer$Inner");
    }

    @Test
    void anInnerClassLeadsToItsOuterClassOfTheSameArchive() {
        Container.Entry inner = CompiledEntries.archive(classes, "demo/Outer$Inner.class");
        Container.Entry outer = inner.getParent().getChildren().values().stream().filter(e -> e.getPath().equals("demo/Outer.class")).findFirst().orElseThrow();
        Container.Entry elsewhere = CompiledEntries.archive(classes, "demo/Outer.class");

        URI uri = uri(indexes("demo/Outer", elsewhere, outer), inner);

        assertThat(uri.getPath()).isEqualTo(outer.getUri().getPath());
        assertThat(uri.getQuery()).isEqualTo("position=3");
        assertThat(uri.getFragment()).isEqualTo("demo/Outer$Inner");
    }

    @Test
    void withoutItsOuterClassAnInnerClassKeepsItsOwnPath() {
        Container.Entry inner = CompiledEntries.archive(classes, "demo/Outer$Inner.class");
        CompletableFuture<Indexes> failed = CompletableFuture.failedFuture(new IllegalStateException("broken"));

        assertThat(uri(indexes("demo/Other"), inner).getPath()).isEqualTo(inner.getUri().getPath());
        assertThat(uri(indexes("demo/Outer", CompiledEntries.archive(classes, "demo/Outer.class")), inner).getPath()).isEqualTo(inner.getUri().getPath());
        assertThat(uri(List.of(failed), inner).getPath()).isEqualTo(inner.getUri().getPath());
    }

    @Test
    void anEntryThatHoldsNoTypeKeepsItsPath() {
        Container.Entry text = CompiledEntries.archive(Map.of("demo/notes.txt", new byte[0]), "demo/notes.txt");

        assertThat(uri(List.of(), text)).hasToString("file:/x.jar!/demo/notes.txt?position=3#demo/Outer$Inner");
    }
}
