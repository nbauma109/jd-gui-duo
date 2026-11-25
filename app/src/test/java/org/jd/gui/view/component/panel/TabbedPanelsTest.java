/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.view.component.panel;

import org.jd.gui.api.API;
import org.jd.gui.api.feature.ContainerEntryGettable;
import org.jd.gui.api.feature.PageCreator;
import org.jd.gui.api.feature.TreeNodeExpandable;
import org.jd.gui.api.feature.UriGettable;
import org.jd.gui.api.feature.UriOpenable;
import org.jd.gui.api.model.Container;
import org.jd.gui.api.model.TreeNodeData;
import org.jd.gui.model.debug.DebugThread;
import org.jd.gui.util.decompiler.GuiPreferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.Component;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.io.Closeable;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import static org.assertj.core.api.Assertions.assertThat;

/** The tabs of the opened files, and the panel of an archive: its tree, its tabs and the threads of a debugged program. */
class TabbedPanelsTest {

    private static final String SINGLE_LINE_TABS = "UITabsPreferencesProvider.singleLineTabs";

    private final Map<String, String> preferences = new HashMap<>();

    @AfterEach
    void clearTheDebugger() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            TreeTabbedPanel.setDebugFrames(List.of());
            TreeTabbedPanel.setDebugFrameSelectHandler(null);
            TreeTabbedPanel.setDebugNavigateHandler(null);
        });
    }

    private API api() {
        return (API) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {API.class},
                (proxy, method, args) -> "getPreferences".equals(method.getName()) ? preferences : null);
    }

    private static final class Page extends JPanel implements UriGettable, UriOpenable, Closeable {
        private static final long serialVersionUID = 1L;
        private final transient URI uri;
        private final transient List<URI> opened = new ArrayList<>();
        private boolean closed;

        Page(String path) {
            uri = URI.create("file:" + path);
        }

        @Override
        public URI getUri() { return uri; }

        @Override
        public boolean openUri(URI target) {
            opened.add(target);
            return true;
        }

        @Override
        public void close() { closed = true; }
    }

    private static List<String> titles(JTabbedPane tabs) {
        List<String> titles = new ArrayList<>();
        for (int i = 0; i < tabs.getTabCount(); i++) {
            titles.add(tabs.getTitleAt(i));
        }
        return titles;
    }

    private static TabbedPanel<Page> panelWith(API api, String... names) {
        TabbedPanel<Page> panel = new TabbedPanel<>(api);
        for (String name : names) {
            panel.addPage(name, () -> null, "tip of " + name, new Page("/" + name));
        }
        return panel;
    }

    @Test
    void aPageIsShownInANewTabThatIsFoundAgainByItsUri() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            TabbedPanel<Page> panel = panelWith(api(), "a", "b");
            JTabbedPane tabs = panel.getTabbedPane();

            assertThat(titles(tabs)).containsExactly("a", "b");
            assertThat(tabs.getSelectedIndex()).isEqualTo(1);
            Page found = panel.showPage(URI.create("file:/a"));
            assertThat(found).isSameAs(tabs.getComponentAt(0));
            assertThat(tabs.getSelectedIndex()).isZero();
            assertThat(panel.showPage(URI.create("file:/other"))).isNull();
            assertThat(((JComponent) tabs.getTabComponentAt(0)).getToolTipText()).isEqualTo("tip of a");
        });
    }

    @Test
    void theCrossOfATabClosesItsPage() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            TabbedPanel<Page> panel = panelWith(api(), "a");
            Page page = (Page) panel.getTabbedPane().getComponentAt(0);
            JPanel tab = (JPanel) panel.getTabbedPane().getTabComponentAt(0);
            JLabel cross = (JLabel) tab.getComponent(1);
            Icon idle = cross.getIcon();

            mouse(cross, MouseEvent.MOUSE_ENTERED);
            assertThat(cross.getIcon()).isNotSameAs(idle);
            mouse(cross, MouseEvent.MOUSE_PRESSED);
            mouse(cross, MouseEvent.MOUSE_RELEASED);
            mouse(cross, MouseEvent.MOUSE_EXITED);
            assertThat(cross.getIcon()).isSameAs(idle);
            mouse(cross, MouseEvent.MOUSE_CLICKED);

            assertThat(panel.getTabbedPane().getTabCount()).isZero();
            assertThat(page.closed).isTrue();
        });
    }

    private static void mouse(JComponent component, int id) {
        component.dispatchEvent(new MouseEvent(component, id, 0, 0, 1, 1, 1, false, MouseEvent.BUTTON1));
    }

    private static JMenuItem item(javax.swing.JPopupMenu menu, String text) {
        for (Component component : menu.getComponents()) {
            if (component instanceof JMenuItem menuItem && text.equals(menuItem.getText())) {
                return menuItem;
            }
        }
        throw new AssertionError(text);
    }

    @Test
    void theMenuOfATabClosesItTheOthersOrAll() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            TabbedPanel<Page> panel = panelWith(api(), "a", "b", "c", "d");
            panel.preferencesChanged(Map.of());
            JTabbedPane tabs = panel.getTabbedPane();

            item(panel.new PopupTabMenu(tabs.getComponentAt(0)), "Close").doClick(0);
            assertThat(titles(tabs)).containsExactly("b", "c", "d");
            item(panel.new PopupTabMenu(tabs.getComponentAt(1)), "Close Others").doClick(0);
            assertThat(titles(tabs)).containsExactly("c");
            item(panel.new PopupTabMenu(tabs.getComponentAt(0)), "Close All").doClick(0);
            assertThat(tabs.getTabCount()).isZero();
        });
    }

    @Test
    void tabsOnOneLineCanBeSelectedFromTheMenu() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            TabbedPanel<Page> panel = panelWith(api(), "a", "b", "c");
            panel.preferencesChanged(Map.of(SINGLE_LINE_TABS, "true"));
            JTabbedPane tabs = panel.getTabbedPane();
            assertThat(tabs.getTabLayoutPolicy()).isEqualTo(JTabbedPane.SCROLL_TAB_LAYOUT);

            JMenu select = (JMenu) item(panel.new PopupTabMenu(tabs.getComponentAt(1)), "Select Tab");

            assertThat(select.getItemCount()).isEqualTo(3);
            assertThat(select.getItem(1).getFont().isBold()).isTrue();
            select.getItem(0).doClick(0);
            assertThat(tabs.getSelectedIndex()).isZero();
            panel.preferencesChanged(Map.of(SINGLE_LINE_TABS, "false"));
            assertThat(tabs.getTabLayoutPolicy()).isEqualTo(JTabbedPane.WRAP_TAB_LAYOUT);
        });
    }

    /** A node of the tree of an archive; those with a name that has a dot open a page. */
    private static class Node extends DefaultMutableTreeNode implements ContainerEntryGettable, UriGettable, TreeNodeExpandable {
        private static final long serialVersionUID = 1L;
        final transient URI uri;
        int populated;

        Node(String uri) {
            this.uri = URI.create(uri);
            setUserObject(uri.substring(uri.lastIndexOf('/') + 1));
        }

        @Override
        public URI getUri() { return uri; }

        @Override
        public Container.Entry getEntry() { return null; }

        @Override
        public void populateTreeNode(API api) { populated++; }
    }

    private static final class FileNode extends Node implements PageCreator {
        private static final long serialVersionUID = 1L;

        FileNode(String uri) {
            super(uri);
            setUserObject(new TreeNodeData() {
                public String getLabel() { return uri; }
                public String getTip() { return "tip"; }
                public Icon getIcon() { return null; }
                public Icon getOpenIcon() { return null; }
            });
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T extends JComponent & UriGettable> T createPage(API api) {
            return (T) new Page(uri.getPath());
        }
    }

    private static final class Archive {
        final Node directory = new Node("file:/x.jar!/demo");
        final FileNode first = new FileNode("file:/x.jar!/demo/First.class");
        final FileNode second = new FileNode("file:/x.jar!/demo/Second.class");
        final TreeTabbedPanel<Node> panel;

        Archive(API api) {
            panel = new TreeTabbedPanel<>(api, URI.create("file:/x.jar"));
            Node root = new Node("file:/x.jar");
            directory.add(first);
            directory.add(second);
            root.add(directory);
            panel.tree.setModel(new DefaultTreeModel(root));
        }

        JTabbedPane leftTabs() {
            return (JTabbedPane) ((javax.swing.JSplitPane) panel.getComponent(0)).getLeftComponent();
        }

        JTabbedPane pages() {
            return ((TabbedPanel<?>) ((javax.swing.JSplitPane) panel.getComponent(0)).getRightComponent()).getTabbedPane();
        }
    }

    @Test
    void openingTheUriOfAFileShowsItsPageAndSelectsItsNode() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Archive archive = new Archive(api());
            List<Object> changes = new ArrayList<>();
            archive.panel.addPageChangeListener(changes::add);
            URI uri = URI.create("file:/x.jar!/demo/First.class");

            assertThat(archive.panel.openUri(uri)).isTrue();

            Page page = (Page) archive.pages().getSelectedComponent();
            assertThat(page.getUri().getPath()).isEqualTo(uri.getPath());
            assertThat(page.opened).contains(uri);
            assertThat(archive.panel.tree.getLastSelectedPathComponent()).isSameAs(archive.first);
            assertThat(changes).contains(page);
            // the archive itself, and what it does not hold
            assertThat(archive.panel.openUri(URI.create("file:/x.jar"))).isTrue();
            assertThat(archive.panel.openUri(URI.create("file:/x.jar!/demo/Missing.class"))).isFalse();
            assertThat(archive.panel.openUri(URI.create("file:/x.jar!/demo"))).isTrue();
            assertThat(archive.panel.tree.getLastSelectedPathComponent()).isSameAs(archive.directory);
            assertThat(archive.panel.getUri()).isEqualTo(URI.create("file:/x.jar"));
        });
    }

    @Test
    void selectingANodeShowsItsPageAndSelectingATabItsNode() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Archive archive = new Archive(api());
            JTree tree = archive.panel.tree;

            tree.setSelectionPath(new TreePath(archive.first.getPath()));
            tree.setSelectionPath(new TreePath(archive.second.getPath()));
            assertThat(titles(archive.pages())).containsExactly("First.class", "Second.class");

            archive.pages().setSelectedIndex(0);
            assertThat(tree.getLastSelectedPathComponent()).isSameAs(archive.first);
            assertThat(archive.panel.closePage()).isTrue();
            assertThat(archive.panel.closePage()).isTrue();
            assertThat(archive.panel.closePage()).isFalse();
            assertThat(tree.getSelectionPath()).isNull();
        });
    }

    @Test
    void aNodeThatIsExpandedIsFilled() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Archive archive = new Archive(api());

            archive.panel.tree.expandPath(new TreePath(archive.directory.getPath()));

            assertThat(archive.directory.populated).isPositive();
        });
    }

    @Test
    void theSizeOfTheFontOfTheTreeFollowsThePreferenceAndTheWheel() throws Exception {
        preferences.put(GuiPreferences.TREE_NODE_FONT_SIZE_KEY, "20");

        SwingUtilities.invokeAndWait(() -> {
            Archive archive = new Archive(api());
            JScrollPane scrollPane = (JScrollPane) archive.leftTabs().getComponentAt(0);
            assertThat(archive.panel.tree.getFont().getSize()).isEqualTo(20);

            wheel(scrollPane, InputEvent.CTRL_DOWN_MASK, -1);
            assertThat(preferences).containsEntry(GuiPreferences.TREE_NODE_FONT_SIZE_KEY, "21");
            wheel(scrollPane, InputEvent.CTRL_DOWN_MASK, 1);
            wheel(scrollPane, 0, 1);
            assertThat(archive.panel.tree.getFont().getSize()).isEqualTo(20);
            archive.panel.preferencesChanged(Map.of(GuiPreferences.TREE_NODE_FONT_SIZE_KEY, "1000"));
            assertThat(archive.panel.tree.getFont().getSize()).isEqualTo(40);
            archive.panel.preferencesChanged(Map.of(GuiPreferences.TREE_NODE_FONT_SIZE_KEY, "large"));
            assertThat(archive.panel.tree.getFont().getSize()).isEqualTo(40);
        });
    }

    private static void wheel(Component component, int modifiers, int rotation) {
        component.dispatchEvent(new MouseWheelEvent(component, MouseEvent.MOUSE_WHEEL, 0, modifiers, 5, 5, 0, false,
                MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, rotation));
    }

    private static JTree debugTree(Archive archive) {
        return (JTree) ((JScrollPane) archive.leftTabs().getComponentAt(1)).getViewport().getView();
    }

    private static List<String> rows(JTree tree) {
        List<String> rows = new ArrayList<>();
        for (int row = 0; row < tree.getRowCount(); row++) {
            TreePath path = tree.getPathForRow(row);
            JLabel label = (JLabel) tree.getCellRenderer().getTreeCellRendererComponent(tree, path.getLastPathComponent(), false,
                    tree.isExpanded(row), false, row, false);
            rows.add(label.getText());
        }
        return rows;
    }

    @Test
    void theThreadsOfTheDebuggedProgramAreShownWithTheFramesOfTheSuspendedOnes() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Archive archive = new Archive(api());
            JTree tree = debugTree(archive);
            List<String> frames = List.of("demo.First.run() line: 3", "demo.First.main(String[]) line: 9");

            TreeTabbedPanel.setDebugFrames(List.of(new DebugThread("main", 1, true, true, frames), new DebugThread("worker", 2, false, false, List.of())));
            assertThat(rows(tree)).containsExactly("Thread \"main\" (Suspended, at breakpoint)", frames.get(0), frames.get(1),
                    "Thread \"worker\" (Running)");
            assertThat(archive.leftTabs().getSelectedIndex()).isEqualTo(1);

            // the same threads: each one is updated in place
            TreeTabbedPanel.setDebugFrames(List.of(new DebugThread("main", 1, false, false, List.of()), new DebugThread("worker", 2, false, true, frames)));
            assertThat(rows(tree)).containsExactly("Thread \"main\" (Running)", "Thread \"worker\" (Suspended)", frames.get(0), frames.get(1));
            TreeTabbedPanel.setDebugFrames(List.of(new DebugThread("renamed", 1, false, false, List.of()), new DebugThread("worker", 2, false, true, frames)));
            assertThat(rows(tree)).startsWith("Thread \"renamed\" (Running)");
            TreeTabbedPanel.setDebugFrames(List.of(new DebugThread("other", 3, false, false, List.of())));
            assertThat(rows(tree)).containsExactly("Thread \"other\" (Running)");
        });
    }

    @Test
    void selectingAThreadOrAFrameTellsTheDebugger() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Archive archive = new Archive(api());
            JTree tree = debugTree(archive);
            List<String> selected = new ArrayList<>();
            TreeTabbedPanel.setDebugFrameSelectHandler((thread, frame) -> selected.add(thread + "/" + frame));
            TreeTabbedPanel.setDebugFrames(List.of(new DebugThread("main", 7, true, true, List.of("a.B.c() line: 1", "a.B.d() line: 2"))));
            assertThat(selected).isEmpty();

            tree.setSelectionRow(0);
            tree.setSelectionRow(2);

            assertThat(selected).containsExactly("7/0", "7/1");
        });
    }

    @Test
    void withoutAThreadHandlerSelectingAFrameGoesToItsLine() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Archive archive = new Archive(api());
            JTree tree = debugTree(archive);
            List<String> visited = new ArrayList<>();
            TreeTabbedPanel.setDebugFrames(List.of(new DebugThread("main", 7, true, true,
                    List.of("demo.First.run() line: 3", "demo.First.run() line: many", "no line", "run() line: 4"))));
            tree.setSelectionRow(1); // nobody listens yet
            TreeTabbedPanel.setDebugNavigateHandler((type, line) -> visited.add(type + ":" + line));

            for (int row : new int[] {0, 2, 3, 4, 1}) {
                tree.setSelectionRow(row);
            }
            tree.clearSelection();

            assertThat(visited).containsExactly("demo.First:3");
        });
    }
}
