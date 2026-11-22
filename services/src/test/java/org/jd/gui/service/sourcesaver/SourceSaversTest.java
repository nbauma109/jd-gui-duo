/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.sourcesaver;

import org.jd.gui.api.API;
import org.jd.gui.api.model.Container;
import org.jd.gui.model.container.entry.path.FileEntryPath;
import org.jd.gui.spi.SourceSaver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/** The savers that write the files and the archives of the sources: a file, an archive that is copied or rebuilt from its content. */
class SourceSaversTest {

    @TempDir
    Path dir;

    private final List<Double> progress = new ArrayList<>();

    /** A file, a directory or an archive of a container whose root is below a real file (the progress is measured against its size). */
    private static final class FakeEntry implements Container.Entry {
        final String path;
        final URI uri;
        final boolean directory;
        final Function<FakeEntry, InputStream> content;
        final long length;
        final Map<Container.EntryPath, Container.Entry> children = new LinkedHashMap<>();
        Container container;
        Container.Entry parent;

        FakeEntry(String path, URI uri, boolean directory, long length, Function<FakeEntry, InputStream> content) {
            this.path = path;
            this.uri = uri;
            this.directory = directory;
            this.length = length;
            this.content = content;
        }

        FakeEntry add(FakeEntry child) {
            child.container = container;
            child.parent = this;
            children.put(new FileEntryPath(child.path), child);
            return this;
        }

        public boolean isDirectory() { return directory; }
        public String getPath() { return path; }
        public Map<Container.EntryPath, Container.Entry> getChildren() { return children; }
        public Container getContainer() { return container; }
        public Container.Entry getParent() { return parent; }
        public URI getUri() { return uri; }
        public long length() { return length; }
        public long compressedLength() { return length; }
        public InputStream getInputStream() { return content.apply(this); }
    }

    private static InputStream bytes(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    /** A jar of {@code size} bytes on the disk, as the file entry of a container with the given children. */
    private FakeEntry archive(String name, int size, FakeEntry... children) throws IOException {
        Path file = dir.resolve(name);
        Files.write(file, new byte[size]);
        FakeEntry jar = new FakeEntry("", file.toUri(), false, size, e -> bytes(""));
        FakeEntry root = new FakeEntry("", URI.create("jar:" + file.toUri() + "!/"), true, 0, e -> bytes(""));
        root.parent = jar;
        Container container = new Container() {
            public String getType() { return "jar"; }
            public Entry getRoot() { return root; }
        };
        jar.container = container;
        root.container = container;
        for (FakeEntry child : children) {
            root.add(child);
        }
        // the entry that the saver gets is the archive itself, whose children are those of the container
        jar.children.putAll(root.children);
        return jar;
    }

    private static FakeEntry file(String path, String text) {
        return new FakeEntry(path, URI.create("jar:file:/x.jar!/" + path), false, text.length(), e -> bytes(text));
    }

    private API api(File sourceFile) {
        SourceSaver fileSaver = new FileSourceSaverProvider();
        return (API) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {API.class}, (proxy, method, args) -> switch (method.getName()) {
            case "loadSourceFile" -> sourceFile;
            case "getSourceSaver" -> fileSaver;
            case "getPreferences" -> new HashMap<String, String>();
            default -> null;
        });
    }

    @Test
    void aFileIsSavedAtItsPathUnderTheRootAndTheProgressFollows() throws IOException {
        FakeEntry jar = archive("lib.jar", 200, file("hello.txt", "hello world"));
        FakeEntry hello = (FakeEntry) jar.children.values().iterator().next();
        FileSourceSaverProvider saver = new FileSourceSaverProvider();

        saver.save(api(null), dir, hello, () -> 10.0, progress::add, () -> false);

        assertThat(Files.readString(dir.resolve("hello.txt"))).isEqualTo("hello world");
        // the size of the file (11 bytes) against the one of the archive (200 bytes), added to the progress so far
        assertThat(progress).containsExactly(10.0 + 100 * 11 / 200.0);
        assertThat(saver.getFileCount(api(null), hello)).isEqualTo(1);
        assertThat(saver.getSourcePath(hello)).isEqualTo("hello.txt");
        assertThat(saver.getSelectors()).contains("*:file:*");
    }

    @Test
    void theProgressNeverGoesBeyondOneHundred() throws IOException {
        FakeEntry jar = archive("lib.jar", 10, file("big.txt", "0123456789"));

        new FileSourceSaverProvider().save(api(null), dir, (FakeEntry) jar.children.values().iterator().next(), () -> 50.0, progress::add, () -> false);

        assertThat(progress).isEmpty();
    }

    @Test
    void aFileThatCannotBeReadIsReplacedByAnErrorMarker() throws IOException {
        InputStream failing = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("on purpose");
            }
        };
        FakeEntry jar = archive("lib.jar", 100);
        FakeEntry broken = new FakeEntry("broken.txt", URI.create("jar:file:/x.jar!/broken.txt"), false, 5, e -> failing);
        jar.add(broken);
        broken.container = jar.container;

        new FileSourceSaverProvider().saveContent(api(null), dir, dir.resolve("broken.txt"), broken, () -> 0.0, progress::add, () -> false);

        assertThat(Files.readString(dir.resolve("broken.txt"))).isEqualTo("// INTERNAL ERROR //");
    }

    @Test
    void anArchiveThatHasItsSourcesOnTheDiskIsCopiedNextToItself() throws IOException {
        FakeEntry jar = archive("lib.jar", 50, file("A.java", "class A {}"));
        Path sources = dir.resolve("downloaded-sources.jar");
        Files.writeString(sources, "the sources");
        ZipFileSourceSaverProvider saver = new ZipFileSourceSaverProvider();

        saver.save(api(sources.toFile()), dir, jar, () -> 0.0, progress::add, () -> false);

        // the path of the saved sources is the one of the jar, with "-sources" before the extension
        assertThat(Files.readString(dir.resolve("lib-sources.jar"))).isEqualTo("the sources");
        assertThat(saver.getSelectors()).contains("*:file:*.zip", "*:file:*.jar", "*:file:*.war", "*:file:*.jmod", "*:file:*.apk");
    }

    @Test
    void anArchiveWithoutSourcesIsBuiltFromTheSavedContentOfItsEntries() throws IOException {
        FakeEntry jar = archive("lib.jar", 50, file("A.java", "class A {}"), file("B.txt", "bee"));
        Path expected = dir.resolve("lib-sources.jar");

        new ZipFileSourceSaverProvider().save(api(null), dir, jar, () -> 0.0, progress::add, () -> false);

        assertThat(expected).exists();
        try (FileSystem zip = FileSystems.newFileSystem(expected)) {
            assertThat(Files.readString(zip.getPath("/A.java"))).isEqualTo("class A {}");
            assertThat(Files.readString(zip.getPath("/B.txt"))).isEqualTo("bee");
        }
    }

    @Test
    void anArchiveThatCannotBeSavedBecauseItsTargetExistsIsLeftAlone() throws IOException {
        FakeEntry jar = archive("lib.jar", 50, file("A.java", "class A {}"));
        Path target = dir.resolve("lib-sources.jar");
        Files.writeString(target, "already there");

        new ZipFileSourceSaverProvider().save(api(null), dir, jar, () -> 0.0, progress::add, () -> false);

        assertThat(Files.readString(target)).isEqualTo("already there");
    }

    @Test
    void thePathOfTheSourcesOfAnArchiveWithoutAJarExtensionIsItsPathWithTheSourceArchiveSuffix() throws IOException {
        FakeEntry zip = archive("lib.zip", 50);

        assertThat(new ZipFileSourceSaverProvider().getSourcePath(zip)).isEqualTo(zip.getPath() + ".src.zip");
    }
}
