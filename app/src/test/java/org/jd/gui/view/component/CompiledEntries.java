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
import org.jd.gui.util.decompiler.GuiPreferences;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import javax.tools.ToolProvider;

import static org.assertj.core.api.Assertions.assertThat;

/** What the tests of the pages of class files share: sources compiled into the entries of an archive, and the API they are shown with. */
public final class CompiledEntries {

    private CompiledEntries() {
    }

    public static final class FakeEntry implements Container.Entry {
        private final String path;
        private final byte[] bytes;
        private FakeEntry parent;
        private Container container;
        private final Map<Container.EntryPath, Container.Entry> children = new LinkedHashMap<>();

        FakeEntry(String path, byte[] bytes) {
            this.path = path;
            this.bytes = bytes;
        }

        public boolean isDirectory() { return bytes == null; }
        public String getPath() { return path; }
        public Map<Container.EntryPath, Container.Entry> getChildren() { return children; }
        public Container getContainer() { return container; }
        public Container.Entry getParent() { return parent; }
        public URI getUri() { return URI.create("file:/x.jar!/" + path); }
        public long length() { return bytes == null ? 0 : bytes.length; }
        public long compressedLength() { return length(); }
        public InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
    }

    /** An entry in an archive that holds it, together with the other entries given. */
    public static FakeEntry archive(Map<String, byte[]> files, String path) {
        FakeEntry root = new FakeEntry("", null);
        Container container = new Container() {
            public String getType() { return "jar"; }
            public Entry getRoot() { return root; }
        };
        root.container = container;
        // the root of an archive is in the entry of the file of the archive
        root.parent = new FakeEntry("x.jar", new byte[0]);
        FakeEntry found = null;
        for (Map.Entry<String, byte[]> file : files.entrySet()) {
            FakeEntry entry = new FakeEntry(file.getKey(), file.getValue());
            entry.parent = root;
            entry.container = container;
            root.children.put(new org.jd.gui.model.container.entry.path.FileEntryPath(file.getKey()), entry);
            if (file.getKey().equals(path)) {
                found = entry;
            }
        }
        assertThat(found).as(path).isNotNull();
        return found;
    }

    /** Compiles the sources (path and text) and gives the class files by their path. */
    public static Map<String, byte[]> compile(Path directory, Map<String, String> sources) throws IOException {
        Path classes = directory.resolve("classes");
        List<String> arguments = new ArrayList<>(List.of("-g", "-d", classes.toString()));
        for (Map.Entry<String, String> source : sources.entrySet()) {
            Path file = directory.resolve("src").resolve(source.getKey());
            Files.createDirectories(file.getParent());
            Files.writeString(file, source.getValue());
            arguments.add(file.toString());
        }
        int result = ToolProvider.getSystemJavaCompiler().run(null, null, null, arguments.toArray(String[]::new));
        assertThat(result).isZero();
        Map<String, byte[]> compiled = new LinkedHashMap<>();
        try (Stream<Path> files = Files.walk(classes)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                compiled.put(classes.relativize(file).toString().replace('\\', '/'), Files.readAllBytes(file));
            }
        }
        return compiled;
    }

    public static Map<String, String> preferences() {
        Map<String, String> preferences = new HashMap<>();
        preferences.put(GuiPreferences.ERROR_BACKGROUND_COLOR, "0xFF6666");
        return preferences;
    }

    public static API api(Map<String, String> preferences) {
        return (API) Proxy.newProxyInstance(CompiledEntries.class.getClassLoader(), new Class<?>[] {API.class},
                (proxy, method, args) -> "getPreferences".equals(method.getName()) ? preferences : null);
    }

    /** Indexes that are ready, where the index of a name has the keys given. */
    public static List<Future<Indexes>> indexes(Map<String, List<String>> keys) {
        @SuppressWarnings({"unchecked", "rawtypes"})
        Indexes indexes = name -> {
            Map<String, Object> index = new HashMap<>();
            keys.getOrDefault(name, List.of()).forEach(key -> index.put(key, List.of(new FakeEntry(key, new byte[0]))));
            return (Map) index;
        };
        return List.of(CompletableFuture.completedFuture(indexes));
    }
}
