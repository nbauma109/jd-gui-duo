/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.view.component;

import org.jd.gui.api.API;
import org.jd.gui.api.model.Container;
import org.jd.gui.api.model.Indexes;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.jar.Manifest;

import javax.swing.SwingUtilities;

import jd.core.links.HyperlinkData;

import static org.assertj.core.api.Assertions.assertThat;

/** A manifest: the main class and the agent class are links, also when the value continues on the next line, and it can be beautified. */
class ManifestFilePageTest {

    /** The lines of a manifest are limited to 72 bytes: a long value goes on, after a space, on the next one. */
    private static final String LONG_NAME = "demo.application.with.a.very.long.package.name.that.needs.a.second.line.Application";

    private static final String MANIFEST = "Manifest-Version: 1.0\r\n"
            + "Main-Class: " + LONG_NAME.substring(0, 60) + "\r\n " + LONG_NAME.substring(60) + "\r\n"
            + "Premain-Class: demo.Agent\r\n"
            + "Created-By: test\r\n"
            + "\r\n"
            + "Name: demo/\r\n"
            + "Sealed: true\r\n";

    private record FakeEntry(String path, byte[] bytes, URI uri, Container container, Container.Entry parent) implements Container.Entry {
        public boolean isDirectory() { return false; }
        public String getPath() { return path; }
        public Map<Container.EntryPath, Container.Entry> getChildren() { return Map.of(); }
        public Container getContainer() { return container; }
        public Container.Entry getParent() { return parent; }
        public URI getUri() { return uri; }
        public long length() { return bytes.length; }
        public long compressedLength() { return length(); }
        public InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
    }

    private static Container.Entry entry(String path, String text) {
        FakeEntry root = new FakeEntry("", new byte[0], URI.create("file:/x.jar!/"), null, null);
        Container container = new Container() {
            public String getType() { return "jar"; }
            public Entry getRoot() { return root; }
        };
        return new FakeEntry(path, text.getBytes(StandardCharsets.UTF_8), URI.create("file:/x.jar!/" + path), container, root);
    }

    private static API api() {
        return (API) Proxy.newProxyInstance(ManifestFilePageTest.class.getClassLoader(), new Class<?>[] {API.class},
                (proxy, method, args) -> "getPreferences".equals(method.getName()) ? new HashMap<String, String>() : null);
    }

    private static ManifestFilePage page() {
        return new ManifestFilePage(api(), entry("META-INF/MANIFEST.MF", MANIFEST));
    }

    /** The class that each link shows, and where it leads. */
    private static List<String> links(ManifestFilePage page) {
        String text = page.getText();
        return page.hyperlinks.values().stream()
                .map(link -> ManifestFilePage.getValue(text, link.getStartPosition(), link.getEndPosition())
                        + " -> " + ((ManifestFilePage.ManifestHyperlinkData) link).fragment)
                .toList();
    }

    private static final List<String> EXPECTED = List.of(
            LONG_NAME + " -> " + LONG_NAME.replace('.', '/') + "-main-([Ljava/lang/String;)V",
            "demo.Agent -> demo/Agent-premain-(*)?");

    private static void index(ManifestFilePage page, String... types) {
        Map<String, Object> declarations = new HashMap<>();
        for (String type : types) {
            declarations.put(type, List.of(entry(type + ".class", "")));
        }
        @SuppressWarnings({"unchecked", "rawtypes"})
        Indexes indexes = name -> "typeDeclarations".equals(name) ? (Map) declarations : null;
        List<Future<Indexes>> futures = List.of(CompletableFuture.completedFuture(indexes));
        page.indexesChanged(futures);
    }

    private static long enabled(ManifestFilePage page) {
        return page.hyperlinks.values().stream().filter(link -> ((ManifestFilePage.ManifestHyperlinkData) link).enabled).count();
    }

    @Test
    void theMainClassAndTheAgentClassAreLinksEvenAcrossTwoLines() {
        assertThat(links(page())).isEqualTo(EXPECTED);
    }

    @Test
    void onlyTheLinksToIndexedClassesAreEnabled() {
        ManifestFilePage page = page();

        index(page, "demo/Agent");

        assertThat(enabled(page)).isEqualTo(1);
        assertThat(page.hyperlinks.values()).extracting(HyperlinkData::getStartPosition).hasSize(2);
        index(page, LONG_NAME.replace('.', '/'), "demo/Agent");
        assertThat(enabled(page)).isEqualTo(2);
    }

    @Test
    void beautifyingJoinsTheLongLinesAndSortsTheAttributesAndTheLinksFollowTheText() throws Exception {
        ManifestFilePage page = page();

        SwingUtilities.invokeAndWait(() -> page.beautifyCheckbox.setSelected(true));

        assertThat(page.getText()).contains("Main-Class: " + LONG_NAME).doesNotContain("\r\n " + LONG_NAME.substring(60));
        assertThat(page.getText().indexOf("Created-By")).isLessThan(page.getText().indexOf("Main-Class"));
        // the links are those of the text that is shown now: none is left over from the text that was shown before
        assertThat(links(page)).containsExactlyInAnyOrderElementsOf(EXPECTED);
        index(page, "demo/Agent");
        assertThat(enabled(page)).isEqualTo(1);

        SwingUtilities.invokeAndWait(() -> page.beautifyCheckbox.setSelected(false));

        assertThat(page.getText()).isEqualTo(MANIFEST);
        assertThat(links(page)).isEqualTo(EXPECTED);
        // the indexes are not read again by the checkbox: the rebuilt links are enabled as before
        assertThat(enabled(page)).isEqualTo(1);
    }

    @Test
    void theHeadersAreDumpedSortedWithTheSectionsOfTheEntries() throws Exception {
        Manifest manifest = new Manifest(new ByteArrayInputStream(MANIFEST.getBytes(StandardCharsets.UTF_8)));
        StringWriter out = new StringWriter();

        ManifestFilePage.dumpManifestHeaders(manifest, new PrintWriter(out)).flush();

        assertThat(out.toString()).contains("Manifest-Version: 1.0", "Entry: demo/", "Sealed: true");
    }

    @Test
    void thePageIsNamedAfterItsFile() {
        assertThat(page().getFileName()).isEqualTo("MANIFEST.MF");
        assertThat(page().getUri()).isEqualTo(URI.create("file:/x.jar!/META-INF/MANIFEST.MF"));
    }

    @Test
    void theValueOfALinkIgnoresTheSpacesAndEndsAtTheEndOfTheLine() {
        ManifestFilePage page = page();

        assertThat(page.skipSeparators("  \t\r\n x", 0)).isEqualTo(6);
        assertThat(page.skipSeparators("   ", 0)).isEqualTo(3);
        assertThat(page.searchEndIndexOfValue("Main-Class: a.B", 0, 12)).isEqualTo(15);
        assertThat(page.searchEndIndexOfValue("Main-Class: a.B\nX", 0, 12)).isEqualTo(15);
        assertThat(page.searchEndIndexOfValue("Main-Class: a.B\rX", 0, 12)).isEqualTo(15);
    }
}
