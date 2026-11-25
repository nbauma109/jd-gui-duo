/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.model.container;

import org.jd.gui.api.API;
import org.jd.gui.api.model.Container;
import org.jd.gui.spi.ContainerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** The content of an archive, read as the files of a file system, also an archive that is inside it. */
class GenericContainerTest {

    @TempDir
    Path directory;

    private record Archive(String path, URI uri) implements Container.Entry {
        public boolean isDirectory() { return false; }
        public String getPath() { return path; }
        public Map<Container.EntryPath, Container.Entry> getChildren() { return Map.of(); }
        public Container getContainer() { return null; }
        public Container.Entry getParent() { return null; }
        public URI getUri() { return uri; }
        public long length() { return 0; }
        public long compressedLength() { return 0; }
        public InputStream getInputStream() { return InputStream.nullInputStream(); }
    }

    private static byte[] zip(Map<String, byte[]> files) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> file : files.entrySet()) {
                out.putNextEntry(new ZipEntry(file.getKey()));
                out.write(file.getValue());
            }
        }
        return bytes.toByteArray();
    }

    private static API api(boolean knowsArchives) {
        ContainerFactory factory = (ContainerFactory) Proxy.newProxyInstance(GenericContainerTest.class.getClassLoader(),
                new Class<?>[] {ContainerFactory.class}, (proxy, method, args) -> "make".equals(method.getName())
                        ? new GenericContainer((API) args[0], (Container.Entry) args[1], (Path) args[2]) : null);
        return (API) Proxy.newProxyInstance(GenericContainerTest.class.getClassLoader(), new Class<?>[] {API.class},
                (proxy, method, args) -> "getContainerFactory".equals(method.getName()) && knowsArchives ? factory : null);
    }

    private static Container.Entry child(Container.Entry entry, String path) {
        return entry.getChildren().values().stream().filter(e -> e.getPath().equals(path)).findFirst().orElseThrow();
    }

    private Path archive() throws IOException {
        byte[] inner = zip(Map.of("inside.txt", "inside".getBytes(StandardCharsets.UTF_8)));
        return Files.write(directory.resolve("outer.zip"), zip(Map.of("folder/notes.txt", "some notes".getBytes(StandardCharsets.UTF_8), "inner.zip", inner)));
    }

    @Test
    void theEntriesAreTheFoldersAndTheFilesOfTheArchive() throws IOException {
        Path file = archive();
        Archive parent = new Archive(file.toString(), file.toUri());
        try (FileSystem fileSystem = FileSystems.newFileSystem(file); GenericContainer container = new GenericContainer(api(true), parent, fileSystem.getPath("/"))) {
            Container.Entry root = container.getRoot();

            assertThat(container.getType()).isEqualTo("generic");
            assertThat(root.getPath()).isEmpty();
            assertThat(root.getParent()).isSameAs(parent);
            assertThat(root.getUri().toString()).endsWith("outer.zip!/");
            assertThat(root.getChildren().values()).extracting(Container.EntryPath::getPath).containsExactly("folder", "inner.zip");
            Container.Entry folder = child(root, "folder");
            assertThat(folder.isDirectory()).isTrue();
            assertThat(folder.getParent()).isSameAs(parent);
            Container.Entry notes = child(folder, "folder/notes.txt");
            assertThat(notes.getParent()).isSameAs(folder);
            assertThat(notes.getContainer()).isSameAs(container);
            assertThat(notes.getUri().toString()).endsWith("outer.zip!/folder/notes.txt");
            assertThat(notes.isDirectory()).isFalse();
            assertThat(notes.length()).isEqualTo(10);
            assertThat(notes.compressedLength()).isPositive();
            try (InputStream in = notes.getInputStream()) {
                assertThat(in.readAllBytes()).asString(StandardCharsets.UTF_8).isEqualTo("some notes");
            }
        }
    }

    @Test
    void anArchiveInsideTheArchiveHasEntriesToo() throws IOException {
        Path file = archive();
        try (FileSystem fileSystem = FileSystems.newFileSystem(file);
                GenericContainer container = new GenericContainer(api(true), new Archive(file.toString(), file.toUri()), fileSystem.getPath("/"))) {
            Container.Entry inner = child(container.getRoot(), "inner.zip");

            assertThat(inner.getChildren().values()).extracting(Container.EntryPath::getPath).containsExactly("inside.txt");
        }
    }

    @Test
    void withoutAFactoryForItAnArchiveInsideHasNoEntries() throws IOException {
        Path file = archive();
        // the archive is not a file: its entries are measured by their length
        Archive parent = new Archive("elsewhere.zip", file.toUri());
        try (FileSystem fileSystem = FileSystems.newFileSystem(file); GenericContainer container = new GenericContainer(api(false), parent, fileSystem.getPath("/"))) {
            Container.Entry inner = child(container.getRoot(), "inner.zip");

            assertThat(inner.getChildren()).isEmpty();
            assertThat(inner.compressedLength()).isEqualTo(inner.length());
        }
    }

    @Test
    void anEntryThatIsGoneHasNothingToRead() throws IOException {
        Path folder = Files.createDirectory(directory.resolve("folder"));
        Path file = Files.writeString(folder.resolve("gone.txt"), "x");
        try (GenericContainer container = new GenericContainer(api(false), new Archive("elsewhere", folder.toUri()), folder)) {
            Container.Entry gone = child(container.getRoot(), "gone.txt");
            Files.delete(file);

            assertThat(gone.length()).isEqualTo(-1);
            assertThat(gone.getInputStream()).isNull();
        }
    }
}
