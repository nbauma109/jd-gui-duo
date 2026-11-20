/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.gui;

import org.assertj.swing.core.GenericTypeMatcher;
import org.assertj.swing.core.Robot;
import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.finder.WindowFinder;
import org.assertj.swing.fixture.ComponentContainerFixture;
import org.assertj.swing.fixture.DialogFixture;
import org.assertj.swing.fixture.FrameFixture;
import org.assertj.swing.fixture.JButtonFixture;
import org.assertj.swing.fixture.JMenuItemFixture;
import org.assertj.swing.fixture.JTextComponentFixture;
import org.assertj.swing.fixture.JTreeFixture;
import org.assertj.swing.timing.Condition;
import org.assertj.swing.timing.Pause;
import org.assertj.swing.timing.Timeout;
import org.jd.gui.view.component.Tree;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.Window;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JTabbedPane;
import javax.swing.JTree;
import javax.swing.text.JTextComponent;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base class of the GUI tests. Every test drives the real application, started once per JVM on the real
 * desktop (see {@link GuiApp}), through genuine mouse and keyboard events.
 */
@Tag("gui")
@ExtendWith(FailureArtifacts.class)
abstract class GuiTestBase {

    static final long TIMEOUT_MS = 20_000;

    protected static GuiApp app;
    protected static Robot robot;
    protected static FrameFixture window;

    @BeforeAll
    static void startApplication() {
        app = GuiApp.get();
        robot = app.robot();
        window = app.mainWindow();
    }

    /** Leaves the application in its pristine state: no dialog, no secondary window, no open file. */
    @AfterEach
    void resetApplication() {
        GuiActionRunner.execute(() -> {
            for (Window w : Window.getWindows()) {
                if (w.isShowing() && w != window.target() && (w instanceof Dialog || w instanceof Frame)) {
                    w.dispose();
                }
            }
        });
        for (int i = 0; i < 10 && isMenuEnabled("File", "Close"); i++) {
            window.menuItemWithPath("File", "Close").click();
            robot.waitForIdle();
        }
    }

    // ---------------------------------------------------------------- waiting

    protected static void waitUntil(String description, BooleanSupplier condition) {
        waitUntil(description, TIMEOUT_MS, condition);
    }

    protected static void waitUntil(String description, long timeoutMs, BooleanSupplier condition) {
        Pause.pause(new Condition(description) {
            @Override
            public boolean test() {
                return GuiActionRunner.execute(condition::getAsBoolean);
            }
        }, Timeout.timeout(timeoutMs));
    }

    // ---------------------------------------------------------------- menus & dialogs

    protected static void clickMenu(String... path) {
        window.menuItemWithPath(path).click();
        robot.waitForIdle();
    }

    protected static boolean isMenuEnabled(String... path) {
        JMenuItem item = window.menuItemWithPath(path).target();
        return GuiActionRunner.execute(item::isEnabled);
    }

    protected static DialogFixture dialog(String title) {
        return WindowFinder.findDialog(new GenericTypeMatcher<JDialog>(JDialog.class) {
            @Override
            protected boolean isMatching(JDialog dialog) {
                return dialog.isShowing() && title.equals(dialog.getTitle());
            }
        }).withTimeout(TIMEOUT_MS).using(robot);
    }

    protected static boolean isDialogShowing(String title) {
        return GuiActionRunner.execute(() -> {
            for (Window w : Window.getWindows()) {
                if (w instanceof Dialog d && d.isShowing() && title.equals(d.getTitle())) {
                    return true;
                }
            }
            return false;
        });
    }

    protected static JButtonFixture button(ComponentContainerFixture container, String text) {
        return container.button(new GenericTypeMatcher<JButton>(JButton.class) {
            @Override
            protected boolean isMatching(JButton button) {
                return button.isShowing() && text.equals(button.getText() == null ? null : button.getText().trim());
            }
        });
    }

    /** The virtual key of the platform's menu-shortcut modifier: Command on macOS, Control elsewhere. */
    protected static int menuKey() {
        return java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() == java.awt.event.InputEvent.META_DOWN_MASK
                ? java.awt.event.KeyEvent.VK_META : java.awt.event.KeyEvent.VK_CONTROL;
    }

    /** Presses the keys together (modifiers first), then releases them in reverse order, on the focused main window. */
    protected static void chord(int... keys) {
        window.focus();
        int pressed = 0;
        try {
            for (int key : keys) {
                robot.pressKey(key);
                pressed++;
            }
        } finally {
            for (int i = pressed - 1; i >= 0; i--) {
                robot.releaseKey(keys[i]);
            }
        }
        robot.waitForIdle();
    }

    /** Presses a shortcut made of the platform menu-shortcut modifier, optional extra modifiers and the key. */
    protected static void pressShortcut(int keyCode, int... extraModifierKeys) {
        int[] keys = new int[extraModifierKeys.length + 2];
        keys[0] = menuKey();
        System.arraycopy(extraModifierKeys, 0, keys, 1, extraModifierKeys.length);
        keys[keys.length - 1] = keyCode;
        chord(keys);
    }

    protected static void pressKey(int keyCode) {
        window.focus();
        robot.pressAndReleaseKey(keyCode);
        robot.waitForIdle();
    }

    /**
     * Types text into a field with real key events. The robot types with a US keyboard map, so when the machine
     * running the tests has another layout (the characters then come out wrong) the text is set directly instead.
     */
    protected static void typeText(JTextComponentFixture field, String text) {
        field.focus();
        field.deleteText();
        field.enterText(text);
        if (!text.equals(field.text())) {
            field.setText(text);
        }
        robot.waitForIdle();
    }

    /** The "File > Recent Files" entry of a file (its label is a shortened path, the file is a client property). Opens the menu. */
    protected static JMenuItemFixture recentFileItem(Path file) {
        clickMenu("File", "Recent Files");
        // the same file can be listed twice under different spellings (Windows 8.3 short and long names)
        return new JMenuItemFixture(robot, robot.finder().findAll(new GenericTypeMatcher<JCheckBoxMenuItem>(JCheckBoxMenuItem.class) {
            @Override
            protected boolean isMatching(JCheckBoxMenuItem item) {
                return item.isShowing() && sameFile(file.toFile(), item.getClientProperty("recentFile"));
            }
        }).stream().findFirst().orElseThrow(() -> new AssertionError("No recent file entry for " + file)));
    }

    /** Compares files by canonical path (Windows may hand out 8.3 short names for the temporary directory). */
    private static boolean sameFile(java.io.File expected, Object actual) {
        if (!(actual instanceof java.io.File file)) {
            return false;
        }
        try {
            return expected.getCanonicalFile().equals(file.getCanonicalFile());
        } catch (java.io.IOException _) {
            return expected.equals(file);
        }
    }

    // ---------------------------------------------------------------- files

    /** In a Save dialog: goes to the folder of the file, types its name and confirms. */
    protected static void saveAs(Path file) {
        org.assertj.swing.fixture.JFileChooserFixture chooser = window.fileChooser();
        chooser.setCurrentDirectory(file.getParent().toFile());
        typeText(chooser.fileNameTextBox(), file.getFileName().toString());
        chooser.approve();
    }

    /** Opens a file with File > Open File... and the file chooser, then waits until a tab shows it. */
    protected static void openFile(Path file) {
        clickMenu("File", "Open File...");
        window.fileChooser().selectFile(file.toFile()).approve();
        String name = file.getFileName().toString();
        waitUntil("the file " + name + " is opened", () -> window.target().getTitle().startsWith(name));
        robot.waitForIdle();
    }

    protected static void openFixtureJar() {
        openFile(app.fixtureJar());
    }

    protected static JTreeFixture fileTree() {
        return window.tree(new GenericTypeMatcher<JTree>(JTree.class) {
            @Override
            protected boolean isMatching(JTree tree) {
                return tree instanceof Tree && tree.isShowing();
            }
        });
    }

    /** Text of the rows of a tree (the renderers are panels of labels). */
    protected static List<String> rows(JTree tree) {
        return GuiActionRunner.execute(() -> {
            List<String> rows = new ArrayList<>();
            for (int i = 0; i < tree.getRowCount(); i++) {
                Object node = tree.getPathForRow(i).getLastPathComponent();
                Component renderer = tree.getCellRenderer().getTreeCellRendererComponent(
                        tree, node, false, tree.isExpanded(i), tree.getModel().isLeaf(node), i, false);
                rows.add(labels(renderer));
            }
            return rows;
        });
    }

    private static String labels(Component component) {
        StringBuilder sb = new StringBuilder();
        collectLabels(component, sb);
        return sb.toString().trim();
    }

    private static void collectLabels(Component component, StringBuilder sb) {
        if (component instanceof JLabel label && label.getText() != null) {
            sb.append(label.getText());
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                collectLabels(child, sb);
            }
        }
    }

    protected static int rowOf(JTree tree, String text) {
        List<String> rows = rows(tree);
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).equals(text)) {
                return i;
            }
        }
        return -1;
    }

    /** Expands the nodes of a tree (programmatically, as test set-up) until the number of rows is stable. */
    protected static void expandAll(JTreeFixture tree) {
        int previous = -1;
        for (int guard = 0; guard < 20; guard++) {
            int count = GuiActionRunner.execute(() -> {
                JTree t = tree.target();
                for (int i = t.getRowCount() - 1; i >= 0; i--) {
                    t.expandRow(i);
                }
                return t.getRowCount();
            });
            if (count == previous) {
                return;
            }
            previous = count;
            robot.waitForIdle();
        }
    }

    /** Titles of the tabs of the main window (one per opened file). */
    protected static List<String> mainTabTitles() {
        List<JTabbedPane> tabbedPanes = findAll(JTabbedPane.class, tp -> tp.getClass().getName().contains("TabbedPanel"));
        return GuiActionRunner.execute(() -> {
            List<String> titles = new ArrayList<>();
            if (!tabbedPanes.isEmpty()) {
                JTabbedPane main = tabbedPanes.get(0);
                for (int i = 0; i < main.getTabCount(); i++) {
                    titles.add(main.getTitleAt(i));
                }
            }
            return titles;
        });
    }

    /** Double-clicks the node with the given text and waits until a source page shows it. */
    protected static void openType(String simpleClassName) {
        JTreeFixture tree = fileTree();
        expandAll(tree);
        int row = rowOf(tree.target(), simpleClassName);
        assertThat(row).as("row of " + simpleClassName + " in " + rows(tree.target())).isGreaterThanOrEqualTo(0);
        tree.doubleClickRow(row);
        waitUntil("the source of " + simpleClassName + " is displayed", () -> window.target().getTitle().startsWith(simpleClassName + ".class"));
        waitUntil("the decompiled source is displayed", () -> !sourceText().isEmpty());
    }

    // ---------------------------------------------------------------- source page

    protected static JTextComponent sourceArea() {
        return window.textBox(new GenericTypeMatcher<JTextComponent>(JTextComponent.class) {
            @Override
            protected boolean isMatching(JTextComponent component) {
                return component.isShowing() && component.getClass().getName().contains("SourceSyntaxTextArea");
            }
        }).target();
    }

    protected static String sourceText() {
        List<JTextComponent> areas = findAll(JTextComponent.class, c -> c.isShowing() && c.getClass().getName().contains("SourceSyntaxTextArea"));
        return areas.isEmpty() ? "" : GuiActionRunner.execute(() -> areas.get(0).getText());
    }

    protected static <T extends Component> List<T> findAll(Class<T> type, java.util.function.Predicate<T> filter) {
        return GuiActionRunner.execute(() -> {
            List<T> found = new ArrayList<>();
            collect(window.target(), type, filter, found);
            return found;
        });
    }

    private static <T extends Component> void collect(Component c, Class<T> type, java.util.function.Predicate<T> filter, List<T> found) {
        if (type.isInstance(c) && filter.test(type.cast(c))) {
            found.add(type.cast(c));
        }
        if (c instanceof Container container) {
            for (Component child : container.getComponents()) {
                collect(child, type, filter, found);
            }
        }
    }
}
