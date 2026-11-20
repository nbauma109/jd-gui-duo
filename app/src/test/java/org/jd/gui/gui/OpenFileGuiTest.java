/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.gui;

import org.junit.jupiter.api.Test;

import java.awt.event.KeyEvent;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OpenFileGuiTest extends GuiTestBase {

    @Test
    void opensAJarWithTheFileMenuAndShowsItsContent() {
        openFixtureJar();

        assertThat(window.target().getTitle()).isEqualTo("fixture.jar - Java Decompiler");
        assertThat(rows(fileTree().target())).isNotEmpty();
        assertThat(isMenuEnabled("File", "Close")).isTrue();
        assertThat(isMenuEnabled("Navigation", "Open Type...")).isTrue();
        assertThat(isMenuEnabled("Search", "Search...")).isTrue();
    }

    @Test
    void jarTreeListsPackageAndTypes() {
        openFixtureJar();
        org.assertj.swing.fixture.JTreeFixture tree = fileTree();
        expandAll(tree);

        assertThat(rows(tree.target())).contains("demo", "Greeter", "Shape");
    }

    @Test
    void closeMenuReturnsToTheWelcomeScreen() {
        openFixtureJar();

        clickMenu("File", "Close");

        waitUntil("the tab is closed", () -> window.target().getTitle().equals("Java Decompiler"));
        window.label(org.assertj.swing.core.matcher.JLabelMatcher.withText("No files are open")).requireVisible();
        assertThat(isMenuEnabled("File", "Close")).isFalse();
    }

    @Test
    void cancellingTheFileChooserOpensNothing() {
        clickMenu("File", "Open File...");
        window.fileChooser().cancel();

        robot.waitForIdle();
        assertThat(window.target().getTitle()).isEqualTo("Java Decompiler");
    }

    @Test
    void openedFileIsListedInTheRecentFilesMenu() {
        openFixtureJar();

        recentFileItem(app.fixtureJar()).requireVisible();
        robot.pressAndReleaseKey(KeyEvent.VK_ESCAPE);
        robot.pressAndReleaseKey(KeyEvent.VK_ESCAPE);
    }

    @Test
    void reopeningAnOpenFileFromTheRecentFilesMenuDoesNotDuplicateItsTab() throws Exception {
        openFixtureJar();
        Path other = app.workDir().resolve("other.jar");
        Files.copy(app.fixtureJar(), other, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        openFile(other);
        assertThat(window.target().getTitle()).startsWith("other.jar");

        recentFileItem(app.fixtureJar()).click();

        robot.waitForIdle();
        assertThat(mainTabTitles()).containsExactlyInAnyOrder("fixture.jar", "other.jar");
    }

    @Test
    void openFileShortcutShowsTheFileChooser() {
        pressShortcut(KeyEvent.VK_O);

        window.fileChooser().requireVisible();
        window.fileChooser().cancel();
    }

    @Test
    void toolbarOpenButtonShowsTheFileChooser() {
        window.button(new org.assertj.swing.core.GenericTypeMatcher<org.jd.gui.view.component.IconButton>(org.jd.gui.view.component.IconButton.class) {
            @Override
            protected boolean isMatching(org.jd.gui.view.component.IconButton b) {
                return b.isShowing() && "Open File...".equals(b.getText());
            }
        }).click();

        window.fileChooser().requireVisible();
        window.fileChooser().cancel();
    }

    @Test
    void opensAStandaloneClassFile() {
        openFile(app.fixtureClasses().resolve("demo").resolve("Greeter.class"));

        waitUntil("the decompiled class is displayed", () -> sourceText().contains("public class Greeter"));
    }

    @Test
    void opensAJavaSourceFile() throws Exception {
        Path source = app.workDir().resolve("Hello.java");
        Files.writeString(source, "public class Hello {\n    public static void main(String[] args) {\n        System.out.println(\"hi\");\n    }\n}\n");

        openFile(source);

        waitUntil("the source is displayed", () -> sourceText().contains("public class Hello"));
    }

    @Test
    void clickingATabSelectsTheCorrespondingFile() throws Exception {
        openFixtureJar();
        Path other = app.workDir().resolve("second.jar");
        Files.copy(app.fixtureJar(), other, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        openFile(other);
        assertThat(mainTabTitles()).containsExactly("fixture.jar", "second.jar");

        org.assertj.swing.fixture.JTabbedPaneFixture tabs = new org.assertj.swing.fixture.JTabbedPaneFixture(robot,
                findAll(javax.swing.JTabbedPane.class, tp -> tp.getClass().getName().contains("TabbedPanel")).get(0));
        tabs.selectTab("fixture.jar");

        waitUntil("the first file is selected", () -> window.target().getTitle().startsWith("fixture.jar"));
    }
}
