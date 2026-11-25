/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.view;

import org.jd.gui.api.API;
import org.jd.gui.api.model.Container;
import org.jd.gui.api.model.Indexes;
import org.jd.gui.service.type.ClassFileTypeFactoryProvider;
import org.jd.gui.spi.TypeFactory;
import org.jd.gui.view.component.CompiledEntries;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** The tree of the types above and below a type. The dialog is not shown. */
class OpenTypeHierarchyViewTest {

    @TempDir
    static Path directory;

    private static Map<String, byte[]> classes;

    @BeforeAll
    static void compile() throws IOException {
        assumeFalse(GraphicsEnvironment.isHeadless());
        classes = CompiledEntries.compile(directory, Map.of(
                "demo/Base.java", "package demo; public class Base {}",
                "demo/Middle.java", "package demo; public class Middle extends Base {}",
                "demo/Leaf.java", "package demo; public class Leaf extends Middle {}",
                "demo/Other.java", "package demo; public class Other extends Base {}",
                "demo/Task.java", "package demo; public interface Task extends Runnable {}",
                "Parent.java", "public class Parent {}",
                "Lonely.java", "public class Lonely extends Parent {}"));
    }

    private static Container.Entry entry(String type) {
        return CompiledEntries.archive(classes, type + ".class");
    }

    /** Indexes that declare the types, and know the subtypes. */
    private static Collection<Future<Indexes>> indexes(Map<String, List<String>> subTypes, String... declared) {
        Map<String, Object> declarations = new HashMap<>();
        for (String type : declared) {
            declarations.put(type, List.of(entry(type)));
        }
        @SuppressWarnings({"unchecked", "rawtypes"})
        Indexes indexes = name -> (Map) ("typeDeclarations".equals(name) ? declarations : new HashMap<String, Object>(subTypes));
        return List.of(CompletableFuture.completedFuture(indexes));
    }

    private static OpenTypeHierarchyView view() {
        TypeFactory factory = new ClassFileTypeFactoryProvider();
        API api = (API) Proxy.newProxyInstance(OpenTypeHierarchyViewTest.class.getClassLoader(), new Class<?>[] {API.class},
                (proxy, method, args) -> "getTypeFactory".equals(method.getName()) ? factory : null);
        return new OpenTypeHierarchyView(api, null, (point, entries, type) -> { });
    }

    private static JTree tree(OpenTypeHierarchyView view) throws ReflectiveOperationException {
        Field field = OpenTypeHierarchyView.class.getDeclaredField("openTypeHierarchyTree");
        field.setAccessible(true);
        return (JTree) field.get(view);
    }

    private static JTree show(OpenTypeHierarchyView view, Collection<Future<Indexes>> indexes, String type) throws Exception {
        Field field = OpenTypeHierarchyView.class.getDeclaredField("collectionOfFutureIndexes");
        field.setAccessible(true);
        SwingUtilities.invokeAndWait(() -> { });
        field.set(view, indexes);
        SwingUtilities.invokeAndWait(() -> view.updateTree(entry(type), type));
        return tree(view);
    }

    /** The visible rows, indented by their depth; the selected one has a star, one that can be expanded a plus. */
    private static List<String> rows(JTree tree) throws Exception {
        List<String> rows = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            for (int row = 0; row < tree.getRowCount(); row++) {
                TreePath path = tree.getPathForRow(row);
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                String label = node.getUserObject() instanceof OpenTypeHierarchyView.TreeNodeBean bean ? bean.getLabel() : "";
                rows.add("  ".repeat(path.getPathCount() - 2) + label + (tree.isRowSelected(row) ? " *" : "")
                        + (!tree.isExpanded(row) && node.getChildCount() > 0 ? " +" : ""));
            }
        });
        return rows;
    }

    private static final Map<String, List<String>> SUBTYPES = Map.of(
            "java/lang/Object", List.of("demo/Base"),
            "demo/Base", List.of("demo/Other", "demo/Middle"),
            "demo/Middle", List.of("demo/Leaf"));

    @Test
    void theTypesAboveAreShownDownToTheSelectedOneWithItsSiblingsAndOnlyATypeWithSubtypesCanBeExpanded() throws Exception {
        JTree tree = show(view(), indexes(SUBTYPES, "demo/Base", "demo/Middle", "demo/Leaf", "demo/Other"), "demo/Middle");

        assertThat(rows(tree)).containsExactly(
                "Object - java.lang",
                "  Base - demo",
                "    Middle - demo *",
                "      Leaf - demo",
                "    Other - demo");
    }

    @Test
    void expandingATypeShowsItsSubtypes() throws Exception {
        OpenTypeHierarchyView view = view();
        JTree tree = show(view, indexes(SUBTYPES, "demo/Base", "demo/Middle", "demo/Leaf", "demo/Other"), "demo/Other");
        assertThat(rows(tree)).containsExactly("Object - java.lang", "  Base - demo", "    Middle - demo +", "    Other - demo *");

        SwingUtilities.invokeAndWait(() -> tree.expandRow(2));

        assertThat(rows(tree)).containsExactly("Object - java.lang", "  Base - demo", "    Middle - demo", "      Leaf - demo *", "    Other - demo");
    }

    @Test
    void theTreeFollowsTheIndexesFromTheSelectedType() throws Exception {
        OpenTypeHierarchyView view = view();
        JTree tree = show(view, indexes(SUBTYPES, "demo/Base", "demo/Middle", "demo/Leaf", "demo/Other"), "demo/Middle");

        Collection<Future<Indexes>> fewer = indexes(Map.of("demo/Base", List.of("demo/Middle")), "demo/Base", "demo/Middle");
        SwingUtilities.invokeAndWait(() -> view.updateTree(fewer));
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(rows(tree)).containsExactly("Object - java.lang", "  Base - demo", "    Middle - demo *");
    }

    @Test
    void aSubtypeThatNoArchiveDeclaresIsLeftOut() throws Exception {
        JTree tree = show(view(), indexes(Map.of("demo/Base", List.of("demo/Middle", "demo/Gone")), "demo/Base", "demo/Middle"), "demo/Middle");

        assertThat(rows(tree)).containsExactly("Object - java.lang", "  Base - demo", "    Middle - demo *");
    }

    @Test
    void aSuperTypeThatIsInNoArchiveIsTheRootEvenWithoutAPackage() throws Exception {
        JTree tree = show(view(), indexes(Map.of("Parent", List.of("Lonely")), "Lonely"), "Lonely");

        assertThat(rows(tree)).containsExactly("Parent", "  Lonely *");
    }

    @Test
    void anInterfaceIsItsOwnRoot() throws Exception {
        JTree tree = show(view(), indexes(Map.of(), "demo/Task"), "demo/Task");

        assertThat(rows(tree)).containsExactly("Task - demo *");
    }
}
