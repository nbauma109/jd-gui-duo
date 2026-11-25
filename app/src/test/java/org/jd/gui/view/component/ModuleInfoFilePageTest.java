/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.view.component;

import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rsyntaxtextarea.TokenTypes;
import org.jd.gui.api.model.Indexes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import javax.swing.SwingUtilities;
import javax.swing.text.Segment;

import jd.core.links.HyperlinkData;
import jd.core.links.HyperlinkReferenceData;

import static org.assertj.core.api.Assertions.assertThat;

/** The page of a module-info class: its text, its links and what a search highlights in it. */
class ModuleInfoFilePageTest {

    @TempDir
    static Path directory;

    private static Map<String, byte[]> classes;

    @BeforeAll
    static void compile() throws IOException {
        classes = CompiledEntries.compile(directory, Map.of(
                "module-info.java", """
                        module demo.mod {
                            requires java.logging;
                            exports demo.api;
                            opens demo.impl to java.logging;
                            uses demo.api.Service;
                            provides demo.api.Service with demo.impl.ServiceImpl;
                        }
                        """,
                "demo/api/Service.java", "package demo.api; public interface Service {}",
                "demo/impl/ServiceImpl.java", "package demo.impl; public class ServiceImpl implements demo.api.Service {}"));
    }

    private static ModuleInfoFilePage page(Map<String, byte[]> files) {
        return new ModuleInfoFilePage(CompiledEntries.api(CompiledEntries.preferences()), CompiledEntries.archive(files, "module-info.class"));
    }

    private static List<String> shown(ModuleInfoFilePage page, boolean enabledOnly) {
        String text = page.getText();
        return page.hyperlinks.values().stream()
                .filter(link -> !enabledOnly || ((HyperlinkReferenceData) link).getReference().isEnabled())
                .map(link -> text.substring(link.getStartPosition(), link.getEndPosition())).toList();
    }

    private static List<String> highlighted(ModuleInfoFilePage page, String fragmentOrQuery) throws Exception {
        List<String> found = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            page.textArea.setSize(800, 600);
            page.openUri(URI.create("file:/x.jar!/module-info.class" + fragmentOrQuery));
        });
        SwingUtilities.invokeAndWait(() -> page.textArea.getMarkAllHighlightRanges().stream()
                .sorted().forEach(range -> found.add(page.getText().substring(range.getStartOffset(), range.getEndOffset()))));
        return found;
    }

    @Test
    void theModuleIsDecompiledWithLinksToItsModulesPackagesAndTypes() {
        ModuleInfoFilePage page = page(classes);

        assertThat(page.getText()).contains("module demo.mod", "requires java.logging", "exports demo.api", "uses ", "provides ");
        assertThat(shown(page, false)).contains("java.logging", "demo.api", "demo.impl")
                .anySatisfy(link -> assertThat(link).endsWith("Service")).anySatisfy(link -> assertThat(link).endsWith("ServiceImpl"));
        assertThat(page.getSyntaxStyle()).isEqualTo("text/java-module");
        assertThat(page.getFileName()).isEqualTo("module-info.java");
    }

    @Test
    void theLinksFollowTheIndexesOfModulesPackagesAndTypes() {
        ModuleInfoFilePage page = page(classes);
        assertThat(shown(page, true)).isEmpty();

        page.indexesChanged(CompiledEntries.indexes(Map.of("javaModuleDeclarations", List.of("java.logging"))));
        assertThat(shown(page, true)).containsOnly("java.logging");

        page.indexesChanged(CompiledEntries.indexes(Map.of("packageDeclarations", List.of("demo/api"))));
        assertThat(shown(page, true)).containsOnly("demo.api");

        page.indexesChanged(CompiledEntries.indexes(Map.of("typeDeclarations", List.of("demo/impl/ServiceImpl"))));
        assertThat(shown(page, true)).hasSize(1).allSatisfy(link -> assertThat(link).endsWith("ServiceImpl"));

        // indexes that are not ready, or that failed, enable nothing
        CompletableFuture<Indexes> failed = CompletableFuture.failedFuture(new IllegalStateException("broken"));
        List<Future<Indexes>> unusable = List.of(new CompletableFuture<>(), failed);
        page.indexesChanged(unusable);
        assertThat(shown(page, true)).isEmpty();
    }

    @ParameterizedTest(name = "{0} {1} highlights {2}")
    @CsvSource({"M, java.logging, java.logging", "t, ServiceImpl, ServiceImpl", "r, java.logging, ''", "Mr, java.logging, java.logging"})
    void aSearchHighlightsTheReferencesItAsksFor(String flags, String pattern, String expected) throws Exception {
        List<String> found = highlighted(page(classes), "?highlightFlags=" + flags + (flags.contains("r") ? "" : "r") + "&highlightPattern=" + pattern);

        if (expected.isEmpty()) {
            assertThat(found).isEmpty();
        } else {
            assertThat(found).isNotEmpty().allSatisfy(text -> assertThat(text).endsWith(expected));
        }
    }

    @Test
    void theDeclarationOfTheModuleIsHighlightedByASearchOfDeclarationsAndByItsFragment() throws Exception {
        ModuleInfoFilePage page = page(classes);

        assertThat(highlighted(page, "?highlightFlags=dM&highlightPattern=demo.mod")).containsExactly("demo.mod");
        assertThat(highlighted(page, "?highlightFlags=d&highlightPattern=demo.mod")).isEmpty();
        assertThat(highlighted(page, "?highlightPattern=demo.mod")).isEmpty();
        assertThat(highlighted(page, "#module-info")).containsExactly("demo.mod");
        assertThat(highlighted(page, "#other")).isEmpty();
    }

    @Test
    void aClassThatCannotBeReadShowsAnError() {
        ModuleInfoFilePage page = page(Map.of("module-info.class", new byte[] {1, 2, 3}));

        assertThat(page.getText()).isEqualTo("// INTERNAL ERROR //");
        assertThat(page.hyperlinks).isEmpty();
    }

    @Test
    void theWordsOfAModuleDeclarationAreKeywords() {
        char[] line = "  requires transitive demo.api; // x_1".toCharArray();

        Token token = new ModuleInfoFilePage.ModuleInfoTokenMaker().getTokenList(new Segment(line, 0, line.length), TokenTypes.NULL, 0);

        List<String> keywords = new ArrayList<>();
        List<String> identifiers = new ArrayList<>();
        for (; token != null && token.isPaintable(); token = token.getNextToken()) {
            if (token.getType() == TokenTypes.RESERVED_WORD) {
                keywords.add(token.getLexeme());
            } else if (token.getType() == TokenTypes.IDENTIFIER) {
                identifiers.add(token.getLexeme());
            }
        }
        assertThat(keywords).containsExactly("requires", "transitive");
        assertThat(identifiers).containsExactly("demo.api", "x_1");
    }

    @Test
    void aLinkOfTheModuleKeepsTheNameOfAPackageAsAPath() {
        assertThat(page(classes).listener.getReferences()).extracting(reference -> reference.getTypeName()).contains("demo/api", "demo/impl");
        assertThat(page(classes).hyperlinks.values()).extracting(HyperlinkData::getStartPosition).isSorted();
    }
}
