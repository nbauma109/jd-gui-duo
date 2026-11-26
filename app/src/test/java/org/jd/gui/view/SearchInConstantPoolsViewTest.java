/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.view;

import org.jd.gui.api.API;
import org.jd.gui.api.feature.ContainerEntryGettable;
import org.jd.gui.api.feature.TreeNodeExpandable;
import org.jd.gui.api.feature.UriGettable;
import org.jd.gui.api.model.Container;
import org.jd.gui.model.container.DelegatingFilterContainer;
import org.jd.gui.spi.TreeNodeFactory;
import org.jd.gui.view.component.CompiledEntries;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** The search dialog: what can be typed, what is asked for, and the tree of the results. It is not shown. */
class SearchInConstantPoolsViewTest {

    @BeforeAll
    static void needsAScreen() {
        assumeFalse(GraphicsEnvironment.isHeadless());
    }

    /** A node of the results: an entry and, once it is unfolded, its children. */
    private static final class Node extends DefaultMutableTreeNode implements ContainerEntryGettable, UriGettable, TreeNodeExpandable {
        private static final long serialVersionUID = 1L;
        private final transient Container.Entry entry;

        Node(Container.Entry entry) {
            super(entry.getPath());
            this.entry = entry;
        }

        @Override
        public Container.Entry getEntry() { return entry; }

        @Override
        public URI getUri() { return entry.getUri(); }

        @Override
        public void populateTreeNode(API api) {
            if (getChildCount() == 0) {
                entry.getChildren().values().forEach(child -> add(new Node((Container.Entry) child)));
            }
        }
    }

    private final List<String> asked = new ArrayList<>();
    private final List<URI> opened = new ArrayList<>();

    /** The root of the results: the content of the archive that was searched. */
    private Container.Entry results;

    private SearchInConstantPoolsView<Node> view() throws Exception {
        TreeNodeFactory factory = (TreeNodeFactory) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {TreeNodeFactory.class},
                (proxy, method, args) -> "make".equals(method.getName()) ? new Node(results) : null);
        API api = (API) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {API.class},
                (proxy, method, args) -> "getTreeNodeFactory".equals(method.getName()) ? factory : null);
        SearchInConstantPoolsView<Node> view = new SearchInConstantPoolsView<>(api, null, (pattern, flags) -> asked.add(pattern + " " + flags),
                (uri, pattern, flags) -> opened.add(uri));
        SwingUtilities.invokeAndWait(() -> { });
        return view;
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object owner, String name) throws ReflectiveOperationException {
        java.lang.reflect.Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(owner);
    }

    private static KeyEvent key(Component source, int id, int code, char character) {
        return new KeyEvent(source, id, 0, 0, code, character);
    }

    @Test
    void theCharactersThatNoNameHasCannotBeTyped() throws Exception {
        SearchInConstantPoolsView<Node> view = view();
        JTextField text = field(view, "searchInConstantPoolsEnterTextField");

        SwingUtilities.invokeAndWait(() -> {
            for (char refused : "=(){}[]7".toCharArray()) {
                KeyEvent event = key(text, KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, refused);
                for (KeyListener listener : text.getKeyListeners()) {
                    listener.keyTyped(event);
                }
                assertThat(event.isConsumed()).as(String.valueOf(refused)).isTrue();
            }
            text.setText("a");
            for (char accepted : "b7*".toCharArray()) {
                KeyEvent event = key(text, KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, accepted);
                for (KeyListener listener : text.getKeyListeners()) {
                    listener.keyTyped(event);
                }
                assertThat(event.isConsumed()).isFalse();
            }
        });
    }

    @Test
    void everyChangeOfTheTextOrOfAChoiceStartsASearch() throws Exception {
        SearchInConstantPoolsView<Node> view = view();
        JTextField text = field(view, "searchInConstantPoolsEnterTextField");

        SwingUtilities.invokeAndWait(() -> {
            text.setText("name");
            text.setText("");
        });
        int initial = view.getFlags();
        for (String name : List.of("Type", "Constructor", "Method", "Field", "String", "Module", "Declarations", "References")) {
            JCheckBox box = field(view, "searchInConstantPoolsCheckBox" + name);
            SwingUtilities.invokeAndWait(() -> box.setSelected(!box.isSelected()));
        }

        assertThat(asked).startsWith("name " + initial).hasSizeGreaterThanOrEqualTo(10);
        assertThat(view.getFlags()).isEqualTo(SearchInConstantPoolsView.SEARCH_CONSTRUCTOR | SearchInConstantPoolsView.SEARCH_METHOD
                | SearchInConstantPoolsView.SEARCH_FIELD | SearchInConstantPoolsView.SEARCH_STRING | SearchInConstantPoolsView.SEARCH_MODULE);
        assertThat(view.getPattern()).isEmpty();
    }

    @Test
    void theResultsAreUnfoldedToTheFirstOneAndCanBeOpened() throws Exception {
        SearchInConstantPoolsView<Node> view = view();
        JTree tree = field(view, "searchInConstantPoolsTree");
        JTextField text = field(view, "searchInConstantPoolsEnterTextField");
        JLabel label = field(view, "searchInConstantPoolsLabel");
        Container.Entry found = CompiledEntries.archive(Map.of("A.txt", new byte[0], "B.txt", new byte[0]), "A.txt");
        DelegatingFilterContainer container = new DelegatingFilterContainer(found.getContainer(), List.of(found));
        results = container.getRoot();
        SwingUtilities.invokeAndWait(() -> {
            // without results, the arrows stay in the text
            KeyEvent down = key(text, KeyEvent.KEY_PRESSED, KeyEvent.VK_DOWN, KeyEvent.CHAR_UNDEFINED);
            for (KeyListener listener : text.getKeyListeners()) {
                listener.keyPressed(down);
            }
            assertThat(down.isConsumed()).isFalse();
        });

        view.updateTree(List.of(container), 1);
        SwingUtilities.invokeAndWait(() -> { });

        SwingUtilities.invokeAndWait(() -> {
            assertThat(label.getText()).isEqualTo("1 matching entry:");
            Node selected = (Node) tree.getLastSelectedPathComponent();
            // only what matches is shown
            assertThat(selected.getEntry().getPath()).isEqualTo("A.txt");
            assertThat(selected.getParent().getChildCount()).isEqualTo(1);

            tree.dispatchEvent(new MouseEvent(tree, MouseEvent.MOUSE_CLICKED, 0, 0, 5, 5, 2, false, MouseEvent.BUTTON1));
            JDialog dialog;
            try {
                dialog = field(view, "searchInConstantPoolsDialog");
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
            JButton open = dialog.getRootPane().getDefaultButton();
            open.setEnabled(true);
            open.doClick(0);
            assertThat(opened).containsExactly(selected.getUri(), selected.getUri());

            KeyEvent down = key(text, KeyEvent.KEY_PRESSED, KeyEvent.VK_DOWN, KeyEvent.CHAR_UNDEFINED);
            tree.clearSelection();
            for (KeyListener listener : text.getKeyListeners()) {
                listener.keyPressed(down);
            }
            assertThat(down.isConsumed()).isTrue();
            assertThat(tree.getSelectionCount()).isEqualTo(1);
            tree.setSelectionRow(0);
            KeyEvent up = key(tree, KeyEvent.KEY_PRESSED, KeyEvent.VK_UP, KeyEvent.CHAR_UNDEFINED);
            for (KeyListener listener : tree.getKeyListeners()) {
                listener.keyPressed(up);
            }
            assertThat(up.isConsumed()).isTrue();
            tree.collapsePath(new TreePath(((Node) selected.getParent()).getPath()));
            tree.expandPath(new TreePath(((Node) selected.getParent()).getPath()));
        });

        view.updateTree(List.of(), 0);
        SwingUtilities.invokeAndWait(() -> assertThat(label.getText()).isEqualTo("Matching entries:"));
        view.updateTree(null, 3);
        SwingUtilities.invokeAndWait(() -> assertThat(label.getText()).isEqualTo("3 matching entries:"));
        view.showWaitCursor();
        view.hideWaitCursor();
        SwingUtilities.invokeAndWait(() -> { });
        assertThat(view.isVisible()).isFalse();
    }
}
