/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.gui;

import org.assertj.swing.core.MouseButton;
import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.fixture.JTreeFixture;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.Window;
import java.awt.datatransfer.DataFlavor;
import java.io.IOException;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;

import static org.assertj.core.api.Assertions.assertThat;

/** The pop-up menus of the project tree and of the tabs: copy the names, show the byte code, close the tabs. */
class ContextualActionsGuiTest extends GuiTestBase {

    @AfterEach
    void closeTheViewers() {
        GuiActionRunner.execute(() -> {
            for (Window w : Window.getWindows()) {
                if (w.isShowing() && w != window.target()) {
                    w.dispose();
                }
            }
        });
    }

    private static JTreeFixture expandedTree() {
        openFixtureJar();
        JTreeFixture tree = fileTree();
        expandAll(tree);
        return tree;
    }

    private static int row(JTreeFixture tree, String text) {
        int row = rowOf(tree.target(), text);
        assertThat(row).as("row '" + text + "' in " + rows(tree.target())).isGreaterThanOrEqualTo(0);
        return row;
    }

    private static String clipboard() throws Exception {
        return (String) java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
    }

    @Test
    void typesOfferToCopyTheirNameAndMethodsOfferTheByteCodeAndTheGraphs() {
        JTreeFixture tree = expandedTree();

        String[] ofType = tree.showPopupMenuAt(row(tree, "Greeter")).menuLabels();
        robotEscape();
        String[] ofMethod = tree.showPopupMenuAt(row(tree, "greet() : String")).menuLabels();
        robotEscape();
        String[] ofField = tree.showPopupMenuAt(row(tree, "name : String")).menuLabels();
        robotEscape();

        assertThat(ofType).containsExactly("Copy Internal Name", "Copy Qualified Name");
        assertThat(ofField).containsExactly("Copy Internal Name", "Copy Qualified Name");
        assertThat(ofMethod).startsWith("Copy Internal Name", "Copy Qualified Name", "Show Byte Code")
                .hasSize(11)
                .anyMatch(label -> label.startsWith("Show Initial Control Flow Graph (Goto, Loop, Pre-Reduce)"))
                .anyMatch(label -> label.contains("Minimum-Depth Control Flow Graph (with pre-reduce)"));
    }

    @Test
    void copyActionsPutTheNameOfTheNodeOnTheClipboard() throws Exception {
        JTreeFixture tree = expandedTree();

        tree.showPopupMenuAt(row(tree, "Greeter")).menuItemWithPath("Copy Qualified Name").click();
        assertThat(clipboard()).isEqualTo("demo.Greeter");
        tree.showPopupMenuAt(row(tree, "Greeter")).menuItemWithPath("Copy Internal Name").click();
        assertThat(clipboard()).isEqualTo("demo/Greeter");
        tree.showPopupMenuAt(row(tree, "greet() : String")).menuItemWithPath("Copy Qualified Name").click();
        assertThat(clipboard()).isEqualTo("demo.Greeter.greet() : String");
        tree.showPopupMenuAt(row(tree, "greet() : String")).menuItemWithPath("Copy Internal Name").click();
        assertThat(clipboard()).isEqualTo("demo/Greeter.greet() : String");
        tree.showPopupMenuAt(row(tree, "name : String")).menuItemWithPath("Copy Qualified Name").click();
        assertThat(clipboard()).isEqualTo("demo.Greeter.name : String");
    }

    @Test
    void showByteCodeOpensAViewerOfTheMethod() {
        JTreeFixture tree = expandedTree();

        tree.showPopupMenuAt(row(tree, "greet() : String")).menuItemWithPath("Show Byte Code").click();

        waitUntil("the byte code viewer is shown", () -> viewer() != null);
        JFrame viewer = viewer();
        assertThat(viewer.getTitle()).isEqualTo("Byte Code Viewer for public String greet()");
        String text = GuiActionRunner.execute(() -> findIn(viewer, RSyntaxTextArea.class, RSyntaxTextArea::isShowing).get(0).getText());
        assertThat(text).contains("Byte code:").contains("aload_0").contains("getfield name : Ljava/lang/String;").contains("areturn").contains("Line number table");

        // the find bar of the viewer
        JTextField search = findIn(viewer, JTextField.class, JTextField::isShowing).get(0);
        new org.assertj.swing.fixture.JTextComponentFixture(robot, search).setText("areturn");
        new org.assertj.swing.fixture.JButtonFixture(robot, findIn(viewer, javax.swing.JButton.class, b -> b.isShowing() && "Next".equals(b.getText())).get(0)).click();
        new org.assertj.swing.fixture.JButtonFixture(robot, findIn(viewer, javax.swing.JButton.class, b -> b.isShowing() && "Previous".equals(b.getText())).get(0)).click();
        assertThat(viewer.isShowing()).isTrue();
    }

    @Test
    void tabPopupClosesTheTabs() throws IOException {
        openFixtureJar();
        openFile(FixtureArchives.createModularJar(app.workDir()));
        waitUntil("two files are open", () -> mainTabTitles().size() == 2);

        tabPopup(0, "Close Others");
        waitUntil("only one tab is left", () -> mainTabTitles().size() == 1);
        assertThat(mainTabTitles()).hasSize(1);

        tabPopup(0, "Close All");
        waitUntil("no tab is left", () -> mainTabTitles().isEmpty());
        assertThat(mainTabTitles()).isEmpty();
    }

    @Test
    void tabPopupClosesTheTab() throws IOException {
        openFixtureJar();
        openFile(FixtureArchives.createWar(app.workDir()));
        waitUntil("two files are open", () -> mainTabTitles().size() == 2);

        tabPopup(1, "Close");

        waitUntil("one tab is left", () -> mainTabTitles().size() == 1);
        assertThat(mainTabTitles()).hasSize(1);
    }

    private static JFrame viewer() {
        return GuiActionRunner.execute(() -> java.util.Arrays.stream(Window.getWindows())
                .filter(w -> w.isShowing() && w instanceof JFrame f && f.getTitle().startsWith("Byte Code Viewer"))
                .map(JFrame.class::cast).findFirst().orElse(null));
    }

    /** Right-clicks the tab and chooses an entry of the pop-up menu. */
    private static void tabPopup(int tabIndex, String entry) {
        List<JTabbedPane> panes = findAll(JTabbedPane.class, tp -> tp.getClass().getName().contains("TabbedPanel") && tp.isShowing());
        JTabbedPane main = panes.get(0);
        java.awt.Rectangle bounds = GuiActionRunner.execute(() -> main.getBoundsAt(tabIndex));
        robot.click(main, new java.awt.Point(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2), MouseButton.RIGHT_BUTTON, 1);
        robot.waitForIdle();
        JMenuItem item = robot.finder().find(new org.assertj.swing.core.GenericTypeMatcher<JMenuItem>(JMenuItem.class) {
            @Override
            protected boolean isMatching(JMenuItem candidate) {
                return candidate.isShowing() && candidate.getParent() instanceof JPopupMenu && entry.equals(candidate.getText());
            }
        });
        new org.assertj.swing.fixture.JMenuItemFixture(robot, item).click();
    }

    private static void robotEscape() {
        robot.pressAndReleaseKey(java.awt.event.KeyEvent.VK_ESCAPE);
        robot.waitForIdle();
    }
}
