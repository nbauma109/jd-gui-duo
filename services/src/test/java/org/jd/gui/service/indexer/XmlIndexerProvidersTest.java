/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.indexer;

import org.jd.gui.api.model.Container;
import org.jd.gui.api.model.Indexes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** What is searchable in XML files: their words, and the classes that the beans of a Spring configuration name. */
class XmlIndexerProvidersTest {

    private static final String XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <?style sheet="plain"?>
            <!-- a comment -->
            <beans xmlns="urn:beans" xmlns:x="urn:extra">
              <bean id="greeter" class="demo.Greeter"/>
              <x:other class="demo.NotABean"><![CDATA[ raw text ]]></x:other>
              <name>  some words  </name>
            </beans>
            """;

    private record FakeEntry(String path, String text, URI uri, Container container, Container.Entry parent) implements Container.Entry {
        public boolean isDirectory() { return false; }
        public String getPath() { return path; }
        public Map<Container.EntryPath, Container.Entry> getChildren() { return Map.of(); }
        public Container getContainer() { return container; }
        public Container.Entry getParent() { return parent; }
        public URI getUri() { return uri; }
        public long length() { return text.length(); }
        public long compressedLength() { return length(); }
        public InputStream getInputStream() { return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)); }
    }

    @TempDir
    Path directory;

    /** An entry of an archive that is a file: the progress of the indexing is measured against its size. */
    private Container.Entry entry(String path, String text) throws IOException {
        Path archive = Files.write(directory.resolve("x.jar"), new byte[10_000]);
        FakeEntry file = new FakeEntry("x.jar", "", archive.toUri(), null, null);
        FakeEntry root = new FakeEntry("", "", URI.create("file:/x.jar!/"), null, file);
        Container container = new Container() {
            public String getType() { return "jar"; }
            public Entry getRoot() { return root; }
        };
        return new FakeEntry(path, text, URI.create("file:/x.jar!/" + path), container, root);
    }

    private static final class Index extends HashMap<String, Collection<Object>> {
        private static final long serialVersionUID = 1L;

        @Override
        public Collection<Object> get(Object key) {
            return computeIfAbsent((String) key, k -> new ArrayList<>());
        }
    }

    private final Map<String, Index> indexes = new HashMap<>();

    private void index(AbstractIndexerProvider indexer, Container.Entry entry) {
        @SuppressWarnings({"unchecked", "rawtypes"})
        Indexes target = name -> (Map) indexes.computeIfAbsent(name, n -> new Index());
        indexer.index(null, entry, target, () -> 0, value -> { }, () -> false);
    }

    private Collection<String> keys(String name) {
        return indexes.getOrDefault(name, new Index()).keySet();
    }

    @Test
    void theWordsOfAnXmlFileAreIndexedAndTheClassesOfItsBeansAreTypeReferences() throws IOException {
        Container.Entry entry = entry("beans.xml", XML);

        index(new XmlFileIndexerProvider(), entry);

        assertThat(keys("strings")).contains("1.0", "UTF-8", "style", "sheet=\"plain\"", "a comment", "beans", "bean", "id", "greeter", "class",
                "demo.NotABean", "x", "urn:beans", "urn:extra", "raw text", "some words").doesNotContain("demo.Greeter", "");
        assertThat(keys("typeReferences")).containsExactly("demo/Greeter");
        assertThat(indexes.get("strings").get("greeter")).containsExactly(entry);
    }

    @Test
    void theWordsOfASchemaOrOfAStyleSheetAreIndexed() throws IOException {
        index(new XmlBasedFileIndexerProvider(), entry("schema.xsd", XML));

        assertThat(keys("strings")).contains("1.0", "style", "a comment", "beans", "greeter", "demo.Greeter", "urn:extra", "raw text", "some words")
                .doesNotContain("");
        assertThat(keys("typeReferences")).isEmpty();
    }

    @Test
    void whatComesBeforeAnErrorIsIndexed() throws IOException {
        Container.Entry entry = entry("broken.xml", "<root><first/><second></root>");

        index(new XmlFileIndexerProvider(), entry);

        assertThat(keys("strings")).contains("root", "first", "second");
        indexes.clear();
        index(new XmlBasedFileIndexerProvider(), entry);
        assertThat(keys("strings")).contains("root", "first", "second");
    }

    @Test
    void eachIndexerTakesItsKindOfFiles() {
        assertThat(new XmlFileIndexerProvider().getSelectors()).contains("*:file:*.xml");
        assertThat(new XmlBasedFileIndexerProvider().getSelectors()).contains("*:file:*.xsl", "*:file:*.xslt", "*:file:*.xsd");
    }
}
