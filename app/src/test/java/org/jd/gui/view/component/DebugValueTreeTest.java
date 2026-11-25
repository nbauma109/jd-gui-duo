/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.view.component;

import org.jd.gui.model.debug.DebugVariableNode;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

import static org.assertj.core.api.Assertions.assertThat;

/** The "Name | Value" tree of the variables and of the expressions. */
class DebugValueTreeTest {

    private static final String GRID_COLOR = "Table.gridColor";

    private static final Icon TOP = new ImageIcon(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));
    private static final Icon BELOW = new ImageIcon(new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB));

    private final List<DebugVariableNode.Kind> kinds = new ArrayList<>();

    private static DefaultMutableTreeNode node(Object userObject) {
        return new DefaultMutableTreeNode(userObject);
    }

    private DebugValueTree tree(Icon topLevelIcon, DefaultMutableTreeNode... rows) {
        DefaultMutableTreeNode root = node("root");
        for (DefaultMutableTreeNode row : rows) {
            root.add(row);
        }
        DebugValueTree tree = new DebugValueTree(new DefaultTreeModel(root), kind -> {
            kinds.add(kind);
            return BELOW;
        }, topLevelIcon);
        tree.setSize(400, 200);
        return tree;
    }

    private static JPanel render(DebugValueTree tree, int row, boolean selected) {
        Object node = tree.getPathForRow(row).getLastPathComponent();
        Component component = tree.getCellRenderer().getTreeCellRendererComponent(tree, node, selected, false, true, row, false);
        return (JPanel) component;
    }

    private static JLabel label(JPanel panel, int index) {
        return (JLabel) panel.getComponent(index);
    }

    @Test
    void aVariableShowsItsNameItsValueAndTheIconOfItsDepth() {
        DefaultMutableTreeNode parent = node(new DebugVariableNode("point", "Point (id=1)", DebugVariableNode.Kind.FIELD_PRIVATE));
        parent.add(node(new DebugVariableNode("x", "1", DebugVariableNode.Kind.FIELD_PUBLIC)));
        DebugValueTree tree = tree(TOP, parent);
        tree.expandRow(0);

        JPanel top = render(tree, 0, false);
        assertThat(label(top, 0).getText()).isEqualTo("point");
        assertThat(label(top, 0).getIcon()).isSameAs(TOP);
        assertThat(label(top, 1).getText()).isEqualTo("Point (id=1)");
        assertThat(top.getToolTipText()).isEqualTo("point = Point (id=1)");
        JPanel below = render(tree, 1, true);
        assertThat(label(below, 0).getIcon()).isSameAs(BELOW);
        assertThat(kinds).contains(DebugVariableNode.Kind.FIELD_PUBLIC);
    }

    @Test
    void anErrorIsRedUnlessItIsSelectedAndAnEmptyValueIsNotInTheToolTip() {
        DebugValueTree tree = tree(null, node(new DebugVariableNode("broken", "<error>")), node(new DebugVariableNode("empty", "")));
        Color red = new Color(0xB00020);

        assertThat(label(render(tree, 0, false), 1).getForeground()).isEqualTo(red);
        assertThat(label(render(tree, 0, true), 1).getForeground()).isNotEqualTo(red);
        assertThat(render(tree, 1, false).getToolTipText()).isEqualTo("empty");
        assertThat(kinds).containsOnly(DebugVariableNode.Kind.LOCAL);
    }

    @Test
    void theRowToAddAnExpressionAndAPlainRowHaveNoValue() {
        DebugValueTree tree = tree(TOP, node(DebugValueTree.ADD_ROW), node("loading..."));

        JPanel add = render(tree, 0, false);
        assertThat(label(add, 0).getText()).isEqualTo("Add new expression");
        assertThat(label(add, 0).getForeground()).isEqualTo(Color.GRAY);
        assertThat(label(add, 0).getFont().isItalic()).isTrue();
        assertThat(label(add, 0).getIcon().getIconWidth()).isEqualTo(16);
        assertThat(label(add, 0).getIcon().getIconHeight()).isEqualTo(16);
        paint((JComponent) add, 400, 19);
        BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = icon.createGraphics();
        label(add, 0).getIcon().paintIcon(add, graphics, 0, 0);
        graphics.dispose();
        assertThat(icon.getRGB(7, 7)).isNotZero();
        JPanel plain = render(tree, 1, false);
        assertThat(label(plain, 0).getText()).isEqualTo("loading...");
        assertThat(label(plain, 0).getIcon()).isNull();
        assertThat(label(plain, 1).getText()).isEmpty();
        assertThat(plain.getToolTipText()).isNull();
    }

    @Test
    void aClickOnTheRowToAddAnExpressionRunsItsActionAlsoRightOfItsText() throws Exception {
        DebugValueTree tree = tree(TOP, node(new DebugVariableNode("x", "1")), node(DebugValueTree.ADD_ROW));
        int[] added = new int[1];

        SwingUtilities.invokeAndWait(() -> {
            click(tree, 30, tree.getRowBounds(1).y + 5); // no action yet
            tree.setAddRowAction(() -> added[0]++);
            click(tree, 30, tree.getRowBounds(1).y + 5);
            click(tree, 399, tree.getRowBounds(1).y + 5);
            click(tree, 30, tree.getRowBounds(0).y + 5); // another row
            click(tree, 30, 190); // below the rows
        });

        assertThat(added[0]).isEqualTo(2);
    }

    private static void click(JComponent component, int x, int y) {
        component.dispatchEvent(new MouseEvent(component, MouseEvent.MOUSE_CLICKED, 0, 0, x, y, 1, false, MouseEvent.BUTTON1));
    }

    private static BufferedImage paint(JComponent component, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        component.setSize(width, height);
        component.doLayout();
        component.paint(graphics);
        graphics.dispose();
        return image;
    }

    /** Paints with a grid that can be told apart: the one of some looks is as white as the background. */
    private static BufferedImage withRedGrid(Supplier<BufferedImage> painting) throws Exception {
        Object color = UIManager.get(GRID_COLOR);
        BufferedImage[] image = new BufferedImage[1];
        SwingUtilities.invokeAndWait(() -> {
            UIManager.put(GRID_COLOR, Color.RED);
            try {
                image[0] = painting.get();
            } finally {
                UIManager.put(GRID_COLOR, color);
            }
        });
        return image[0];
    }

    @Test
    void theBoundaryBetweenTheColumnsCanBeDraggedInTheHeader() throws Exception {
        DebugValueTree tree = tree(TOP, node(new DebugVariableNode("x", "1")));
        JScrollPane scrollPane = tree.wrapInScrollPane();
        JComponent header = (JComponent) scrollPane.getColumnHeader().getView();
        assertThat(tree.getNameColumnX()).isEqualTo(180);

        SwingUtilities.invokeAndWait(() -> {
            tree.setSize(400, 200);
            move(header, MouseEvent.MOUSE_MOVED, 181);
            assertThat(header.getCursor().getType()).isEqualTo(Cursor.E_RESIZE_CURSOR);
            move(header, MouseEvent.MOUSE_MOVED, 20);
            assertThat(header.getCursor().getType()).isEqualTo(Cursor.DEFAULT_CURSOR);
            // a drag that does not start on the boundary does nothing
            move(header, MouseEvent.MOUSE_PRESSED, 20);
            move(header, MouseEvent.MOUSE_DRAGGED, 300);
            assertThat(tree.getNameColumnX()).isEqualTo(180);
            move(header, MouseEvent.MOUSE_PRESSED, 180);
            move(header, MouseEvent.MOUSE_DRAGGED, 240);
            assertThat(tree.getNameColumnX()).isEqualTo(240);
            // the columns keep a part of the width each
            move(header, MouseEvent.MOUSE_DRAGGED, 4000);
            assertThat(tree.getNameColumnX()).isEqualTo(340);
            move(header, MouseEvent.MOUSE_RELEASED, 340);
            move(header, MouseEvent.MOUSE_DRAGGED, 200);
            assertThat(tree.getNameColumnX()).isEqualTo(340);
        });

        assertThat(header.getPreferredSize().height).isEqualTo(21);
        BufferedImage painted = withRedGrid(() -> paint(header, 400, 21));
        assertThat(painted.getRGB(340, 5)).isEqualTo(Color.RED.getRGB());
        assertThat(painted.getRGB(320, 2)).isNotEqualTo(Color.RED.getRGB());
    }

    private static void move(JComponent component, int id, int x) {
        component.dispatchEvent(new MouseEvent(component, id, 0, 0, x, 5, 1, false, MouseEvent.BUTTON1));
    }

    @Test
    void theGridIsPaintedAndTheTreeFillsAViewThatIsLargerThanItsRows() throws Exception {
        DebugValueTree tree = tree(TOP, node(new DebugVariableNode("x", "1")));
        assertThat(tree.getScrollableTracksViewportWidth()).isFalse();
        assertThat(tree.getScrollableTracksViewportHeight()).isFalse();
        JScrollPane scrollPane = tree.wrapInScrollPane();

        SwingUtilities.invokeAndWait(() -> {
            scrollPane.getViewport().setSize(2000, 2000);
            assertThat(tree.getScrollableTracksViewportWidth()).isTrue();
            assertThat(tree.getScrollableTracksViewportHeight()).isTrue();
            scrollPane.getViewport().setSize(1, 1);
            assertThat(tree.getScrollableTracksViewportWidth()).isFalse();
            assertThat(tree.getScrollableTracksViewportHeight()).isFalse();
        });
        BufferedImage image = withRedGrid(() -> paint(tree, 400, 200));

        // a line under each row, and one between the columns
        assertThat(image.getRGB(300, 18)).isEqualTo(Color.RED.getRGB());
        assertThat(image.getRGB(180, 100)).isEqualTo(Color.RED.getRGB());
        assertThat(image.getRGB(300, 100)).isNotEqualTo(Color.RED.getRGB());
    }
}
