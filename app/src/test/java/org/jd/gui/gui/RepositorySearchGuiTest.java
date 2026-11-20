/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.gui;

import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.fixture.DialogFixture;
import org.assertj.swing.fixture.FrameFixture;
import org.assertj.swing.fixture.JTabbedPaneFixture;
import org.assertj.swing.fixture.JTableFixture;
import org.assertj.swing.fixture.JTextComponentFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Window;
import java.io.IOException;

import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;

import static org.assertj.core.api.Assertions.assertThat;

/** The repository search window, pointed at a local Nexus through the secured preferences. */
class RepositorySearchGuiTest extends GuiTestBase {

    private LocalNexus nexus;

    @BeforeEach
    void pointToTheLocalNexus() throws IOException {
        nexus = new LocalNexus();
        clickMenu("Help", "Secured Preferences...");
        DialogFixture dialog = dialog("Secured Preferences");
        org.assertj.swing.fixture.JTextComponentFixture url = new org.assertj.swing.fixture.JTextComponentFixture(robot,
                findIn(dialog.target(), JTextField.class, f -> f.isShowing() && !(f instanceof javax.swing.JPasswordField)).get(0));
        url.setText(nexus.baseUrl());
        button(dialog, "Ok").click();
        waitUntil("the dialog is closed", () -> !isDialogShowing("Secured Preferences"));
    }

    @AfterEach
    void restoreThePreferences() {
        GuiActionRunner.execute(() -> {
            for (Window w : Window.getWindows()) {
                if (w.isShowing() && w != window.target()) {
                    w.dispose();
                }
            }
        });
        clickMenu("Help", "Secured Preferences...");
        DialogFixture dialog = dialog("Secured Preferences");
        new org.assertj.swing.fixture.JButtonFixture(robot, findIn(dialog.target(), javax.swing.JButton.class, b -> b.isShowing() && "Clear".equals(b.getText())).get(0)).click();
        button(dialog, "Ok").click();
        nexus.close();
    }

    @Test
    void keywordSearchListsTheArtifactsAndShowsTheDependencySnippets() {
        FrameFixture search = openSearch();

        typeText(field(search, 0), "demo");
        button(search, "Search").click();

        waitUntil("the results are listed", () -> resultRows(search).size() == 2);
        assertThat(resultRows(search)).allMatch(row -> row.contains("org.demo") && row.contains("demo-lib") && row.contains("1.2.3"));
        assertThat(nexus.requests()).contains("/service/rest/v1/search?q=demo");

        selectResult(search, 0);
        assertThat(snippet(search, "Maven")).contains("<groupId>org.demo</groupId>").contains("<artifactId>demo-lib</artifactId>").contains("<version>1.2.3</version>");
        assertThat(snippet(search, "Gradle")).contains("org.demo:demo-lib:1.2.3");
        assertThat(snippet(search, "Ivy")).contains("org=\"org.demo\"").contains("name=\"demo-lib\"");
        assertThat(snippet(search, "SBT")).contains("demo-lib").contains("1.2.3");
        assertThat(snippet(search, "Leiningen")).contains("org.demo/demo-lib").contains("1.2.3");
        assertThat(snippet(search, "Grape")).contains("demo-lib");
        assertThat(snippet(search, "Buildr")).contains("org.demo:demo-lib:jar:1.2.3");
        assertThat(snippet(search, "bld")).contains("org.demo").contains("demo-lib");
    }

    @Test
    void sha1AndCoordinatesSearches() {
        FrameFixture search = openSearch();

        selectTab(search, "SHA-1");
        typeText(field(search, 0), "da39a3ee5e6b4b0d3255bfef95601890afd80709");
        button(search, "Search").click();
        waitUntil("the SHA-1 results are listed", () -> resultRows(search).size() == 1);
        assertThat(nexus.requests()).contains("/service/rest/v1/search/assets?sha1=da39a3ee5e6b4b0d3255bfef95601890afd80709");

        selectTab(search, "Coordinates");
        typeText(field(search, 0), "org.demo");
        typeText(field(search, 1), "demo-lib");
        typeText(field(search, 2), "1.2.3");
        button(search, "Search").click();
        waitUntil("the coordinates results are listed", () -> resultRows(search).size() == 2);
        assertThat(nexus.requests()).contains("/service/rest/v1/search?group=org.demo&name=demo-lib&version=1.2.3");
    }

    private FrameFixture openSearch() {
        clickMenu("Help", "Search maven central...");
        waitUntil("the search window is shown", () -> GuiActionRunner.execute(() -> java.util.Arrays.stream(Window.getWindows())
                .anyMatch(w -> w.isShowing() && w instanceof java.awt.Frame f && "Repository Search".equals(f.getTitle()))));
        java.awt.Frame frame = GuiActionRunner.execute(() -> java.util.Arrays.stream(Window.getWindows())
                .filter(w -> w.isShowing() && w instanceof java.awt.Frame f && "Repository Search".equals(f.getTitle())).map(java.awt.Frame.class::cast).findFirst().orElseThrow());
        return new FrameFixture(robot, (javax.swing.JFrame) frame);
    }

    /** The text field number {@code index} among the ones that are shown (they belong to the selected tab). */
    private static JTextComponentFixture field(FrameFixture search, int index) {
        return new JTextComponentFixture(robot, findIn(search.target(), JTextField.class, f -> f.isShowing() && f.isEditable()).get(index));
    }

    private static void selectTab(FrameFixture search, String title) {
        JTabbedPane tabs = findIn(search.target(), JTabbedPane.class, t -> t.isShowing() && t.indexOfTab(title) >= 0).get(0);
        new JTabbedPaneFixture(robot, tabs).selectTab(title);
    }

    private static JTable table(FrameFixture search) {
        return findIn(search.target(), JTable.class, JTable::isShowing).get(0);
    }

    private static java.util.List<String> resultRows(FrameFixture search) {
        java.util.List<JTable> tables = findIn(search.target(), JTable.class, JTable::isShowing);
        if (tables.isEmpty()) {
            return java.util.List.of();
        }
        return GuiActionRunner.execute(() -> {
            JTable table = tables.get(0);
            java.util.List<String> rows = new java.util.ArrayList<>();
            for (int row = 0; row < table.getRowCount(); row++) {
                StringBuilder sb = new StringBuilder();
                for (int column = 0; column < table.getColumnCount(); column++) {
                    sb.append(table.getValueAt(row, column)).append(' ');
                }
                rows.add(sb.toString());
            }
            return rows;
        });
    }

    private static void selectResult(FrameFixture search, int row) {
        new JTableFixture(robot, table(search)).selectRows(row);
    }

    /** The text of the dependency snippet shown in the tab with the given title. */
    private static String snippet(FrameFixture search, String tabTitle) {
        selectTab(search, tabTitle);
        return GuiActionRunner.execute(() -> findIn(search.target(), javax.swing.text.JTextComponent.class, t -> t.isShowing() && !(t instanceof JTextField))
                .stream().map(javax.swing.text.JTextComponent::getText).filter(t -> !t.isEmpty()).findFirst().orElse(""));
    }
}
