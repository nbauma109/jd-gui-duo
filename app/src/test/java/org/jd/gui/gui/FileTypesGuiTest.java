/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.gui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Every kind of file the application has a page for, opened from one archive. */
class FileTypesGuiTest extends GuiTestBase {

    @Test
    void everyTextAndSourceTypeShowsItsContent() throws IOException {
        Path archive = FixtureArchives.createAssorted(app.workDir(), app.fixtureJar());
        openFile(archive);

        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, String> entry : FixtureArchives.assortedFragments().entrySet()) {
            FilePagesGuiTest.openEntry(entry.getKey());
            String text = FilePagesGuiTest.pageText();
            if (!text.contains(entry.getValue())) {
                problems.add(entry.getKey() + " shows: " + text.substring(0, Math.min(120, text.length())));
            }
        }

        assertThat(problems).isEmpty();
    }

    @Test
    void nestedArchiveIsExploredInPlace() throws IOException {
        openFile(FixtureArchives.createAssorted(app.workDir(), app.fixtureJar()));
        expandAll(fileTree());

        assertThat(rows(fileTree().target())).contains("lib.jar", "Greeter.class", "Shape.class");

        FilePagesGuiTest.openEntry("Greeter.class");
        assertThat(FilePagesGuiTest.pageText()).contains("public class Greeter implements Shape");
    }

    @Test
    void imageIsDisplayedByItsOwnPage() throws IOException {
        openFile(FixtureArchives.createAssorted(app.workDir(), app.fixtureJar()));
        org.assertj.swing.fixture.JTreeFixture tree = fileTree();
        expandAll(tree);
        int row = rowOf(tree.target(), "icon.png");
        assertThat(row).as("rows " + rows(tree.target())).isGreaterThanOrEqualTo(0);

        tree.doubleClickRow(row);

        waitUntil("the image page is shown", () -> window.target().getTitle().startsWith("icon.png"));
        assertThat(window.target().getTitle()).startsWith("icon.png");
    }
}
