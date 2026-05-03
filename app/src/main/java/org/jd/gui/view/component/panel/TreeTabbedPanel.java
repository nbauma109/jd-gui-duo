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
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import static org.jd.gui.util.decompiler.GuiPreferences.TREE_NODE_FONT_SIZE_KEY;

public class TreeTabbedPanel<T extends DefaultMutableTreeNode & ContainerEntryGettable & UriGettable> extends JPanel implements UriGettable, UriOpenable, PageChangeable, PageClosable, PreferencesChangeListener {

    private static final long serialVersionUID = 1L;
    private static final int MIN_TREE_NODE_FONT_SIZE = 2;
    private static final int MAX_TREE_NODE_FONT_SIZE = 40;
    protected final transient API api;
    private final URI uri;
    protected final Tree tree;
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

        JSplitPane splitter = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeScrollPane, tabbedPanel);
        splitter.setResizeWeight(0.2);

        add(splitter, BorderLayout.CENTER);
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
}
