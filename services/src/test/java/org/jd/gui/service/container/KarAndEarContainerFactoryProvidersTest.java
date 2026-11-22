/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */
package org.jd.gui.service.container;

import org.jd.gui.api.API;
import org.jd.gui.api.model.Container;
import org.jd.gui.spi.ContainerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** A KAR or an EAR is recognized from its extension (in an archive) or from what an unpacked one contains (in a folder). */
class KarAndEarContainerFactoryProvidersTest {

    @TempDir
    Path dir;

    private static API api() {
        return (API) Proxy.newProxyInstance(KarAndEarContainerFactoryProvidersTest.class.getClassLoader(), new Class<?>[] {API.class},
                (proxy, method, args) -> "getPreferences".equals(method.getName()) ? new HashMap<String, String>() : null);
    }

    private Path zip(String name, String... entries) throws IOException {
        Path zip = dir.resolve(name);
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            for (String entry : entries) {
                out.putNextEntry(new ZipEntry(entry));
                out.write(1);
                out.closeEntry();
            }
        }
        return zip;
    }

    private static boolean acceptsTheRootOfTheArchive(ContainerFactory factory, Path archive) throws IOException {
        try (FileSystem fileSystem = FileSystems.newFileSystem(archive)) {
            return factory.accept(api(), fileSystem.getPath("/"));
        }
    }

    private static final class FakeParent implements Container.Entry {
        public boolean isDirectory() { return false; }
        public String getPath() { return ""; }
        public Map<Container.EntryPath, Container.Entry> getChildren() { return Map.of(); }
        public Container getContainer() { return null; }
        public Container.Entry getParent() { return null; }
        public URI getUri() { return URI.create("file:/x"); }
        public long length() { return 0; }
        public long compressedLength() { return 0; }
        public InputStream getInputStream() { return new ByteArrayInputStream(new byte[0]); }
    }

    @Test
    void aKarIsRecognizedByItsExtensionOrByItsRepositoryFolder() throws IOException {
        KarContainerFactoryProvider factory = new KarContainerFactoryProvider();

        assertThat(factory.getType()).isEqualTo("kar");
        assertThat(acceptsTheRootOfTheArchive(factory, zip("features.kar", "repository/a.jar"))).isTrue();
        assertThat(acceptsTheRootOfTheArchive(factory, zip("features.KAR", "repository/a.jar"))).isTrue();
        // an archive of another extension, which has no repository folder
        assertThat(acceptsTheRootOfTheArchive(factory, zip("features.zip", "other/a.jar"))).isFalse();
        // an unpacked KAR: a folder of the disk with a repository folder
        Path unpacked = Files.createDirectories(dir.resolve("unpacked"));
        assertThat(factory.accept(api(), unpacked)).isFalse();
        Files.createDirectories(unpacked.resolve("repository"));
        assertThat(factory.accept(api(), unpacked)).isTrue();
    }

    @Test
    void anEarIsRecognizedByItsExtensionOrByItsApplicationDescriptor() throws IOException {
        EarContainerFactoryProvider factory = new EarContainerFactoryProvider();

        assertThat(factory.getType()).isEqualTo("ear");
        assertThat(acceptsTheRootOfTheArchive(factory, zip("app.ear", "META-INF/application.xml"))).isTrue();
        assertThat(acceptsTheRootOfTheArchive(factory, zip("app.zip", "other.txt"))).isFalse();
        Path unpacked = Files.createDirectories(dir.resolve("unpacked-ear"));
        assertThat(factory.accept(api(), unpacked)).isFalse();
        Files.createDirectories(unpacked.resolve("META-INF"));
        Files.writeString(unpacked.resolve("META-INF/application.xml"), "<application/>");
        assertThat(factory.accept(api(), unpacked)).isTrue();
    }

    @Test
    void theFactoriesMakeTheirContainer() throws IOException {
        Path kar = Files.createDirectories(dir.resolve("kar-folder"));
        Files.createDirectories(kar.resolve("repository"));
        Path ear = Files.createDirectories(dir.resolve("ear-folder"));
        Files.createDirectories(ear.resolve("META-INF"));

        assertThat(new KarContainerFactoryProvider().make(api(), new FakeParent(), kar)).isNotNull().extracting(Container::getType).isEqualTo("kar");
        assertThat(new EarContainerFactoryProvider().make(api(), new FakeParent(), ear)).isNotNull().extracting(Container::getType).isEqualTo("ear");
    }
}
