/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.view.component;

import org.jd.gui.api.API;
import org.jd.gui.api.feature.ContentSearchable.SearchType;
import org.jd.gui.api.model.Container;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.SwingUtilities;

import static org.assertj.core.api.Assertions.assertThat;

/** The page of a class: the decompiled source until the original one is loaded. */
class DynamicPageTest {

    private static final String SOURCE = """
            package demo;

            // the original source
            public class Greeter {
                public String greet() {
                    return "hello";
                }
            }
            """;

    @TempDir
    static Path directory;

    private static Map<String, byte[]> classes;

    @BeforeAll
    static void compile() throws IOException {
        classes = CompiledEntries.compile(directory, Map.of("demo/Greeter.java", SOURCE));
    }

    private final List<API.LoadSourceListener> loading = new ArrayList<>();

    private API api(String source) {
        Map<String, String> preferences = CompiledEntries.preferences();
        return (API) Proxy.newProxyInstance(DynamicPageTest.class.getClassLoader(), new Class<?>[] {API.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getPreferences" -> preferences;
                    case "getSource" -> source;
                    case "loadSource" -> {
                        loading.add((API.LoadSourceListener) args[1]);
                        yield null;
                    }
                    default -> null;
                });
    }

    private static Container.Entry entry() {
        return CompiledEntries.archive(classes, "demo/Greeter.class");
    }

    @Test
    void theSourceThatIsThereIsShownAtOnce() {
        DynamicPage page = new DynamicPage(api(SOURCE), entry());

        assertThat(page.getPage()).isInstanceOf(JavaFilePage.class);
        assertThat(page.getPage().getText()).isEqualTo(SOURCE);
        assertThat(loading).isEmpty();
    }

    @Test
    void theDecompiledSourceIsReplacedByTheOriginalOneOnceItIsLoaded() throws Exception {
        Container.Entry entry = entry();
        DynamicPage page = new DynamicPage(api(null), entry);
        assertThat(page.getPage()).isInstanceOf(ClassFilePage.class);
        assertThat(loading).containsExactly(page);
        page.indexesChanged(List.of());
        URI uri = URI.create("file:/x.jar!/demo/Greeter.class?highlightFlags=md&highlightPattern=greet");
        SwingUtilities.invokeAndWait(() -> page.openUri(uri));

        page.sourceLoaded(SOURCE);
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(page.getPage()).isInstanceOf(JavaFilePage.class);
        assertThat(page.getPage().getText()).contains("// the original source");
        assertThat(page.getComponents()).containsExactly(page.getPage());
        // the new page is opened as the one before was
        assertThat(page.getPage().textArea.getMarkAllHighlightRanges()).hasSize(1);
        assertThat(page.getEntry()).isSameAs(entry);
        assertThat(page.getUri()).isEqualTo(entry.getUri());
    }

    @Test
    void whatIsAskedOfThePageIsAskedOfTheOneThatIsShown() throws Exception {
        DynamicPage page = new DynamicPage(api(null), entry());
        SearchType search = new SearchType(false, false, false, true, true);

        SwingUtilities.invokeAndWait(() -> {
            assertThat(page.highlightText("greet", search)).isTrue();
            page.findNext("greet", search);
            assertThat(page.getSelectedText()).isEqualToIgnoringCase("greet");
            page.findPrevious("greet", search);
            page.selectAll();
            assertThat(page.getSelectedText()).isEqualTo(page.getPage().getText());
            page.goToLineNumber(1);
            page.preferencesChanged(CompiledEntries.preferences());
        });

        assertThat(page.getFileName()).isEqualTo("demo/Greeter.java");
        assertThat(page.getMaximumLineNumber()).isPositive().isEqualTo(page.getPage().getMaximumLineNumber());
        assertThat(page.checkLineNumber(1)).isTrue();
        assertThat(page.getFocusedTypeName()).isEqualTo(page.getPage().getFocusedTypeName());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        page.save(api(null), out);
        assertThat(out.toString(StandardCharsets.UTF_8)).contains("class Greeter");
    }

    @Test
    void theEntryOfTheOriginalSourceIsTheOneOfTheClassWithAnotherContent() throws IOException {
        Container.Entry entry = entry();
        Container.Entry delegated = new DynamicPage.DelegatedEntry(entry, SOURCE);

        assertThat(delegated.getPath()).isEqualTo(entry.getPath());
        assertThat(delegated.getUri()).isEqualTo(entry.getUri());
        assertThat(delegated.getContainer()).isSameAs(entry.getContainer());
        assertThat(delegated.getParent()).isSameAs(entry.getParent());
        assertThat(delegated.getChildren()).isSameAs(entry.getChildren());
        assertThat(delegated.isDirectory()).isFalse();
        assertThat(delegated.length()).isEqualTo(entry.length());
        assertThat(delegated.compressedLength()).isEqualTo(entry.compressedLength());
        try (java.io.InputStream in = delegated.getInputStream()) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(SOURCE);
        }
    }
}
