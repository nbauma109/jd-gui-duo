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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import jd.core.links.HyperlinkData;

import static org.assertj.core.api.Assertions.assertThat;

/** The deployment descriptors: the classes of a web.xml and of an ejb-jar.xml are links, and so are the pages of a web.xml. */
class DescriptorXmlFilePagesTest {

    private static final String WEB_XML = """
            <web-app>
              <filter><filter-class>demo.MyFilter</filter-class></filter>
              <listener><listener-class>
                  demo.MyListener
              </listener-class></listener>
              <servlet><servlet-class>demo.MyServlet</servlet-class></servlet>
              <welcome-file-list>
                <welcome-file>index.jsp</welcome-file>
                <welcome-file>   </welcome-file>
                <welcome-file>missing.jsp</welcome-file>
              </welcome-file-list>
              <login-config><form-login-config><form-login-page>/WEB-INF/login.jsp</form-login-page></form-login-config></login-config>
              <description>demo.NotAClass</description>
            </web-app>
            """;

    private static final String EJB_XML = """
            <ejb-jar>
              <enterprise-beans>
                <session><ejb-class>demo.SessionBean</ejb-class><home>demo.SessionHome</home></session>
                <message-driven><ejb-class>demo.Listener</ejb-class></message-driven>
                <entity><prim-key-class>   </prim-key-class></entity>
              </enterprise-beans>
              <display-name>demo.NotAClass</display-name>
            </ejb-jar>
            """;

    private static final class FakeEntry implements Container.Entry {
        final String path;
        final byte[] bytes;
        final URI uri;
        Container container;
        final Map<Container.EntryPath, Container.Entry> children = new LinkedHashMap<>();

        FakeEntry(String path, String text) {
            this.path = path;
            this.bytes = text.getBytes(StandardCharsets.UTF_8);
            this.uri = URI.create("file:/x.war!/" + path);
        }

        FakeEntry add(FakeEntry child) {
            children.put(child, child);
            return this;
        }

        public boolean isDirectory() { return !children.isEmpty(); }
        public String getPath() { return path; }
        public Map<Container.EntryPath, Container.Entry> getChildren() { return children; }
        public Container getContainer() { return container; }
        public Container.Entry getParent() { return null; }
        public URI getUri() { return uri; }
        public long length() { return bytes.length; }
        public long compressedLength() { return length(); }
        public InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
    }

    private static FakeEntry descriptor(String path, String text) {
        FakeEntry root = new FakeEntry("", "")
                .add(new FakeEntry("index.jsp", ""))
                .add(new FakeEntry("WEB-INF", "").add(new FakeEntry("WEB-INF/login.jsp", "")));
        Container container = new Container() {
            public String getType() { return "war"; }
            public Entry getRoot() { return root; }
        };
        FakeEntry entry = new FakeEntry(path, text);
        entry.container = container;
        return entry;
    }

    private static API api() {
        return (API) Proxy.newProxyInstance(DescriptorXmlFilePagesTest.class.getClassLoader(), new Class<?>[] {API.class},
                (proxy, method, args) -> "getPreferences".equals(method.getName()) ? new HashMap<String, String>() : null);
    }

    private static List<String> shown(TypeReferencePage page, String text) {
        return page.hyperlinks.values().stream().map(link -> text.substring(link.getStartPosition(), link.getEndPosition())).toList();
    }

    private static void index(TypeReferencePage page, String... types) {
        Map<String, Object> declarations = new HashMap<>();
        for (String type : types) {
            declarations.put(type, List.of(descriptor(type + ".class", "")));
        }
        @SuppressWarnings({"unchecked", "rawtypes"})
        Indexes indexes = name -> "typeDeclarations".equals(name) ? (Map) declarations : null;
        List<Future<Indexes>> futures = List.of(CompletableFuture.completedFuture(indexes));
        ((org.jd.gui.api.feature.IndexesChangeListener) page).indexesChanged(futures);
    }

    private static List<String> enabled(TypeReferencePage page, String text) {
        return page.hyperlinks.values().stream().filter(HyperlinkData::isEnabled)
                .map(link -> text.substring(link.getStartPosition(), link.getEndPosition())).toList();
    }

    @ParameterizedTest
    @ValueSource(strings = {"\n", "\r\n"})
    void theClassesAndThePagesOfAWebDescriptorAreLinksWhateverTheLineEnds(String lineEnd) {
        String text = WEB_XML.replace("\n", lineEnd);

        WebXmlFilePage page = new WebXmlFilePage(api(), descriptor("WEB-INF/web.xml", text));

        // a blank element is not a link, and neither is another element
        assertThat(shown(page, text)).containsExactly("demo.MyFilter", "demo.MyListener", "demo.MyServlet", "index.jsp", "missing.jsp", "/WEB-INF/login.jsp");
    }

    @Test
    void theClassesOfAWebDescriptorFollowTheIndexesAndItsPagesTheContentOfTheArchive() {
        WebXmlFilePage page = new WebXmlFilePage(api(), descriptor("WEB-INF/web.xml", WEB_XML));

        index(page, "demo/MyServlet");

        // a page is enabled when the archive holds it (a path that starts with a slash is relative to its root)
        assertThat(enabled(page, WEB_XML)).containsExactly("demo.MyServlet", "index.jsp", "/WEB-INF/login.jsp");
        index(page);
        assertThat(enabled(page, WEB_XML)).containsExactly("index.jsp", "/WEB-INF/login.jsp");
    }

    @Test
    void aPathIsSearchedInTheFoldersOfTheArchive() {
        Container.Entry root = descriptor("WEB-INF/web.xml", "").getContainer().getRoot();

        assertThat(WebXmlFilePage.searchEntry(root, "/index.jsp").getPath()).isEqualTo("index.jsp");
        assertThat(WebXmlFilePage.searchEntry(root, "WEB-INF/login.jsp").getPath()).isEqualTo("WEB-INF/login.jsp");
        assertThat(WebXmlFilePage.searchEntry(root, "WEB-INF/none.jsp")).isNull();
        assertThat(WebXmlFilePage.searchEntry(root, "none/login.jsp")).isNull();
    }

    @Test
    void theClassesOfAnEjbDescriptorAreLinksThatFollowTheIndexes() {
        EjbJarXmlFilePage page = new EjbJarXmlFilePage(api(), descriptor("META-INF/ejb-jar.xml", EJB_XML));

        assertThat(shown(page, EJB_XML)).containsExactly("demo.SessionBean", "demo.SessionHome", "demo.Listener");
        index(page, "demo/SessionHome", "demo/Listener");
        assertThat(enabled(page, EJB_XML)).containsExactly("demo.SessionHome", "demo.Listener");
    }

    @Test
    void theDescriptorsAreNamedAfterTheirFileAndAreXml() {
        WebXmlFilePage web = new WebXmlFilePage(api(), descriptor("WEB-INF/web.xml", WEB_XML));
        EjbJarXmlFilePage ejb = new EjbJarXmlFilePage(api(), descriptor("META-INF/ejb-jar.xml", EJB_XML));

        assertThat(web.getFileName()).isEqualTo("web.xml");
        assertThat(ejb.getFileName()).isEqualTo("ejb-jar.xml");
        assertThat(web.getUri()).isEqualTo(URI.create("file:/x.war!/WEB-INF/web.xml"));
        assertThat(ejb.getUri()).isEqualTo(URI.create("file:/x.war!/META-INF/ejb-jar.xml"));
        assertThat(web.getSyntaxStyle()).isEqualTo("text/xml");
        assertThat(ejb.getSyntaxStyle()).isEqualTo("text/xml");
    }
}
