/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.mainpanel;

import org.jd.gui.api.API;
import org.jd.gui.api.feature.ContentIndexable;
import org.jd.gui.api.feature.SourcesSavable;
import org.jd.gui.api.model.Container;
import org.jd.gui.api.model.Indexes;
import org.jd.gui.spi.Indexer;
import org.jd.gui.spi.SourceSaver;
import org.jd.gui.view.component.CompiledEntries;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.swing.JComponent;

import static org.assertj.core.api.Assertions.assertThat;

/** The panel of an archive: how it is indexed, saved as sources and closed. */
class ContainerPanelFactoryProviderTest {

    @TempDir
    Path directory;

    private final List<String> done = new ArrayList<>();
    private Indexer indexer;
    private SourceSaver saver;

    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(ContainerPanelFactoryProviderTest.class.getClassLoader(), new Class<?>[] {type}, handler));
    }

    private final API api = proxy(API.class, (self, method, args) -> switch (method.getName()) {
        case "getPreferences" -> CompiledEntries.preferences();
        case "getIndexer" -> indexer;
        case "getSourceSaver" -> saver;
        default -> null;
    });

    /** An archive that can be closed. */
    private Container archive() {
        Container.Entry entry = CompiledEntries.archive(Map.of("notes.txt", new byte[] {1}), "notes.txt");
        Container.Entry root = entry.getParent();
        return proxy(CloseableContainer.class, (self, method, args) -> switch (method.getName()) {
            case "getRoot" -> root;
            case "getType" -> "jar";
            case "close" -> {
                done.add("closed");
                yield null;
            }
            default -> null;
        });
    }

    private interface CloseableContainer extends Container, Closeable {
    }

    private JComponent panel(Container container) {
        ContainerPanelFactoryProvider factory = new ContainerPanelFactoryProvider();
        assertThat(factory.getTypes()).containsExactly("default");
        return factory.make(api, container);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void theIndexOfAnArchiveHoldsWhatItsIndexerPutsInIt() {
        indexer = proxy(Indexer.class, (self, method, args) -> {
            if ("index".equals(method.getName())) {
                Indexes target = (Indexes) args[2];
                ((Collection) target.getIndex("strings").get("a word")).add(args[1]);
                ((Collection) target.getIndex("strings").get("a word")).add(args[1]);
            }
            return null;
        });
        ContentIndexable panel = (ContentIndexable) panel(archive());

        Indexes indexes = panel.index(api, () -> 0, value -> { }, () -> false);

        assertThat((Collection<Object>) indexes.getIndex("strings").get("a word")).hasSize(2);
        // once built, an index no longer makes up what it does not hold
        assertThat(indexes.getIndex("strings").get("another")).isNull();
        assertThat(indexes.getIndex("nothing")).isNull();
        assertThat(panel.index(api, () -> 0, value -> { }, () -> true).getIndex("strings")).isNull();
        indexer = null;
        assertThat(panel.index(api, () -> 0, value -> { }, () -> false).getIndex("strings")).isNull();
    }

    @Test
    void theSourcesAreSavedIntoAnArchive() throws IOException {
        SourcesSavable panel = (SourcesSavable) panel(archive());
        assertThat(panel.getSourceFileName()).isNull();
        assertThat(panel.getFileCount()).isZero();
        saver = proxy(SourceSaver.class, (self, method, args) -> switch (method.getName()) {
            case "getSourcePath" -> "x-sources.jar";
            case "getFileCount" -> 4;
            case "saveContent" -> {
                Files.writeString(((Path) args[2]).resolve("Saved.java"), "class Saved {}");
                yield null;
            }
            default -> null;
        });
        Path target = directory.resolve("sources").resolve("x-sources.jar");

        panel.save(api, target, () -> 0, value -> { }, () -> false);

        assertThat(panel.getSourceFileName()).isEqualTo("x-sources.jar");
        assertThat(panel.getFileCount()).isEqualTo(4);
        try (FileSystem saved = FileSystems.newFileSystem(URI.create("jar:" + target.toUri()), Map.of())) {
            assertThat(saved.getPath("/Saved.java")).hasContent("class Saved {}");
        }
    }

    @Test
    void closingThePanelClosesItsArchive() throws IOException {
        ((Closeable) panel(archive())).close();

        assertThat(done).containsExactly("closed");
    }

    @Test
    void theMapsOfAnIndexBehaveLikeMaps() {
        ContainerPanelFactoryProvider.DelegatedMap<String, String> map = new ContainerPanelFactoryProvider.DelegatedMap<>(new java.util.HashMap<>());
        map.put("a", "1");
        map.putAll(Map.of("b", "2"));

        assertThat(map).isNotEmpty().hasSize(2).containsEntry("a", "1").containsKey("b").containsValue("2");
        assertThat(map.keySet()).containsExactlyInAnyOrder("a", "b");
        assertThat(map.values()).containsExactlyInAnyOrder("1", "2");
        assertThat(map.entrySet()).hasSize(2);
        assertThat((Object) map).isEqualTo(Map.of("a", "1", "b", "2")).hasSameHashCodeAs(Map.of("a", "1", "b", "2"));
        assertThat(map.remove("a")).isEqualTo("1");
        map.clear();
        assertThat(map).isEmpty();

        ContainerPanelFactoryProvider.DelegatedMapMapWithDefault first = new ContainerPanelFactoryProvider.DelegatedMapMapWithDefault(new java.util.HashMap<>());
        ContainerPanelFactoryProvider.DelegatedMapMapWithDefault same = new ContainerPanelFactoryProvider.DelegatedMapMapWithDefault(new java.util.HashMap<>());
        assertThat(first).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(first.get("strings")).isSameAs(first.get("strings"));
        assertThat(first).isNotEqualTo(same);
    }
}
