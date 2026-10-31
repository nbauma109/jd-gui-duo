/*
 * © 2008-2019 Emmanuel Dupuy
 * © 2021-2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.view.component.panel;

import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.api.API;
import org.jd.gui.api.feature.ContainerEntryGettable;
import org.jd.gui.api.feature.PageChangeListener;
import org.jd.gui.api.feature.PageChangeable;
import org.jd.gui.api.feature.PageClosable;
import org.jd.gui.api.feature.PageCreator;
import org.jd.gui.api.feature.PreferencesChangeListener;
import org.jd.gui.api.feature.TreeNodeExpandable;
import org.jd.gui.api.feature.UriGettable;
import org.jd.gui.api.feature.UriOpenable;
import org.jd.gui.api.model.TreeNodeData;
import org.jd.gui.model.debug.DebugThread;
import org.jd.gui.view.component.Tree;
import org.jd.gui.view.renderer.TreeNodeRenderer;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelListener;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;


import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import static org.jd.gui.util.decompiler.GuiPreferences.TREE_NODE_FONT_SIZE_KEY;

public class TreeTabbedPanel<T extends DefaultMutableTreeNode & ContainerEntryGettable & UriGettable> extends JPanel implements UriGettable, UriOpenable, PageChangeable, PageClosable, PreferencesChangeListener {

    private static final long serialVersionUID = 1L;
    private static final int MIN_TREE_NODE_FONT_SIZE = 2;
    private static final int MAX_TREE_NODE_FONT_SIZE = 40;

    private static final DefaultMutableTreeNode DEBUG_TREE_ROOT = new DefaultMutableTreeNode("Debug");
    private static final DefaultTreeModel DEBUG_TREE_MODEL = new DefaultTreeModel(DEBUG_TREE_ROOT);
    private static final java.util.WeakHashMap<TreeTabbedPanel<?>, Void> INSTANCES = new java.util.WeakHashMap<>();
    /** Set by MainController so clicking a frame navigates to that class/line. */
    private static java.util.function.BiConsumer<String, Integer> debugNavigateHandler;
    /** Set by MainController: (thread id, frame index) of the frame the user selected. */
    private static java.util.function.BiConsumer<String, Integer> debugFrameSelectHandler;
    /** True while setDebugFrames updates the tree, so programmatic selection changes are not user clicks. */
    private static boolean debugTreeAdjusting;

    protected final transient API api;
    private final URI uri;
    protected final Tree tree;
    private final JTabbedPane leftTabs;
    @SuppressWarnings("rawtypes")
    private final TabbedPanel tabbedPanel;
    private final transient List<PageChangeListener> pageChangedListeners = new ArrayList<>();
    // Flags to prevent the event cascades
    private boolean updateTreeMenuEnabled = true;
    private boolean openUriEnabled = true;
    private boolean treeNodeChangedEnabled = true;
    private transient Map<String, String> preferences;

    @SuppressWarnings("unchecked")
    public TreeTabbedPanel(API api, URI uri) {
        this.api = api;
        this.uri = uri;
        this.preferences = api.getPreferences();

        tree = new Tree();
        tree.setShowsRootHandles(true);
        tree.setMinimumSize(new Dimension(150, 10));
        tree.setExpandsSelectedPaths(true);
        tree.setCellRenderer(new TreeNodeRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
                // Always render the left tree with focus
                return super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, true);
            }
        });
        tree.addTreeSelectionListener(e -> treeNodeChanged((T)tree.getLastSelectedPathComponent()));
        tree.addTreeExpansionListener(new TreeExpansionListener() {
            @Override
            public void treeExpanded(TreeExpansionEvent e) {
                TreeNode node = (TreeNode)e.getPath().getLastPathComponent();
                if (node instanceof TreeNodeExpandable tne) {
                    int oldHashCode = createHashCode(node.children());
                    tne.populateTreeNode(api);
                    int newHashCode = createHashCode(node.children());
                    if (oldHashCode != newHashCode) {
                        ((DefaultTreeModel)tree.getModel()).reload(node);
                    }
                }
            }
            @Override
            public void treeCollapsed(TreeExpansionEvent e) {}
        });
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    TreePath path = tree.getPathForLocation(e.getX(), e.getY());

                    if (path != null) {
                        tree.setSelectionPath(path);

                        T node = (T)path.getLastPathComponent();
                        Collection<Action> actions = api.getContextualActions(node.getEntry(), node.getUri().getFragment());

                        if (actions != null) {
                            JPopupMenu popup = new JPopupMenu();
                            for (Action action : actions) {
                                if (action != null) {
                                    popup.add(action);
                                } else {
                                    popup.addSeparator();
                                }
                            }
                            popup.show(e.getComponent(), e.getX(), e.getY());
                        }
                    }
                }
            }
        });

        tabbedPanel = new TabbedPanel<>(api);
        tabbedPanel.setMinimumSize(new Dimension(150, 10));
        tabbedPanel.tabbedPane.addChangeListener(e -> pageChanged());

        setLayout(new BorderLayout());

        JScrollPane treeScrollPane = new JScrollPane(tree);
        final MouseWheelListener[] mouseWheelListeners = treeScrollPane.getMouseWheelListeners();
        for (MouseWheelListener listener : mouseWheelListeners) {
            treeScrollPane.removeMouseWheelListener(listener);
        }
        treeScrollPane.addMouseWheelListener(e -> {
            if ((e.getModifiersEx() & (InputEvent.META_DOWN_MASK | InputEvent.CTRL_DOWN_MASK)) != 0) {
                if (e.getWheelRotation() > 0) {
                    updateTreeNodeFontSize(-1);
                } else {
                    updateTreeNodeFontSize(1);
                }
            } else {
                for (MouseWheelListener listener : mouseWheelListeners) {
                    listener.mouseWheelMoved(e);
                }
            }
        });

        applyTreeNodeFontSize(preferences);

        leftTabs = new JTabbedPane(javax.swing.SwingConstants.BOTTOM);
        javax.swing.Icon prjIcon = loadIcon("/org/jd/gui/images/filenav_nav.png");
        javax.swing.Icon dbgIcon = loadIcon("/org/jd/gui/images/bug_exc.png");
        leftTabs.addTab("Project Explorer", prjIcon, treeScrollPane);
        JTree debugTree = new JTree(DEBUG_TREE_MODEL);
        debugTree.setRootVisible(false);
        debugTree.setShowsRootHandles(true);
        debugTree.setCellRenderer(new DebugTreeRenderer());
        debugTree.setFont(debugTree.getFont().deriveFont(java.awt.Font.PLAIN, 11f));
        debugTree.addTreeSelectionListener(e -> {
            TreePath path = debugTree.getSelectionPath();
            if (path == null || debugTreeAdjusting) {
                return;
            }
            Object last = path.getLastPathComponent();
            if (debugFrameSelectHandler != null && last instanceof DefaultMutableTreeNode selected) {
                if (selected.getUserObject() instanceof DebugThread dt && dt.isSuspended()) {
                    debugFrameSelectHandler.accept(Long.toString(dt.id()), 0);
                    return;
                }
                if (selected.getUserObject() instanceof String && selected.getParent() instanceof DefaultMutableTreeNode parent
                        && parent.getUserObject() instanceof DebugThread dt) {
                    debugFrameSelectHandler.accept(Long.toString(dt.id()), parent.getIndex(selected));
                    return;
                }
            }
            if (debugNavigateHandler == null) {
                return;
            }
            if (last instanceof DefaultMutableTreeNode node && node.getUserObject() instanceof String frame) {
                // Frame format: "com.example.Foo.bar() line: 42"
                int lineMarker = frame.lastIndexOf(" line: ");
                if (lineMarker >= 0) {
                    try {
                        int lineNum = Integer.parseInt(frame.substring(lineMarker + 7).trim());
                        String methodPart = frame.substring(0, lineMarker); // "com.example.Foo.bar()"
                        int parenIdx = methodPart.lastIndexOf('(');
                        if (parenIdx > 0) {
                            String classAndMethod = methodPart.substring(0, parenIdx); // "com.example.Foo.bar"
                            int lastDot = classAndMethod.lastIndexOf('.');
                            if (lastDot > 0) {
                                String className = classAndMethod.substring(0, lastDot); // "com.example.Foo"
                                debugNavigateHandler.accept(className, lineNum);
                            }
                        }
                    } catch (NumberFormatException _) {
                        // ignore malformed frame string
                    }
                }
            }
        });
        leftTabs.addTab("Debug", dbgIcon, new JScrollPane(debugTree));

        JSplitPane splitter = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftTabs, tabbedPanel);
        splitter.setResizeWeight(0.2);

        add(splitter, BorderLayout.CENTER);
        INSTANCES.put(this, null);
    }

    private static javax.swing.Icon loadIcon(String path) {
        java.net.URL url = TreeTabbedPanel.class.getResource(path);
        return url == null ? null : new ImageIcon(url);
    }

    public static void setDebugFrames(List<DebugThread> threads) {
        debugTreeAdjusting = true;
        try {
            applyDebugFrames(threads);
        } finally {
            debugTreeAdjusting = false;
        }
    }

    private static void applyDebugFrames(List<DebugThread> threads) {
        // Smart update: avoid a full reload() which collapses the entire tree (causes blink).
        // If the set of threads (by name, in order) is unchanged, update each node individually:
        //   - nodeChanged()          for label-only changes  (no collapse)
        //   - nodeStructureChanged() for frame-list changes  (collapses only that thread node)
        // Otherwise fall back to a full rebuild.
        if (hasSameThreads(threads)) {
            // Incremental update per thread node
            for (int i = 0; i < threads.size(); i++) {
                updateThreadNode((DefaultMutableTreeNode) DEBUG_TREE_ROOT.getChildAt(i), threads.get(i));
            }
        } else {
            // Full rebuild (first connect, disconnect, or thread set changed)
            DEBUG_TREE_ROOT.removeAllChildren();
            for (DebugThread dt : threads) {
                DefaultMutableTreeNode threadNode = new DefaultMutableTreeNode(dt);
                addFrames(threadNode, dt);
                DEBUG_TREE_ROOT.add(threadNode);
            }
            DEBUG_TREE_MODEL.reload(DEBUG_TREE_ROOT);
        }

        if (!threads.isEmpty()) {
            INSTANCES.keySet().forEach(TreeTabbedPanel::expandSuspendedThreads);
        }
    }

    private static boolean hasSameThreads(List<DebugThread> threads) {
        int existingCount = DEBUG_TREE_ROOT.getChildCount();
        if (existingCount != threads.size()) {
            return false;
        }
        for (int i = 0; i < existingCount; i++) {
            DefaultMutableTreeNode n = (DefaultMutableTreeNode) DEBUG_TREE_ROOT.getChildAt(i);
            if (!(n.getUserObject() instanceof DebugThread existing) || existing.id() != threads.get(i).id()) {
                return false;
            }
        }
        return true;
    }

    private static void updateThreadNode(DefaultMutableTreeNode threadNode, DebugThread newDt) {
        DebugThread oldDt = (DebugThread) threadNode.getUserObject();
        if (oldDt.equals(newDt)) {
            return; // Nothing changed for this thread
        }
        threadNode.setUserObject(newDt);
        if (oldDt.isSuspended() != newDt.isSuspended() || !oldDt.frames().equals(newDt.frames())) {
            threadNode.removeAllChildren();
            addFrames(threadNode, newDt);
            DEBUG_TREE_MODEL.nodeStructureChanged(threadNode);
        } else {
            DEBUG_TREE_MODEL.nodeChanged(threadNode);
        }
    }

    private static void addFrames(DefaultMutableTreeNode threadNode, DebugThread dt) {
        if (dt.isSuspended()) {
            for (String frame : dt.frames()) {
                threadNode.add(new DefaultMutableTreeNode(frame));
            }
        }
    }

    /** Re-expands the suspended thread nodes of one panel. */
    private static void expandSuspendedThreads(TreeTabbedPanel panel) {
        panel.leftTabs.setSelectedIndex(1);
        JTree debugTree = (JTree) ((JScrollPane) panel.leftTabs.getComponentAt(1)).getViewport().getView();
        for (int i = 0; i < DEBUG_TREE_ROOT.getChildCount(); i++) {
            DefaultMutableTreeNode threadNode = (DefaultMutableTreeNode) DEBUG_TREE_ROOT.getChildAt(i);
            if (threadNode.getUserObject() instanceof DebugThread dt && dt.isSuspended()) {
                debugTree.expandPath(new TreePath(threadNode.getPath()));
            }
        }
    }

    /** Called by MainController: the user selected a stack frame of a stopped thread. */
    public static void setDebugFrameSelectHandler(java.util.function.BiConsumer<String, Integer> handler) {
        debugFrameSelectHandler = handler;
    }

    /** Called by MainController to wire up frame-click → navigate-to-source. */
    public static void setDebugNavigateHandler(java.util.function.BiConsumer<String, Integer> handler) {
        debugNavigateHandler = handler;
    }

    protected void updateTreeNodeFontSize(int delta) {
        int currentFontSize = tree.getFont().getSize();
        int newFontSize = Math.max(MIN_TREE_NODE_FONT_SIZE, Math.min(MAX_TREE_NODE_FONT_SIZE, currentFontSize + delta));

        if (newFontSize != currentFontSize) {
            setTreeNodeFontSize(newFontSize);
            if (preferences != null) {
                preferences.put(TREE_NODE_FONT_SIZE_KEY, String.valueOf(newFontSize));
            }
        }
    }

    protected void setTreeNodeFontSize(int fontSize) {
        tree.setFont(tree.getFont().deriveFont((float)fontSize));
        tree.setRowHeight(0);
        tree.fireVisibleDataPropertyChange();
        tree.revalidate();
        tree.repaint();
    }

    protected void applyTreeNodeFontSize(Map<String, String> preferences) {
        String fontSize = preferences.get(TREE_NODE_FONT_SIZE_KEY);
        if (fontSize != null) {
            try {
                int parsed = Integer.parseInt(fontSize);
                int bounded = Math.max(MIN_TREE_NODE_FONT_SIZE, Math.min(MAX_TREE_NODE_FONT_SIZE, parsed));
                setTreeNodeFontSize(bounded);
            } catch (NumberFormatException e) {
                assert ExceptionUtil.printStackTrace(e);
            }
        }
        this.preferences = preferences;
    }

    protected static int createHashCode(@SuppressWarnings("all") Enumeration enumeration) {
        int hashCode = 1;

        while (enumeration.hasMoreElements()) {
            hashCode *= 31;

            Object element = enumeration.nextElement();

            if (element != null) {
                hashCode += element.hashCode();
            }
        }

        return hashCode;
    }

    @SuppressWarnings("unchecked")
    protected void treeNodeChanged(T node) {
        if (treeNodeChangedEnabled && node != null) {
            try {
                // Disable tabbedPane.changeListener
                updateTreeMenuEnabled = false;

                // Search base tree node
                URI localURI = node.getUri();

                if (localURI.getFragment() == null && localURI.getQuery() == null) {
                    showPage(localURI, localURI, node);
                } else {
                    URI baseUri = new URI(localURI.getScheme(), localURI.getHost(), localURI.getPath(), null);
                    T baseNode = node;

                    while (baseNode != null && !baseNode.getUri().equals(baseUri)) {
                        baseNode = (T)baseNode.getParent();
                    }

                    if (baseNode != null && baseNode.getUri().equals(baseUri)) {
                        showPage(localURI, baseUri, baseNode);
                    }
                }
            } catch (URISyntaxException e) {
                assert ExceptionUtil.printStackTrace(e);
            } finally {
                // Enable tabbedPane.changeListener
                updateTreeMenuEnabled = true;
            }
        }
    }

    @SuppressWarnings("unchecked")
    protected <P extends JComponent & UriGettable> boolean showPage(URI uri, URI baseUri, DefaultMutableTreeNode baseNode) {
        P page = (P)tabbedPanel.showPage(baseUri);

        if (page == null && baseNode instanceof PageCreator pc) {
            page = pc.createPage(api);
            page.putClientProperty("node", baseNode);

            String path = baseUri.getPath();
            String label = path.substring(path.lastIndexOf('/')+1);
            Object data = baseNode.getUserObject();

            if (data instanceof TreeNodeData tnd) {
                tabbedPanel.addPage(label, tnd::getIcon, tnd.getTip(), page);
            } else {
                tabbedPanel.addPage(label, null, null, page);
            }
        }

        if (openUriEnabled && page instanceof UriOpenable uo) {
            uo.openUri(uri);
        }

        return page != null;
    }

    @SuppressWarnings("unchecked")
    protected <P extends JComponent & UriGettable> void pageChanged() {
        try {
            // Disable highlight
            openUriEnabled = false;

            P page = (P)tabbedPanel.tabbedPane.getSelectedComponent();

            if (updateTreeMenuEnabled) {
                // Synchronize tree
                if (page != null) {
                    T node = (T)page.getClientProperty("node");
                    // Select tree node
                    TreePath treePath = new TreePath(node.getPath());
                    tree.setSelectionPath(treePath);
                    tree.scrollPathToVisible(treePath);
                } else {
                    tree.clearSelection();
                }
            }
            // Fire page changed event
            for (PageChangeListener listener : pageChangedListeners) {
                listener.pageChanged(page);
            }
        } finally {
            // Enable highlight
            openUriEnabled = true;
        }
    }

    // --- URIGetter --- //
    @Override
    public URI getUri() { return uri; }

    // --- URIOpener --- //
    @Override
    public boolean openUri(URI uri) {
        try {
            URI baseUri = new URI(uri.getScheme(), uri.getHost(), uri.getPath(), null);

            if (this.uri.equals(baseUri)) {
                return true;
            }
            DefaultMutableTreeNode node = searchTreeNode(baseUri, (DefaultMutableTreeNode) tree.getModel().getRoot());

            if (showPage(uri, baseUri, node)) {
                DefaultMutableTreeNode childNode = searchTreeNode(uri, node);
                if (childNode != null) {
                    node = childNode;
                }
            }

            if (node != null) {
                try {
                    // Disable tree node changed listener
                    treeNodeChangedEnabled = false;
                    // Populate and expand node
                    if (!(node instanceof PageCreator) && node instanceof TreeNodeExpandable) {
                        ((TreeNodeExpandable) node).populateTreeNode(api);
                        tree.expandPath(new TreePath(node.getPath()));
                    }
                    // Select tree node
                    TreePath treePath = new TreePath(node.getPath());
                    tree.setSelectionPath(treePath);
                    tree.scrollPathToVisible(treePath);
                } finally {
                    // Enable tree node changed listener
                    treeNodeChangedEnabled = true;
                }
                return true;
            }
        } catch (URISyntaxException e) {
            assert ExceptionUtil.printStackTrace(e);
        }

        return false;
    }

    @SuppressWarnings("unchecked")
    protected DefaultMutableTreeNode searchTreeNode(URI uri, DefaultMutableTreeNode node) {
        if (node instanceof TreeNodeExpandable tne) {
            tne.populateTreeNode(api);
        }

        String u = uri.toString();
        T child = null;
        @SuppressWarnings("all")
        Enumeration enumeration = node.children();

        while (enumeration.hasMoreElements()) {
            T element = (T)enumeration.nextElement();
            String childU = element.getUri().toString();

            if (u.length() > childU.length()) {
                if (u.startsWith(childU)) {
                    char c = u.charAt(childU.length());
                    if (c == '/' || c == '!') {
                        child = element;
                        break;
                    }
                }
            } else if (u.equals(childU)) {
                child = element;
                break;
            }
        }

        if (child != null) {
            if (u.equals(child.getUri().toString())) {
                return child;
            }
            // Parent tree node found -> Recursive call
            return searchTreeNode(uri, child);
        }
        // Not found
        return null;
    }

    // --- PageChanger --- //
    @Override
    public void addPageChangeListener(PageChangeListener listener) {
        pageChangedListeners.add(listener);
    }

    // --- PageCloser --- //
    @Override
    public boolean closePage() {
        Component component = tabbedPanel.tabbedPane.getSelectedComponent();

        if (component != null) {
            tabbedPanel.removeComponent(component);
            return true;
        }
        return false;
    }

    // --- PreferencesChangeListener --- //
    @Override
    @SuppressWarnings("unchecked")
    public void preferencesChanged(Map<String, String> preferences) {
        applyTreeNodeFontSize(preferences);
        tabbedPanel.preferencesChanged(preferences);
    }

    /**
     * Renders the Debug JTree:
     *  • Thread node  → thread icon (event thread = yellow/suspended icon, others = plain thread icon)
     *  • Frame node 0 → stckframe_obj (top / suspended frame)
     *  • Frame node N → stckframe_running_obj (running frame below top)
     */
    private static class DebugTreeRenderer extends DefaultTreeCellRenderer {
        private static final long serialVersionUID = 1L;
        private static final javax.swing.Icon THREAD_EVENT_ICON   = loadIcon("/org/jd/gui/images/debugts_obj.png");
        private static final javax.swing.Icon THREAD_OTHER_ICON   = loadIcon("/org/jd/gui/images/thread_obj.png");
        private static final javax.swing.Icon FRAME_TOP_ICON      = loadIcon("/org/jd/gui/images/stckframe_obj.png");
        private static final javax.swing.Icon FRAME_RUNNING_ICON  = loadIcon("/org/jd/gui/images/stckframe_running_obj.png");

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
                boolean expanded, boolean leaf, int row, boolean hasFocus) {
            JLabel label = (JLabel) super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            if (value instanceof DefaultMutableTreeNode node) {
                Object userObj = node.getUserObject();
                if (userObj instanceof DebugThread dt) {
                    label.setText("Thread \"" + dt.name() + "\"" + statusOf(dt));
                    label.setIcon(dt.isEventThread() ? THREAD_EVENT_ICON : THREAD_OTHER_ICON);
                } else if (userObj instanceof String frame) {
                    // Frame node — determine if it is the top frame of its parent
                    DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();
                    boolean isTop = parent != null && parent.getIndex(node) == 0;
                    label.setText(frame);
                    label.setIcon(isTop ? FRAME_TOP_ICON : FRAME_RUNNING_ICON);
                }
            }
            return label;
        }

        private static String statusOf(DebugThread dt) {
            if (!dt.isSuspended()) {
                return " (Running)";
            }
            return dt.isEventThread() ? " (Suspended, at breakpoint)" : " (Suspended)";
        }
    }
}
