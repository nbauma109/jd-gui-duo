/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */
package org.jd.gui.view.component;

import org.jd.gui.model.debug.DebugVariableNode;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.JViewport;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;

/**
 * Tree laid out like the Eclipse Variables/Expressions views: a "Name | Value" table with a column
 * header and grid lines, where the name column holds the tree structure.
 */
public class DebugValueTree extends JTree {
    private static final long serialVersionUID = 1L;

    /** User object of the trailing "Add new expression" row. */
    public static final Object ADD_ROW = new Object() {
        @Override
        public String toString() {
            return "Add new expression";
        }
    };

    private static final int ROW_HEIGHT = 19;
    private static final int RESIZE_GRIP = 4;

    private double nameColumnRatio = 0.45;
    private transient java.util.function.Function<DebugVariableNode.Kind, Icon> iconProvider;
    private transient Icon topLevelIcon;
    private transient Runnable addRowAction;

    public DebugValueTree(DefaultTreeModel model, java.util.function.Function<DebugVariableNode.Kind, Icon> iconProvider, Icon topLevelIcon) {
        super(model);
        this.iconProvider = iconProvider;
        this.topLevelIcon = topLevelIcon;
        setRootVisible(false);
        setShowsRootHandles(true);
        setRowHeight(ROW_HEIGHT);
        setCellRenderer(new Renderer());
        ToolTipManager.sharedInstance().registerComponent(this);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                TreePath path = getPathForLocation(e.getX(), e.getY());
                if (path == null) {
                    int row = getClosestRowForLocation(e.getX(), e.getY());
                    Rectangle bounds = row < 0 ? null : getRowBounds(row);
                    path = bounds != null && bounds.contains(Math.max(bounds.x, e.getX()), e.getY()) ? getPathForRow(row) : null;
                }
                if (path != null && path.getLastPathComponent() instanceof DefaultMutableTreeNode node
                        && node.getUserObject() == ADD_ROW && addRowAction != null) {
                    clearSelection();
                    addRowAction.run();
                }
            }
        });
    }

    public void setAddRowAction(Runnable action) {
        this.addRowAction = action;
    }

    /** X position (in tree coordinates) of the Name/Value column boundary. */
    int getNameColumnX() {
        return Math.max(110, (int) (getWidth() * nameColumnRatio));
    }

    /** Wraps this tree in a scroll pane with the Name/Value column header. */
    public JScrollPane wrapInScrollPane() {
        JScrollPane scrollPane = new JScrollPane(this);
        Header header = new Header();
        scrollPane.setColumnHeaderView(header);
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                header.repaint();
            }
        });
        return scrollPane;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return getParent() instanceof JViewport viewport && viewport.getWidth() > getPreferredSize().width;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return getParent() instanceof JViewport viewport && viewport.getHeight() > getPreferredSize().height;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setColor(gridColor());
            Rectangle clip = g2.getClipBounds() != null ? g2.getClipBounds() : new Rectangle(0, 0, getWidth(), getHeight());
            int firstLine = Math.max(0, clip.y / ROW_HEIGHT);
            for (int y = (firstLine + 1) * ROW_HEIGHT - 1; y < clip.y + clip.height + ROW_HEIGHT; y += ROW_HEIGHT) {
                g2.drawLine(clip.x, y, clip.x + clip.width, y);
            }
            int x = getNameColumnX();
            g2.drawLine(x, clip.y, x, clip.y + clip.height);
        } finally {
            g2.dispose();
        }
    }

    private static Color gridColor() {
        Color color = UIManager.getColor("Table.gridColor");
        return color != null ? color : new Color(0xE0E0E0);
    }

    private int treeIndent() {
        if (getUI() instanceof BasicTreeUI ui) {
            return ui.getLeftChildIndent() + ui.getRightChildIndent();
        }
        return 20;
    }

    private Icon iconFor(DefaultMutableTreeNode node, int depth) {
        if (depth == 1 && topLevelIcon != null) {
            return topLevelIcon;
        }
        return node.getUserObject() instanceof DebugVariableNode variable
            ? iconProvider.apply(variable.kind) : iconProvider.apply(DebugVariableNode.Kind.LOCAL);
    }

    private final class Renderer implements TreeCellRenderer {
        private final JPanel panel = new JPanel(new BorderLayout());
        private final JLabel name = new JLabel();
        private final JLabel value = new JLabel();

        Renderer() {
            name.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 4));
            name.setIconTextGap(4);
            value.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 4));
            panel.add(name, BorderLayout.WEST);
            panel.add(value, BorderLayout.CENTER);
        }

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object object, boolean selected,
                boolean expanded, boolean leaf, int row, boolean hasFocus) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) object;
            Object userObject = node.getUserObject();
            TreePath path = tree.getPathForRow(row);
            int depth = path == null ? 1 : path.getPathCount() - 1;
            int rowX = treeIndent() * depth;
            int nameWidth = Math.max(40, getNameColumnX() - rowX);

            Color background = selected ? UIManager.getColor("Tree.selectionBackground") : tree.getBackground();
            Color foreground = selected ? UIManager.getColor("Tree.selectionForeground") : tree.getForeground();
            panel.setOpaque(true);
            panel.setBackground(background);
            name.setForeground(foreground);
            value.setForeground(foreground);
            name.setFont(tree.getFont());
            value.setFont(tree.getFont());

            if (userObject instanceof DebugVariableNode variable) {
                name.setText(variable.name);
                name.setIcon(iconFor(node, depth));
                value.setText(variable.valueText);
                boolean error = variable.valueText != null && variable.valueText.startsWith("<") && variable.valueText.endsWith(">");
                if (error && !selected) {
                    value.setForeground(new Color(0xB00020));
                }
                panel.setToolTipText(variable.valueText == null || variable.valueText.isEmpty()
                    ? variable.name : variable.name + " = " + variable.valueText);
            } else if (userObject == ADD_ROW) {
                name.setText(ADD_ROW.toString());
                name.setIcon(new PlusIcon());
                name.setFont(tree.getFont().deriveFont(Font.ITALIC));
                name.setForeground(selected ? foreground : Color.GRAY);
                value.setText("");
                panel.setToolTipText(null);
            } else {
                name.setText(String.valueOf(userObject));
                name.setIcon(null);
                value.setText("");
                panel.setToolTipText(null);
            }
            name.setPreferredSize(new Dimension(nameWidth, ROW_HEIGHT));
            panel.setPreferredSize(new Dimension(Math.max(nameWidth + 40, tree.getWidth() - rowX), ROW_HEIGHT));
            return panel;
        }
    }

    /** Small green plus used by the "Add new expression" row. */
    private static final class PlusIcon implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0x3C9A3C));
                g2.fillRect(x + 6, y + 2, 3, 11);
                g2.fillRect(x + 2, y + 6, 11, 3);
            } finally {
                g2.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            return 16;
        }

        @Override
        public int getIconHeight() {
            return 16;
        }
    }

    /** "Name | Value" header; the boundary can be dragged. */
    private final class Header extends JComponent {
        private static final long serialVersionUID = 1L;
        private boolean dragging;

        Header() {
            setPreferredSize(new Dimension(0, 21));
            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    setCursor(Math.abs(e.getX() - getNameColumnX()) <= RESIZE_GRIP
                        ? Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR) : Cursor.getDefaultCursor());
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    dragging = Math.abs(e.getX() - getNameColumnX()) <= RESIZE_GRIP;
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (dragging && DebugValueTree.this.getWidth() > 0) {
                        nameColumnRatio = Math.min(0.85, Math.max(0.15, e.getX() / (double) DebugValueTree.this.getWidth()));
                        DebugValueTree.this.repaint();
                        repaint();
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    dragging = false;
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Color background = UIManager.getColor("TableHeader.background");
            g.setColor(background != null ? background : new Color(0xF0F0F0));
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(gridColor());
            g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
            int x = getNameColumnX();
            g.drawLine(x, 0, x, getHeight());
            Color foreground = UIManager.getColor("TableHeader.foreground");
            g.setColor(foreground != null ? foreground : Color.BLACK);
            g.setFont(DebugValueTree.this.getFont());
            int baseline = (getHeight() + g.getFontMetrics().getAscent() - g.getFontMetrics().getDescent()) / 2;
            g.drawString("Name", 6, baseline);
            g.drawString("Value", x + 6, baseline);
        }
    }
}
