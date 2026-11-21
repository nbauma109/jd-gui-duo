/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.util.container;

import org.jd.gui.api.model.Container;
import org.jd.gui.model.container.ContainerEntryComparator;
import org.jd.gui.model.container.entry.path.FileEntryPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import static org.assertj.core.api.Assertions.assertThat;

class JarContainerEntryUtilTest {

    @TempDir
    Path dir;

    private record Fake(String path, byte[] bytes) implements Container.Entry {
        public boolean isDirectory() { return bytes == null; }
        public String getPath() { return path; }
        public Container getContainer() { return null; }
        public Container.Entry getParent() { return null; }
        public URI getUri() { return URI.create("jar:file:/x.jar!/" + path); }
        public long length() { return bytes == null ? 0 : bytes.length; }
        public long compressedLength() { return length(); }
        public InputStream getInputStream() { return new ByteArrayInputStream(bytes == null ? new byte[0] : bytes); }
        public Map<Container.EntryPath, Container.Entry> getChildren() { return Map.of(); }
    }

    private static byte[] type(String internalName, String... innerTypes) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null);
        for (String inner : innerTypes) {
            String simple = inner.substring(inner.lastIndexOf('$') + 1);
            cw.visitInnerClass(inner, internalName, simple, Opcodes.ACC_STATIC);
        }
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static Map<Container.EntryPath, Container.Entry> entries(Fake... all) {
        Map<Container.EntryPath, Container.Entry> map = new TreeMap<>(ContainerEntryComparator.COMPARATOR);
        for (Fake f : all) {
            map.put(new FileEntryPath(f.path()), f);
        }
        return map;
    }

    private static java.util.List<String> paths(Collection<Container.Entry> entries) {
        return entries.stream().map(Container.EntryPath::getPath).toList();
    }

    @Test
    void entriesWithoutDollarSignAreKeptAsTheyAre() {
        Map<Container.EntryPath, Container.Entry> all = entries(
                new Fake("p/A.class", type("p/A")), new Fake("p/B.txt", new byte[] {1}));

        assertThat(paths(JarContainerEntryUtil.removeInnerTypeEntries(all))).containsExactly("p/A.class", "p/B.txt");
    }

    @Test
    void innerTypesDeclaredByTheirOuterTypeAreRemoved() {
        Map<Container.EntryPath, Container.Entry> all = entries(
                new Fake("p/Outer.class", type("p/Outer", "p/Outer$Inner", "p/Outer$Inner$Deep")),
                new Fake("p/Outer$Inner.class", type("p/Outer$Inner", "p/Outer$Inner$Deep")),
                new Fake("p/Outer$Inner$Deep.class", type("p/Outer$Inner$Deep")),
                new Fake("p/Other.class", type("p/Other")));

        assertThat(paths(JarContainerEntryUtil.removeInnerTypeEntries(all))).containsExactly("p/Other.class", "p/Outer.class");
    }

    @Test
    void aTypeWithADollarInItsNameThatIsNotInnerIsKept() {
        Map<Container.EntryPath, Container.Entry> all = entries(
                new Fake("p/Money$Type.class", type("p/Money$Type")),
                new Fake("p/Plain.class", type("p/Plain")),
                new Fake("p/dir$/Real.class", type("p/dir$/Real")),
                new Fake("p/broken$X.class", new byte[] {1, 2, 3}));

        assertThat(paths(JarContainerEntryUtil.removeInnerTypeEntries(all)))
                .containsExactlyInAnyOrder("p/Money$Type.class", "p/Plain.class", "p/dir$/Real.class", "p/broken$X.class");
    }

    @Test
    void directoriesAreKept() {
        Map<Container.EntryPath, Container.Entry> all = entries(
                new Fake("p/", null),
                new Fake("p/Outer.class", type("p/Outer", "p/Outer$Inner")),
                new Fake("p/Outer$Inner.class", type("p/Outer$Inner")));

        assertThat(paths(JarContainerEntryUtil.removeInnerTypeEntries(all))).containsExactlyInAnyOrder("p/", "p/Outer.class");
    }

    @Test
    void innerTypesWhoseOuterTypeIsMissingAreStillRecognisedByTheirOwnAttribute() {
        Map<Container.EntryPath, Container.Entry> all = entries(
                new Fake("p/Lone.class", type("p/Lone", "p/Lone$Inner")),
                new Fake("p/Lone$Inner.class", type("p/Lone$Inner")),
                new Fake("p/Zed$Self.class", type("p/Zed$Self", "p/Zed$Self")));

        assertThat(paths(JarContainerEntryUtil.removeInnerTypeEntries(all))).containsExactly("p/Lone.class");
    }

    private Path jar(String name, String... entryNames) throws IOException {
        Path jar = dir.resolve(name);
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            for (String entry : entryNames) {
                out.putNextEntry(new ZipEntry(entry));
                out.write(1);
                out.closeEntry();
            }
        }
        return jar;
    }

    private String group(String name, String... entryNames) throws IOException {
        try (JarFile jarFile = new JarFile(jar(name, entryNames).toFile())) {
            return JarContainerEntryUtil.inferGroupFromFile(jarFile);
        }
    }

    @Test
    void theGroupIsThePackageOfTheShallowestClasses() throws IOException {
        assertThat(group("a.jar", "com/acme/tool/A.class", "com/acme/tool/deep/er/B.class")).isEqualTo("com.acme.tool");
    }

    @Test
    void twoSiblingPackagesGiveTheirCommonParent() throws IOException {
        assertThat(group("b.jar", "com/acme/one/A.class", "com/acme/two/B.class")).isEqualTo("com.acme");
    }

    @Test
    void aReverseDomainPackageWinsOverAnotherOneAtTheSameDepth() throws IOException {
        assertThat(group("c.jar", "org/lib/A.class", "other/lib/B.class")).isEqualTo("org.lib");
        assertThat(group("d.jar", "other/lib/B.class", "net/lib/A.class")).isEqualTo("net.lib");
    }

    @Test
    void withoutClassesThePropertiesFilesAreUsed() throws IOException {
        assertThat(group("e.jar", "META-INF/MANIFEST.MF", "my/app/messages.properties")).isEqualTo("my.app");
    }

    @Test
    void withNothingToInferTheDefaultGroupIsUsed() throws IOException {
        assertThat(group("f.jar", "readme.txt")).isEqualTo("com.mycompany");
    }
}
