/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.gui;

import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.fixture.JTreeFixture;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** The manifest, the module descriptor and the web.xml of an archive are shown by pages of their own. */
class FilePagesGuiTest extends GuiTestBase {

    @Test
    void manifestPageShowsTheMainAndTheNamedSections() throws IOException {
        openFile(FixtureArchives.createModularJar(app.workDir()));

        openEntry("MANIFEST.MF");

        assertThat(pageText())
                .contains("Main-Class: demo.impl.ServiceImpl")
                .contains("Class-Path: lib/one.jar lib/two.jar")
                .contains("Implementation-Title: Fixture module")
                .contains("Automatic-Module-Name: fixture.mod")
                .contains("Name: demo/api/")
                .contains("Sealed: true");
    }

    @Test
    void moduleInfoPageShowsTheDirectivesOfTheDescriptor() throws IOException {
        openFile(FixtureArchives.createModularJar(app.workDir()));

        openEntry("module-info");

        assertThat(pageText())
                .contains("module fixture.mod")
                .contains("requires java.logging")
                .contains("requires transitive java.sql")
                .contains("exports demo.api")
                .contains("opens demo.impl to java.logging")
                .contains("uses demo.api.Service")
                .contains("provides demo.api.Service with demo.impl.ServiceImpl");
    }

    @Test
    void webXmlPageShowsTheDeploymentDescriptor() throws IOException {
        openFile(FixtureArchives.createWar(app.workDir()));

        openEntry("web.xml");

        assertThat(pageText())
                .contains("<display-name>Fixture application</display-name>")
                .contains("demo.HelloServlet")
                .contains("<url-pattern>/hello</url-pattern>")
                .contains("demo.AuditFilter")
                .contains("demo.StartupListener");
    }

    @Test
    void manifestOfAWarIsShownToo() throws IOException {
        openFile(FixtureArchives.createWar(app.workDir()));

        openEntry("MANIFEST.MF");

        assertThat(pageText()).contains("Implementation-Title: Fixture web application");
    }

    /** Double-clicks the tree node with the given text and waits until a text page shows something. */
    static void openEntry(String text) {
        JTreeFixture tree = fileTree();
        expandAll(tree);
        int row = rowOf(tree.target(), text);
        assertThat(row).as("row of " + text + " in " + rows(tree.target())).isGreaterThanOrEqualTo(0);
        tree.doubleClickRow(row);
        waitUntil("the page of " + text + " is displayed", () -> !pageText().isEmpty());
    }

    /** Text of the text page currently shown. */
    static String pageText() {
        return GuiActionRunner.execute(() -> findAll(RSyntaxTextArea.class, RSyntaxTextArea::isShowing).stream()
                .map(RSyntaxTextArea::getText).filter(t -> !t.isEmpty()).findFirst().orElse(""));
    }
}
