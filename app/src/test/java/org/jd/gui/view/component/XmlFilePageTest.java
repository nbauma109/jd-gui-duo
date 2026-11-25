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
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import javax.swing.SwingUtilities;

import jd.core.links.HyperlinkData;

import static org.assertj.core.api.Assertions.assertThat;

/** A Spring configuration: the classes of its beans are links to the types that are indexed. */
class XmlFilePageTest {

    private static final String XML = """
            <beans>
              <bean id="first" class="demo.Foo"/>
              <bean id="second"
                    class="demo.Missing"></bean>
              <property name="classy" value="demo.NotABean"/>
              <bean class="demo.Outer$Inner"/>
            </beans>
            """;

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
        return (API) Proxy.newProxyInstance(XmlFilePageTest.class.getClassLoader(), new Class<?>[] {API.class},
                (proxy, method, args) -> "getPreferences".equals(method.getName()) ? new HashMap<String, String>() : null);
    }

    private static XmlFilePage page() {
        XmlFilePage page = new XmlFilePage(api(), entry("META-INF/spring/beans.xml", XML));
        // a size, as in a window: without it the position of a character is unknown and the caret is not moved
        page.textArea.setSize(800, 600);
        return page;
    }

    /** Opens a location of the page and lets the event thread finish: the caret is moved once the text has a position. */
    private static boolean open(XmlFilePage page, String query) throws Exception {
        boolean[] result = new boolean[1];
        SwingUtilities.invokeAndWait(() -> result[0] = page.openUri(URI.create("file:/x.jar!/META-INF/spring/beans.xml" + query)));
        SwingUtilities.invokeAndWait(() -> { });
        return result[0];
    }

    private static List<String> links(XmlFilePage page) {
        return page.hyperlinks.values().stream()
                .map(link -> XML.substring(link.getStartPosition(), link.getEndPosition()) + " -> " + ((TypeReferencePage.TypeHyperlinkData) link).getInternalTypeName())
                .toList();
    }

    private static void index(XmlFilePage page, String... types) {
        Map<String, Object> declarations = new HashMap<>();
        for (String type : types) {
            declarations.put(type, List.of(entry(type + ".class", "")));
        }
        @SuppressWarnings({"unchecked", "rawtypes"})
        Indexes indexes = name -> "typeDeclarations".equals(name) ? (Map) declarations : null;
        List<Future<Indexes>> futures = List.of(CompletableFuture.completedFuture(indexes));
        page.indexesChanged(futures);
    }

    @Test
    void theClassOfEveryBeanIsALinkToItsType() {
        assertThat(links(page())).containsExactly(
                "\"demo.Foo\" -> demo/Foo", "\"demo.Missing\" -> demo/Missing", "\"demo.Outer$Inner\" -> demo/Outer$Inner");
    }

    @Test
    void onlyTheLinksToIndexedTypesAreEnabled() {
        XmlFilePage page = page();
        assertThat(page.hyperlinks.values()).noneMatch(HyperlinkData::isEnabled);

        index(page, "demo/Foo", "demo/Outer$Inner");

        assertThat(page.hyperlinks.values()).filteredOn(HyperlinkData::isEnabled)
                .extracting(link -> ((TypeReferencePage.TypeHyperlinkData) link).getInternalTypeName())
                .containsExactly("demo/Foo", "demo/Outer$Inner");
        // a type that disappears from the indexes disables its link again
        index(page, "demo/Foo");
        assertThat(page.hyperlinks.values()).filteredOn(HyperlinkData::isEnabled).hasSize(1);
    }

    @Test
    void aSearchHighlightsTheBeanClassesThatMatch() throws Exception {
        XmlFilePage page = page();

        open(page, "?highlightFlags=tr&highlightPattern=Inner");

        // the most inner name of the type is matched: the caret is on that link
        int start = XML.indexOf("\"demo.Outer$Inner\"");
        assertThat(page.textArea.getCaretPosition()).isBetween(start, start + "\"demo.Outer$Inner\"".length());
    }

    @Test
    void aStringSearchAndAPositionAndALineNumberMoveTheCaret() throws Exception {
        XmlFilePage page = page();

        open(page, "?highlightFlags=s&highlightPattern=classy");
        assertThat(page.textArea.getCaretPosition()).isBetween(XML.indexOf("classy"), XML.indexOf("classy") + 6);

        open(page, "?position=3");
        assertThat(page.textArea.getCaretPosition()).isEqualTo(3);

        assertThat(open(page, "?lineNumber=3")).isTrue();
        assertThat(page.textArea.getCaretPosition()).isEqualTo(XML.indexOf("  <bean id=\"second\""));

        // what cannot be read is ignored
        assertThat(open(page, "?lineNumber=x")).isFalse();
        assertThat(open(page, "?position=x")).isFalse();
        assertThat(open(page, "")).isFalse();
    }

    @Test
    void thePageIsNamedAfterItsFileAndIsXml() {
        XmlFilePage page = page();

        assertThat(page.getFileName()).isEqualTo("beans.xml");
        assertThat(page.getUri()).isEqualTo(URI.create("file:/x.jar!/META-INF/spring/beans.xml"));
        assertThat(page.getSyntaxStyle()).isEqualTo("text/xml");
    }
}
