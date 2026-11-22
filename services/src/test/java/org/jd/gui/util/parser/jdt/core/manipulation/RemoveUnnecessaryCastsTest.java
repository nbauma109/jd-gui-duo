/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.util.parser.jdt.core.manipulation;

import org.jd.gui.api.API;
import org.jd.gui.api.model.Container;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The casts that the compiler reports as unnecessary are removed from a decompiled source. */
class RemoveUnnecessaryCastsTest {

    private record FakeEntry(String path, URI uri, Container container, Container.Entry parent) implements Container.Entry {
        public boolean isDirectory() { return false; }
        public String getPath() { return path; }
        public Map<Container.EntryPath, Container.Entry> getChildren() { return Map.of(); }
        public Container getContainer() { return container; }
        public Container.Entry getParent() { return parent; }
        public URI getUri() { return uri; }
        public long length() { return 0; }
        public long compressedLength() { return 0; }
        public InputStream getInputStream() { return new ByteArrayInputStream(new byte[0]); }
    }

    private static API api() {
        return (API) Proxy.newProxyInstance(RemoveUnnecessaryCastsTest.class.getClassLoader(), new Class<?>[] {API.class},
                (proxy, method, args) -> "getPreferences".equals(method.getName()) ? new HashMap<String, String>() : null);
    }

    private static Container.Entry entry(String path) {
        FakeEntry jar = new FakeEntry("", URI.create("file:/nonexistent/fixture.jar"), null, null);
        FakeEntry root = new FakeEntry("", URI.create("jar:file:/nonexistent/fixture.jar!/"), null, jar);
        Container container = new Container() {
            public String getType() { return "jar"; }
            public Entry getRoot() { return root; }
        };
        return new FakeEntry(path, URI.create("jar:file:/nonexistent/fixture.jar!/" + path), container, root);
    }

    private static String process(String source) {
        return new RemoveUnnecessaryCasts(api(), entry("demo/Casts.java")).process(source);
    }

    @Test
    void aCastToTheTypeOfTheExpressionIsRemoved() {
        String source = """
                package demo;

                public class Casts {
                    String go(String s, Object o) {
                        String a = (String) s;
                        Object b = (Object) s;
                        String c = (String) o;
                        return ((String) s) + (String) c;
                    }
                }
                """;

        String result = process(source);

        // (String) o is a real cast: the other ones add nothing
        assertThat(result).contains("String a = s;", "Object b = s;", "String c = (String) o;").doesNotContain("(String) s", "(Object) s");
    }

    @Test
    void aSourceWithoutUnnecessaryCastIsKeptAsItIs() {
        String source = """
                package demo;

                public class Casts {
                    int go(Object o, long l) {
                        return ((String) o).length() + (int) l;
                    }
                }
                """;

        assertThat(process(source)).isEqualTo(source);
    }

    @Test
    void aSourceThatDoesNotCompileIsKeptAsItIs() {
        String source = "package demo;\n\npublic class Casts {\n    void go( {\n";

        assertThat(process(source)).isEqualTo(source);
    }
}
