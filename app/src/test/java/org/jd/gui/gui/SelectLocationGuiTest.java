/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.gui;

import org.assertj.swing.core.GenericTypeMatcher;
import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.finder.WindowFinder;
import org.assertj.swing.fixture.DialogFixture;
import org.assertj.swing.fixture.FrameFixture;
import org.assertj.swing.fixture.JTreeFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Window;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTabbedPane;
import javax.swing.JTree;

import static org.assertj.core.api.Assertions.assertThat;

/** A type that exists in several open archives: the user can compare two copies or choose the location to open. */
class SelectLocationGuiTest extends GuiTestBase {

    @BeforeEach
    void openTheArchiveAndATwin() throws IOException {
        openFixtureJar();
        openFile(Files.copy(app.fixtureJar(), app.workDir().resolve("fixture-twin.jar"), StandardCopyOption.REPLACE_EXISTING));
        waitUntil("the types can be opened", () -> isMenuEnabled("Navigation", "Open Type..."));
    }

    @AfterEach
    void closeThePopups() {
        GuiActionRunner.execute(() -> {
            for (Window w : Window.getWindows()) {
                if (w.isShowing() && w != window.target()) {
                    w.dispose();
                }
            }
        });
    }

    @Test
    void twoCopiesOfATypeOfferToCompareTheirSources() {
        chooseGreeterInOpenType();

        DialogFixture question = dialog("Compare JAVA source files ?");
        button(question, "Yes").click();

        FrameFixture diff = WindowFinder.findFrame(new GenericTypeMatcher<JFrame>(JFrame.class) {
            @Override
            protected boolean isMatching(JFrame frame) {
                return frame.isShowing() && frame.getTitle() != null && frame.getTitle().startsWith("Comparison view for class Greeter.class");
            }
        }).withTimeout(TIMEOUT_MS).using(robot);
        diff.requireVisible();
    }

    @Test
    void decliningTheComparisonListsTheLocationsOfTheType() {
        chooseGreeterInOpenType();
        button(dialog("Compare JAVA source files ?"), "No").click();

        JDialog picker = locationPicker();
        JTree tree = tree(picker);

        assertThat(labels(picker)).contains("2 locations:");
        assertThat(rows(tree)).containsExactlyInAnyOrder("fixture-twin.jar", "demo", "Greeter.class", "fixture.jar", "demo", "Greeter.class")
                .containsSubsequence("fixture.jar", "demo", "Greeter.class"); // each archive is followed by its own folder and class
    }

    @Test
    void choosingALocationOpensTheTypeFromThatArchive() {
        chooseGreeterInOpenType();
        button(dialog("Compare JAVA source files ?"), "No").click();
        JDialog picker = locationPicker();
        JTree tree = tree(picker);
        List<String> rows = rows(tree);

        int archive = rows.indexOf("fixture.jar");
        int classOfTheArchive = archive + rows.subList(archive, rows.size()).indexOf("Greeter.class"); // the class that follows fixture.jar
        new JTreeFixture(robot, tree).doubleClickRow(classOfTheArchive);

        waitUntil("the picker is closed", () -> !picker.isShowing());
        waitUntil("the type of fixture.jar is shown", () -> "fixture.jar".equals(selectedMainTab()));
        assertThat(window.target().getTitle()).startsWith("Greeter.class");
        assertThat(sourceText()).contains("public class Greeter implements Shape");
    }

    @Test
    void escapeClosesThePickerWithoutOpeningAnything() {
        chooseGreeterInOpenType();
        button(dialog("Compare JAVA source files ?"), "No").click();
        JDialog picker = locationPicker();
        String selectedBefore = selectedMainTab();

        robot.pressAndReleaseKey(KeyEvent.VK_ESCAPE);

        waitUntil("the picker is closed", () -> !picker.isShowing());
        assertThat(selectedMainTab()).isEqualTo(selectedBefore);
    }

    @Test
    void threeCopiesGoStraightToThePicker() throws IOException {
        openFile(Files.copy(app.fixtureJar(), app.workDir().resolve("fixture-triplet.jar"), StandardCopyOption.REPLACE_EXISTING));
        waitUntil("the types can be opened", () -> isMenuEnabled("Navigation", "Open Type..."));

        chooseGreeterInOpenType();

        JDialog picker = locationPicker();
        assertThat(isDialogShowing("Compare JAVA source files ?")).isFalse();
        assertThat(labels(picker)).contains("3 locations:");
        assertThat(rows(tree(picker)).stream().filter("Greeter.class"::equals)).hasSize(3);
    }

    // -------------------------------------------------------------------

    /** Types "Greeter" in the Open Type dialog and opens the first match. */
    private static void chooseGreeterInOpenType() {
        clickMenu("Navigation", "Open Type...");
        DialogFixture dialog = dialog("Open Type");
        typeText(dialog.textBox(), "Greeter");
        waitUntil("the type is listed", () -> dialog.list().target().getModel().getSize() > 0);
        dialog.list().selectItem(0);
        button(dialog, "Open").click();
    }

    /** The untitled dialog that lists the locations of the type. */
    private static JDialog locationPicker() {
        waitUntil("the locations are listed", () -> picker() != null);
        return picker();
    }

    private static JDialog picker() {
        return GuiActionRunner.execute(() -> java.util.Arrays.stream(Window.getWindows())
                .filter(w -> w.isShowing() && w instanceof JDialog d && (d.getTitle() == null || d.getTitle().isEmpty()))
                .map(JDialog.class::cast).findFirst().orElse(null));
    }

    private static JTree tree(JDialog picker) {
        return findIn(picker, JTree.class, JTree::isShowing).get(0);
    }

    private static List<String> labels(JDialog picker) {
        return GuiActionRunner.execute(() -> findIn(picker, JLabel.class, l -> l.isShowing() && l.getText() != null).stream().map(JLabel::getText).toList());
    }

    private static String selectedMainTab() {
        List<JTabbedPane> panes = findAll(JTabbedPane.class, tp -> tp.getClass().getName().contains("TabbedPanel") && tp.isShowing());
        return GuiActionRunner.execute(() -> {
            JTabbedPane main = panes.get(0);
            return main.getSelectedIndex() < 0 ? null : main.getTitleAt(main.getSelectedIndex());
        });
    }
}
