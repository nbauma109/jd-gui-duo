/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.controller;

import org.jd.gui.api.API;
import org.jd.gui.api.feature.UriOpenable;
import org.jd.gui.api.model.Container;
import org.jd.gui.api.model.Indexes;
import org.jd.gui.api.model.Type;
import org.jd.gui.model.container.DelegatingFilterContainer;
import org.jd.gui.service.type.ClassFileTypeFactoryProvider;
import org.jd.gui.spi.TypeFactory;
import org.jd.gui.view.bean.OpenTypeListCellBean;
import org.jd.gui.view.bean.QuickOutlineListCellBean;
import org.jd.gui.view.component.CompiledEntries;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;

import javax.swing.JList;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Opening a type by its name, choosing among its locations, and the outline of a type. Nothing is shown. */
class TypeControllersTest {

    @TempDir
    static Path directory;

    private static Map<String, byte[]> classes;

    @BeforeAll
    static void compile() throws IOException {
        assumeFalse(GraphicsEnvironment.isHeadless());
        classes = CompiledEntries.compile(directory, Map.of("demo/Widget.java",
                "package demo; public class Widget { public int size; public static class Part {} public void paint(int times) {} }"));
    }

    private static Container.Entry entry(String type) {
        return CompiledEntries.archive(classes, type + ".class");
    }

    private final TypeFactory factory = new ClassFileTypeFactoryProvider();

    private final API api = (API) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {API.class},
            (proxy, method, args) -> "getTypeFactory".equals(method.getName()) ? factory : null);

    private final ScheduledExecutorService executor = (ScheduledExecutorService) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[] {ScheduledExecutorService.class}, (proxy, method, args) -> {
                if ("execute".equals(method.getName())) {
                    ((Runnable) args[0]).run();
                }
                return null;
            });

    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static void set(Object owner, String name, Object value) throws ReflectiveOperationException {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(owner, value);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Collection<Future<Indexes>> indexes(String... types) {
        Map<String, Collection> declarations = new HashMap<>();
        for (String type : types) {
            declarations.put(type, List.of(entry("demo/Widget")));
        }
        Indexes indexes = name -> (Map) declarations;
        return List.of(CompletableFuture.completedFuture(indexes), new CompletableFuture<>());
    }

    private static List<String> found(OpenTypeController controller, String pattern) throws Exception {
        SwingUtilities.invokeAndWait(() -> controller.updateList(pattern));
        SwingUtilities.invokeAndWait(() -> { });
        JList<?> list = (JList<?>) field(field(controller, "openTypeView"), "openTypeList");
        return Collections.list(((javax.swing.DefaultListModel<?>) list.getModel()).elements()).stream()
                .map(bean -> ((OpenTypeListCellBean) bean).getTypeName()).toList();
    }

    @ParameterizedTest(name = "{0} finds {1}")
    @CsvSource(delimiter = '|', value = {
            "W      | demo/Widget,other/Window",
            "w      | demo/Widget,other/Window,other/walker",
            "Wid    | demo/Widget",
            "wi     | demo/Widget,other/Window",
            "W?n    | other/Window",
            "*get   | demo/Widget",
            "Nothing | ''",
            "''     | ''"})
    void theTypesAreFoundByTheStartOfTheirName(String pattern, String expected) throws Exception {
        OpenTypeController controller = new OpenTypeController(api, executor, null);
        set(controller, "collectionOfFutureIndexes", indexes("demo/Widget", "other/Window", "other/walker", "other/Thing"));

        assertThat(found(controller, pattern)).containsExactlyElementsOf(expected.isEmpty() ? List.of() : List.of(expected.split(",")));
        // the same search again comes from what was kept
        assertThat(found(controller, pattern)).hasSize(expected.isEmpty() ? 0 : expected.split(",").length);
    }

    @ParameterizedTest(name = "{0} finds {1}")
    @CsvSource(delimiter = '|', value = {
            "NPE    | java/lang/NullPointerException",
            "NP     | java/lang/NullPointerException",
            "NuPoE  | java/lang/NullPointerException",
            "NPointer | java/lang/NullPointerException",
            "IOE    | java/io/IOException",
            "AB     | demo/ArrayBuilder",
            "*B     | demo/ArrayBuilder",
            "PE     | ''"})
    void theCapitalsOfAPatternAreTheStartsOfTheWordsOfAName(String pattern, String expected) throws Exception {
        OpenTypeController controller = new OpenTypeController(api, executor, null);
        set(controller, "collectionOfFutureIndexes", indexes("java/lang/NullPointerException", "java/io/IOException", "demo/ArrayBuilder", "demo/Nothing"));

        assertThat(found(controller, pattern)).containsExactlyElementsOf(expected.isEmpty() ? List.of() : List.of(expected.split(",")));
    }

    @Test
    void aTypeThatIsInOnePlaceIsOpenedAtOnce() throws Exception {
        OpenTypeController controller = new OpenTypeController(api, executor, null);
        List<URI> opened = new ArrayList<>();
        java.util.function.Consumer<URI> callback = opened::add;
        set(controller, "openCallback", callback);
        set(controller, "collectionOfFutureIndexes", indexes());
        Container.Entry widget = entry("demo/Widget");

        // the dialog is built on the thread of Swing
        SwingUtilities.invokeAndWait(() -> { });

        controller.onTypeSelected(new Point(1, 1), List.of(widget), "demo/Widget");
        controller.indexesChanged(indexes()); // the dialog is not visible: nothing to refresh

        assertThat(opened).singleElement().satisfies(uri -> {
            assertThat(uri.getPath()).isEqualTo(widget.getUri().getPath());
            assertThat(uri.getFragment()).isEqualTo("demo/Widget");
        });
    }

    @Test
    void theLocationsOfAnInnerClassAreItsOuterClass() {
        SelectLocationController controller = new SelectLocationController(api, null);
        Container.Entry part = entry("demo/Widget$Part");
        Container.Entry outer = part.getParent().getChildren().values().stream().filter(e -> e.getPath().equals("demo/Widget.class"))
                .map(Container.Entry.class::cast).findFirst().orElseThrow();
        Container.Entry other = CompiledEntries.archive(Map.of("a/notes.txt", new byte[0]), "a/notes.txt");

        assertThat(controller.getOuterEntries(List.of(part, other))).containsExactly(other, outer);
    }

    @Test
    void theChosenLocationIsGivenBack() {
        SelectLocationController controller = new SelectLocationController(api, null);
        Container.Entry widget = entry("demo/Widget");
        DelegatingFilterContainer container = new DelegatingFilterContainer(widget.getContainer(), List.of(widget));
        List<Container.Entry> chosen = new ArrayList<>();
        controller.onLocationSelected(Set.of(container), URI.create("file:/x.jar!/missing"), chosen::add);
        controller.onLocationSelected(Set.of(container), container.getRoot().getUri(), chosen::add);

        assertThat(chosen).singleElement().satisfies(entry -> assertThat(entry.getUri()).isEqualTo(container.getRoot().getUri()));
    }

    @Test
    void theOutlineOfATypeListsItsFieldsAndItsMethods() {
        QuickOutlineController controller = new QuickOutlineController(api, null);
        Container.Entry widget = entry("demo/Widget");
        Type type = factory.make(api, widget, "demo/Widget");

        DefaultMutableTreeNode root = controller.createTree(type);

        assertThat(((QuickOutlineListCellBean) root.getUserObject()).getLabel()).isEqualTo("Widget");
        List<QuickOutlineListCellBean> members = Collections.list(root.children()).stream()
                .map(node -> (QuickOutlineListCellBean) ((DefaultMutableTreeNode) node).getUserObject()).toList();
        assertThat(members).extracting(QuickOutlineListCellBean::getLabel).containsExactly("size : int", "Widget()", "paint(int) : void");
        assertThat(members).extracting(QuickOutlineListCellBean::fragment).containsExactly("demo/Widget-size-I", "demo/Widget-<init>-()V",
                "demo/Widget-paint-(I)V");
        assertThat(members.get(0).getTip()).isEqualTo("size : int");
        assertThat(members.get(0).getOpenIcon()).isSameAs(members.get(0).getIcon()).isNotNull();
    }

    @Test
    void choosingAMemberOpensItInThePage() throws Exception {
        QuickOutlineController controller = new QuickOutlineController(api, null);
        List<URI> opened = new ArrayList<>();
        controller.onMemberSelected("demo/Widget-size-I"); // no page yet
        UriOpenable page = opened::add;
        set(controller, "currentUri", URI.create("file:/x.jar!/demo/Widget.class"));
        set(controller, "currentUriOpenable", page);

        controller.onMemberSelected("");
        controller.onMemberSelected("demo/Widget-size-I");

        assertThat(opened).containsExactly(URI.create("file:/x.jar!/demo/Widget.class#demo/Widget-size-I"));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"demo/Widget.class | demo/Widget", "demo/Widget.java | demo/Widget", "demo/notes.txt | "})
    void theTypeOfAFileIsTheOneItIsNamedAfter(String path, String type) {
        assertThat(QuickOutlineController.inferFocusedTypeName(CompiledEntries.archive(Map.of(path, new byte[0]), path))).isEqualTo(type);
        assertThat(QuickOutlineController.inferFocusedTypeName(null)).isNull();
    }
}
