/*
 * Copyright (c) 2026 @nbauma109.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.view;

import org.jd.gui.util.swing.SwingUtil;
import org.jd.gui.view.bean.QuickOutlineListCellBean;
import org.jd.gui.view.component.Tree;
import org.jd.gui.view.renderer.TreeNodeRenderer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Locale;
import java.util.function.Consumer;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

public class QuickOutlineView {

    private static final Color OUTLINE_BACKGROUND = Color.decode("#FFFFE1");

    private JDialog quickOutlineDialog;
    private Tree quickOutlineTree;
    private JTextField filterField;

    private DefaultMutableTreeNode originalRootNode;

    public QuickOutlineView(JFrame mainFrame, Consumer<String> selectedMemberCallback) {
        SwingUtil.invokeLater(() -> {
            quickOutlineDialog = new JDialog(mainFrame, "Quick Outline", false);
            quickOutlineDialog.setUndecorated(true);
            quickOutlineDialog.setResizable(false);
            quickOutlineDialog.setLayout(new BorderLayout());
            quickOutlineDialog.getContentPane().setBackground(OUTLINE_BACKGROUND);

            filterField = new JTextField();
            filterField.setBackground(OUTLINE_BACKGROUND);
            filterField.setBorder(javax.swing.BorderFactory.createEmptyBorder(6, 8, 6, 8));
            filterField.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    onFilterChanged();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    onFilterChanged();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    onFilterChanged();
                }
            });
            filterField.registerKeyboardAction(
                    e -> selectFirstResultAndFocusTree(),
                    KeyStroke.getKeyStroke("DOWN"),
                    JComponent.WHEN_FOCUSED);
            quickOutlineDialog.add(filterField, BorderLayout.NORTH);

            quickOutlineTree = new Tree();
            quickOutlineTree.setRootVisible(true);
            quickOutlineTree.setShowsRootHandles(true);
            quickOutlineTree.setBackground(OUTLINE_BACKGROUND);
            quickOutlineTree.setCellRenderer(new TreeNodeRenderer());
            quickOutlineTree.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        onMemberSelected(selectedMemberCallback);
                    }
                }
            });
            quickOutlineTree.registerKeyboardAction(
                    e -> onMemberSelected(selectedMemberCallback),
                    KeyStroke.getKeyStroke("ENTER"),
                    JComponent.WHEN_FOCUSED);
            quickOutlineTree.registerKeyboardAction(
                    e -> filterField.requestFocusInWindow(),
                    KeyStroke.getKeyStroke("UP"),
                    JComponent.WHEN_FOCUSED);

            JScrollPane scrollPane = new JScrollPane(quickOutlineTree);
            scrollPane.setPreferredSize(new Dimension(430, 260));
            scrollPane.getViewport().setBackground(OUTLINE_BACKGROUND);
            quickOutlineDialog.add(scrollPane, BorderLayout.CENTER);

            JRootPane rootPane = quickOutlineDialog.getRootPane();
            rootPane.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
                    KeyStroke.getKeyStroke("ESCAPE"), "QuickOutlineView.cancel");
            rootPane.getActionMap().put("QuickOutlineView.cancel", new AbstractAction() {
                private static final long serialVersionUID = 1L;

                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    closeDialog();
                }
            });

            installAutoCloseHandlers();

            quickOutlineDialog.pack();
            quickOutlineDialog.setLocationRelativeTo(mainFrame);
        });
    }

    public void show(DefaultMutableTreeNode rootNode, JComponent anchorComponent) {
        SwingUtil.invokeLater(() -> {
            this.originalRootNode = rootNode;

            filterField.setText("");
            applyFilter("");

            if (anchorComponent != null && anchorComponent.isShowing()) {
                Point location = anchorComponent.getLocationOnScreen();
                int x = location.x + anchorComponent.getWidth() - quickOutlineDialog.getWidth() - 20;
                int y = location.y + anchorComponent.getHeight() - quickOutlineDialog.getHeight() - 20;
                quickOutlineDialog.setLocation(x, y);
            }

            quickOutlineDialog.setVisible(true);
            filterField.requestFocusInWindow();
        });
    }

    protected void onMemberSelected(Consumer<String> selectedMemberCallback) {
        TreePath path = quickOutlineTree.getSelectionPath();
        if (path == null) {
            return;
        }

        Object lastPathComponent = path.getLastPathComponent();
        if (lastPathComponent instanceof DefaultMutableTreeNode treeNode) {
            Object userObject = treeNode.getUserObject();
            if (userObject instanceof QuickOutlineListCellBean cellBean) {
                String fragment = cellBean.fragment();
                if (fragment != null && !fragment.isEmpty()) {
                    selectedMemberCallback.accept(fragment);
                    closeDialog();
                }
            }
        }
    }

     private void installAutoCloseHandlers() {
         quickOutlineDialog.addWindowFocusListener(new WindowAdapter() {
             @Override
             public void windowLostFocus(WindowEvent e) {
                 closeDialog();
             }
         });
     }

     private void closeDialog() {
        if (quickOutlineDialog != null && quickOutlineDialog.isVisible()) {
            quickOutlineDialog.setVisible(false);
        }
    }

    private void onFilterChanged() {
        applyFilter(filterField.getText());
    }

    private void applyFilter(String rawFilter) {
        DefaultMutableTreeNode source = originalRootNode;
        if (source == null) {
            quickOutlineTree.setModel(new DefaultTreeModel(new DefaultMutableTreeNode("")));
            return;
        }

        String filter = normalize(rawFilter);
        DefaultMutableTreeNode filteredRoot = buildFilteredTree(source, filter);

        quickOutlineTree.setModel(new DefaultTreeModel(filteredRoot));
        quickOutlineTree.expandRow(0);
        selectFirstResultRow();
    }

    private void selectFirstResultAndFocusTree() {
        selectFirstResultRow();
        quickOutlineTree.requestFocusInWindow();
    }

    private void selectFirstResultRow() {
        DefaultMutableTreeNode root = getRootNode();
        if (root == null) {
            return;
        }

        int selectedRow = root.getChildCount() > 0 ? 1 : 0;
        quickOutlineTree.setSelectionRow(selectedRow);
        quickOutlineTree.scrollRowToVisible(selectedRow);
    }

    private DefaultMutableTreeNode getRootNode() {
        if (quickOutlineTree.getModel() instanceof DefaultTreeModel model) {
            Object root = model.getRoot();
            if (root instanceof DefaultMutableTreeNode node) {
                return node;
            }
        }
        return null;
    }

    private DefaultMutableTreeNode buildFilteredTree(DefaultMutableTreeNode sourceRoot, String normalizedFilter) {
        DefaultMutableTreeNode copyRoot = new DefaultMutableTreeNode(sourceRoot.getUserObject());

        if (normalizedFilter.isEmpty()) {
            copyChildren(sourceRoot, copyRoot);
            return copyRoot;
        }

        for (int i = 0; i < sourceRoot.getChildCount(); i++) {
            Object childObj = sourceRoot.getChildAt(i);
            if (childObj instanceof DefaultMutableTreeNode child) {
                DefaultMutableTreeNode filteredChild = buildFilteredSubtree(child, normalizedFilter);
                if (filteredChild != null) {
                    copyRoot.add(filteredChild);
                }
            }
        }

        return copyRoot;
    }

    private DefaultMutableTreeNode buildFilteredSubtree(DefaultMutableTreeNode sourceNode, String normalizedFilter) {
        boolean selfMatches = matches(sourceNode, normalizedFilter);

        DefaultMutableTreeNode copyNode = new DefaultMutableTreeNode(sourceNode.getUserObject());
        for (int i = 0; i < sourceNode.getChildCount(); i++) {
            Object childObj = sourceNode.getChildAt(i);
            if (childObj instanceof DefaultMutableTreeNode child) {
                DefaultMutableTreeNode filteredChild = buildFilteredSubtree(child, normalizedFilter);
                if (filteredChild != null) {
                    copyNode.add(filteredChild);
                }
            }
        }

        if (selfMatches || copyNode.getChildCount() > 0) {
            return copyNode;
        }
        return null;
    }

    private void copyChildren(DefaultMutableTreeNode source, DefaultMutableTreeNode target) {
        for (int i = 0; i < source.getChildCount(); i++) {
            Object childObj = source.getChildAt(i);
            if (childObj instanceof DefaultMutableTreeNode child) {
                DefaultMutableTreeNode copiedChild = new DefaultMutableTreeNode(child.getUserObject());
                target.add(copiedChild);
                copyChildren(child, copiedChild);
            }
        }
    }

    private boolean matches(DefaultMutableTreeNode node, String normalizedFilter) {
        Object userObject = node.getUserObject();
        String candidate;

        if (userObject instanceof QuickOutlineListCellBean cellBean) {
            candidate = cellBean.label();
        } else {
            candidate = String.valueOf(userObject);
        }

        return normalize(candidate).contains(normalizedFilter);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).trim();
    }
}