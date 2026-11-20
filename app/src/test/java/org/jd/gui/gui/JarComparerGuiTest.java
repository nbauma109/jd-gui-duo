/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.gui;

import org.assertj.swing.core.GenericTypeMatcher;
import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.finder.JFileChooserFinder;
import org.assertj.swing.finder.WindowFinder;
import org.assertj.swing.fixture.DialogFixture;
import org.assertj.swing.fixture.FrameFixture;
import org.assertj.swing.fixture.JButtonFixture;
import org.assertj.swing.fixture.JTableFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.Window;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTable;

import static org.assertj.core.api.Assertions.assertThat;

/** "Compare Files...": two archives are compared entry by entry. */
class JarComparerGuiTest extends GuiTestBase {

    private static final String SELECTION = "Select files to compare - Drag and drop";

    @AfterEach
    void closeTheComparer() {
        GuiActionRunner.execute(() -> {
            for (Window w : Window.getWindows()) {
                if (w.isShowing() && w != window.target()) {
                    w.dispose();
                }
            }
        });
    }

    @Test
    void comparisonListsEveryEntryWithItsStatus() throws IOException {
        Path variant = FixtureArchives.createVariantJar(app.workDir(), app.fixtureClasses());

        FrameFixture comparer = compare(app.fixtureJar(), variant);

        assertThat(labels(comparer)).contains("fixture.jar", "variant.jar", "4", "3", "and the files have different contents")
                .anyMatch(text -> text.startsWith("Archives have different size ("));
        assertThat(rows(comparer)).containsExactly(
                "demo/Greeter.class | Changed size", "demo/Main.class | Left only", "demo/Shape.class | =", "added.txt | Right only");
        // the third column is the size change: bytes only on the left are negative, bytes only on the right positive
        assertThat(table(comparer).getValueAt(3, 2).toString()).isEqualTo("+8");
        assertThat(table(comparer).getValueAt(1, 2).toString()).startsWith("-");
    }

    @Test
    void identicalArchivesHaveTheSameSizeAndContents() throws IOException {
        Path copy = Files.copy(app.fixtureJar(), app.workDir().resolve("fixture-copy.jar"), StandardCopyOption.REPLACE_EXISTING);
        FrameFixture comparer = compare(app.fixtureJar(), copy);

        assertThat(labels(comparer)).anyMatch(text -> text.startsWith("Archives have the same size ("))
                .anyMatch(text -> text.contains("the files have exactly the same contents") || text.contains("the files appear to have the same contents"));
        assertThat(rows(comparer)).allMatch(row -> row.endsWith("| ="));
    }

    @Test
    void doubleClickingAChangedClassOpensADiffOfItsSource() throws IOException {
        Path variant = FixtureArchives.createVariantJar(app.workDir(), app.fixtureClasses());
        FrameFixture comparer = compare(app.fixtureJar(), variant);

        new JTableFixture(robot, table(comparer)).cell(org.assertj.swing.data.TableCell.row(0).column(0)).doubleClick();

        FrameFixture diff = WindowFinder.findFrame(new GenericTypeMatcher<JFrame>(JFrame.class) {
            @Override
            protected boolean isMatching(JFrame frame) {
                return frame.isShowing() && frame.getTitle() != null && frame.getTitle().startsWith("Comparison view for class");
            }
        }).withTimeout(TIMEOUT_MS).using(robot);
        diff.requireVisible();
        assertThat(diff.target().getTitle()).contains("Greeter");
    }

    @Test
    void refreshRunsTheComparisonAgain() throws IOException {
        Path variant = FixtureArchives.createVariantJar(app.workDir(), app.fixtureClasses());
        FrameFixture comparer = compare(app.fixtureJar(), variant);

        new JButtonFixture(robot, button(comparer, "Refresh")).click();

        waitUntil("the comparison is done again", () -> labels(comparer).contains("and the files have different contents"));
        assertThat(rows(comparer)).hasSize(4);
    }

    @Test
    void cancellingTheFileSelectionComparesNothing() {
        clickMenu("File", "Compare Files...");
        DialogFixture selection = dialog(SELECTION);

        button(selection, "Cancel").click();

        waitUntil("the selection is closed", () -> !isDialogShowing(SELECTION));
        assertThat(rows(comparerFrame())).isEmpty();
    }

    // -------------------------------------------------------------------

    /** Compares two archives through the file selection dialog and waits for the result. */
    private static FrameFixture compare(Path left, Path right) {
        clickMenu("File", "Compare Files...");
        DialogFixture selection = dialog(SELECTION);
        List<JButton> openButtons = findIn(selection.target(), JButton.class, b -> b.isShowing() && (b.getText() == null || b.getText().isEmpty()));
        new JButtonFixture(robot, openButtons.get(0)).click();
        JFileChooserFinder.findFileChooser().withTimeout(TIMEOUT_MS).using(robot).selectFile(left.toFile()).approve();
        new JButtonFixture(robot, openButtons.get(1)).click();
        JFileChooserFinder.findFileChooser().withTimeout(TIMEOUT_MS).using(robot).selectFile(right.toFile()).approve();
        button(selection, "OK").click();
        FrameFixture comparer = comparerFrame();
        waitUntil("the comparison is done", () -> labels(comparer).stream().anyMatch(text -> text.contains("the files")) && !rows(comparer).isEmpty());
        return comparer;
    }

    private static FrameFixture comparerFrame() {
        return WindowFinder.findFrame(new GenericTypeMatcher<JFrame>(JFrame.class) {
            @Override
            protected boolean isMatching(JFrame frame) {
                return frame.isShowing() && "Jar Comparer".equals(frame.getTitle());
            }
        }).withTimeout(TIMEOUT_MS).using(robot);
    }

    private static JButton button(FrameFixture comparer, String text) {
        return findIn(comparer.target(), JButton.class, b -> b.isShowing() && text.equals(b.getText())).get(0);
    }

    private static List<String> labels(FrameFixture comparer) {
        return findIn(comparer.target(), JLabel.class, l -> l.isShowing() && l.getText() != null && !l.getText().isEmpty())
                .stream().map(l -> GuiActionRunner.execute(l::getText)).toList();
    }

    private static JTable table(FrameFixture comparer) {
        return findIn(comparer.target(), JTable.class, JTable::isShowing).get(0);
    }

    /** The name and the status of each entry. */
    private static List<String> rows(FrameFixture comparer) {
        List<JTable> tables = findIn(comparer.target(), JTable.class, JTable::isShowing);
        if (tables.isEmpty()) {
            return List.of();
        }
        return GuiActionRunner.execute(() -> {
            JTable table = tables.get(0);
            List<String> rows = new ArrayList<>();
            for (int row = 0; row < table.getRowCount(); row++) {
                rows.add(table.getValueAt(row, 0) + " | " + table.getValueAt(row, 1));
            }
            return rows;
        });
    }
}
