/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.fileloader;

import org.jd.gui.api.API;
import org.jd.gui.api.feature.UriGettable;
import org.jd.gui.api.model.Container;
import org.jd.gui.spi.PanelFactory;
import org.jd.gui.util.conversion.TinyDex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Supplier;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JPanel;

import static org.assertj.core.api.Assertions.assertThat;

/** The loaders of the dex and apk files: what they accept, and the classes of a dex shown as a jar. */
class AndroidFileLoaderProvidersTest {

    @TempDir
    Path dir;

    private final List<String> panels = new ArrayList<>();
    private Container converted;

    private static final class Panel extends JPanel implements UriGettable {
        private static final long serialVersionUID = 1L;

        @Override
        public URI getUri() {
            return URI.create("file:/panel");
        }
    }

    private API api() {
        PanelFactory factory = new PanelFactory() {
            @Override
            public String[] getTypes() { return new String[0]; }

            @Override
            @SuppressWarnings("unchecked")
            public <T extends JComponent & UriGettable> T make(API api, Container container) {
                converted = container;
                return (T) new Panel();
            }
        };
        return (API) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {API.class}, (proxy, method, args) -> switch (method.getName()) {
            case "getMainPanelFactory" -> factory;
            case "addPanel" -> {
                panels.add(((File) args[0]).getName() + " | " + args[1] + " | " + args[3]);
                @SuppressWarnings("unchecked")
                Supplier<Icon> icon = (Supplier<Icon>) args[2];
                assertThat(icon.get()).isNull(); // no node factory in this API: no icon
                yield null;
            }
            case "getPreferences" -> new HashMap<String, String>();
            default -> null;
        });
    }

    @Test
    void aReadableFileOfTheRightExtensionIsAccepted() throws IOException {
        Path dex = Files.write(dir.resolve("classes.dex"), new byte[] {1});
        Path upper = Files.write(dir.resolve("CLASSES2.DEX"), new byte[] {1});
        Path apk = Files.write(dir.resolve("app.apk"), new byte[] {1});
        DexFileLoaderProvider dexLoader = new DexFileLoaderProvider();
        ApkFileLoaderProvider apkLoader = new ApkFileLoaderProvider();

        assertThat(dexLoader.accept(api(), dex.toFile())).isTrue();
        assertThat(dexLoader.accept(api(), upper.toFile())).isTrue();
        assertThat(dexLoader.accept(api(), apk.toFile())).isFalse();
        assertThat(dexLoader.accept(api(), dir.resolve("missing.dex").toFile())).isFalse();
        assertThat(dexLoader.accept(api(), Files.createDirectory(dir.resolve("folder.dex")).toFile())).isFalse();
        assertThat(apkLoader.accept(api(), apk.toFile())).isTrue();
        assertThat(dexLoader.getExtensions()).containsExactly("dex");
        assertThat(apkLoader.getExtensions()).containsExactly("apk");
        assertThat(dexLoader.getDescription()).isNotBlank();
        assertThat(apkLoader.getDescription()).isNotBlank();
    }

    @Test
    void theClassesOfADexAreShownAsAJarInAPanelNamedAfterTheFile() throws IOException {
        Path dex = Files.write(dir.resolve("tiny.dex"), TinyDex.bytes());

        boolean loaded = new DexFileLoaderProvider().load(api(), dex.toFile());

        assertThat(loaded).isTrue();
        assertThat(panels).containsExactly("tiny.dex | tiny.dex | Location: " + dex);
        assertThat(converted.getType()).isEqualTo("jar");
        // the package becomes a directory holding the classes
        assertThat(converted.getRoot().getChildren().values()).extracting(Container.EntryPath::getPath).containsExactly("demo");
    }

    @Test
    void aFileThatIsNotADexIsNotLoaded() throws IOException {
        Path garbage = Files.write(dir.resolve("garbage.dex"), "not a dex".getBytes(StandardCharsets.UTF_8));

        assertThat(new DexFileLoaderProvider().load(api(), garbage.toFile())).isFalse();
        assertThat(panels).isEmpty();
    }
}
