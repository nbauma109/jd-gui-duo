/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.util;

import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ByteCodeWriter;
import org.jd.gui.api.API;
import org.jd.gui.api.model.Container;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MethodPatcherTest {

    static final class FakeContainer implements Container {
        final FakeEntry root;
        FakeContainer(FakeEntry root) { this.root = root; }
        public String getType() { return "jar"; }
        public Entry getRoot() { return root; }
    }

    static final class FakeEntry implements Container.Entry {
        final String path;
        final URI uri;
        Container container;
        Container.Entry parent;
        FakeEntry(String path, URI uri) { this.path = path; this.uri = uri; }
        public boolean isDirectory() { return false; }
        public String getPath() { return path; }
        public Map<Container.EntryPath, Container.Entry> getChildren() { return new LinkedHashMap<>(); }
        public Container getContainer() { return container; }
        public Container.Entry getParent() { return parent; }
        public URI getUri() { return uri; }
        public long length() { return 0; }
        public long compressedLength() { return 0; }
        public InputStream getInputStream() { return new ByteArrayInputStream(new byte[0]); }
    }

    static API api() {
        return (API) Proxy.newProxyInstance(MethodPatcherTest.class.getClassLoader(), new Class<?>[] {API.class},
                (proxy, method, args) -> "getPreferences".equals(method.getName()) ? new HashMap<String, String>() : null);
    }

    static FakeEntry entry() {
        FakeEntry jar = new FakeEntry("", URI.create("file:/nonexistent/fixture.jar"));
        FakeEntry root = new FakeEntry("", URI.create("jar:file:/nonexistent/fixture.jar!/"));
        root.parent = jar;
        FakeContainer container = new FakeContainer(root);
        root.container = container;
        FakeEntry entry = new FakeEntry("demo/Calc.class", URI.create("jar:file:/nonexistent/fixture.jar!/demo/Calc.class"));
        entry.container = container;
        entry.parent = root;
        return entry;
    }

    private static final String FAILED = ByteCodeWriter.DECOMPILATION_FAILED_AT_LINE;

    private static String source(String... members) {
        return "package demo;\n\npublic class Calc {\n" + String.join("\n", members) + "}\n";
    }

    private static String working(String signature, String body) {
        return "    " + signature + " {\n        " + body + "\n    }\n";
    }

    private static String failed(String signature) {
        return "    " + signature + " {\n        // " + FAILED + " 12\n        throw new IllegalStateException();\n    }\n";
    }

    private static String lines(String text) {
        return String.valueOf(text.lines().count());
    }

    @Test
    void theBodyOfAMethodThatFailedToDecompileIsReplacedByTheBodyOfTheOtherVersion() {
        String v1 = source(working("public int ok()", "return 1;"), failed("public int broken(int a)"));
        String v0 = source(working("public int ok()", "return 1;"), working("public int broken(int a)", "return a * 2;"));

        String patched = MethodPatcher.patchCode(api(), v1, v0, entry());

        // the padding that keeps the line count is added with the line separator of the platform
        String expected = """
                package demo;

                public class Calc {
                    public int ok() {
                        return 1;
                    }

                    public int broken(int a) /* Patched from JD-Core V0 */{
                        return a * 2;
                    }@PAD
                }
                """.replace("@PAD", System.lineSeparator());
        assertThat(patched).isEqualTo(expected).doesNotContain(FAILED);
    }

    @Test
    void thePatchedMethodKeepsTheNumberOfLinesOfTheFailedOne() {
        String v1 = source(failed("public int broken(int a)"));
        String v0 = source(working("public int broken(int a)", "return a * 2;"));

        String patched = MethodPatcher.patchCode(api(), v1, v0, entry());

        assertThat(lines(patched)).isEqualTo(lines(v1)); // the line numbers of the rest of the file do not move
    }

    @Test
    void aLongerReplacementIsNotPaddedAndShiftsNothingBack() {
        String v1 = source("    public int broken() {\n        // " + FAILED + " 3\n    }\n");
        String v0 = source("    public int broken() {\n        int a = 1;\n        int b = 2;\n        return a + b;\n    }\n");

        String patched = MethodPatcher.patchCode(api(), v1, v0, entry());

        assertThat(patched).contains("int a = 1;").contains("return a + b;").doesNotContain(FAILED);
    }

    @Test
    void aSourceWithoutAFailedMethodIsReturnedAsItIs() {
        String v1 = source(working("public int ok()", "return 1;"));
        String v0 = source(working("public int ok()", "return 2;"));

        assertThat(MethodPatcher.patchCode(api(), v1, v0, entry())).isEqualTo(v1);
    }

    @Test
    void everyFailedMethodIsPatchedIncludingOverloadsAndTheWorkingOnesAreKept() {
        String v1 = source(failed("public int broken(int a)"), working("public int ok()", "return 1;"), failed("public String broken(String s)"));
        String v0 = source(working("public int broken(int a)", "return a;"), working("public int ok()", "return 99;"), working("public String broken(String s)", "return s;"));

        String patched = MethodPatcher.patchCode(api(), v1, v0, entry());

        assertThat(patched).contains("return a;").contains("return s;").contains("return 1;").doesNotContain("return 99;").doesNotContain(FAILED);
        assertThat(patched.split("Patched from JD-Core V0", -1)).hasSize(3); // two patches
    }

    @Test
    void aFailedMethodWithNoCounterpartInTheOtherVersionStaysAsItIs() {
        String v1 = source(failed("public int onlyInV1()"));
        String v0 = source(working("public int other()", "return 0;"));

        String patched = MethodPatcher.patchCode(api(), v1, v0, entry());

        assertThat(patched).isEqualTo(v1);
    }

    @Test
    void methodsWithoutABodyAreIgnored() {
        String v1 = "package demo;\n\npublic abstract class Calc {\n    public abstract int open();\n    public native int nat();\n" + failed("public int broken()") + "}\n";
        String v0 = "package demo;\n\npublic abstract class Calc {\n    public abstract int open();\n    public native int nat();\n" + working("public int broken()", "return 7;") + "}\n";

        String patched = MethodPatcher.patchCode(api(), v1, v0, entry());

        assertThat(patched).contains("public abstract int open();").contains("public native int nat();").contains("return 7;").doesNotContain(FAILED);
    }
}
