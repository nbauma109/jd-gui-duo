/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.gui;

import org.assertj.swing.core.MouseButton;
import org.assertj.swing.edt.GuiActionRunner;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/** The deployment descriptors name classes of the archive: the names are links that open the class. */
class XmlPagesGuiTest extends GuiTestBase {

    @Test
    void ejbJarDescriptorLinksToTheClassesItNames() throws IOException {
        openFile(FixtureArchives.createEjbJar(app.workDir(), app.fixtureClasses()));
        waitUntil("the indexes are built", () -> isMenuEnabled("Search", "Search..."));
        FilePagesGuiTest.openEntry("ejb-jar.xml");
        assertThat(FilePagesGuiTest.pageText()).contains("<ejb-name>GreeterBean</ejb-name>").contains("<ejb-class>demo.Greeter</ejb-class>");

        clickOn("demo.Greeter");

        waitUntil("the class is shown", () -> window.target().getTitle().startsWith("Greeter.class"));
        assertThat(sourceText()).contains("public class Greeter implements Shape");
    }

    @Test
    void ejbJarDescriptorLinksToTheInterfaceItExposes() throws IOException {
        openFile(FixtureArchives.createEjbJar(app.workDir(), app.fixtureClasses()));
        waitUntil("the indexes are built", () -> isMenuEnabled("Search", "Search..."));
        FilePagesGuiTest.openEntry("ejb-jar.xml");

        clickOn("demo.Shape");

        waitUntil("the interface is shown", () -> window.target().getTitle().startsWith("Shape.class"));
        assertThat(sourceText()).contains("public interface Shape");
    }

    @Test
    void webDescriptorLinksToTheServletClass() throws IOException {
        openFile(FixtureArchives.createWarWithClasses(app.workDir(), app.fixtureClasses()));
        waitUntil("the indexes are built", () -> isMenuEnabled("Search", "Search..."));
        FilePagesGuiTest.openEntry("web.xml");
        assertThat(FilePagesGuiTest.pageText()).contains("<servlet-class>demo.Greeter</servlet-class>");

        clickOn("demo.Greeter");

        waitUntil("the class is shown", () -> window.target().getTitle().startsWith("Greeter.class"));
        assertThat(sourceText()).contains("public class Greeter implements Shape");
    }

    @Test
    void manifestMainClassLinksToTheClass() throws IOException {
        openFile(FixtureArchives.createManifestJar(app.workDir(), app.fixtureClasses()));
        waitUntil("the indexes are built", () -> isMenuEnabled("Search", "Search..."));
        FilePagesGuiTest.openEntry("MANIFEST.MF");
        assertThat(FilePagesGuiTest.pageText()).contains("Main-Class: demo.Main").contains("Premain-Class: org.example.agents");

        clickOn("demo.Main");

        waitUntil("the class is shown", () -> window.target().getTitle().startsWith("Main.class"));
        assertThat(sourceText()).contains("public class Main");
    }

    @Test
    void manifestCanBeBeautified() throws IOException {
        openFile(FixtureArchives.createManifestJar(app.workDir(), app.fixtureClasses()));
        FilePagesGuiTest.openEntry("MANIFEST.MF");
        String raw = FilePagesGuiTest.pageText();
        javax.swing.JCheckBox beautify = findAll(javax.swing.JCheckBox.class, c -> c.isShowing() && "Beautify".equals(c.getText())).get(0);
        assertThat(raw).startsWith("Manifest-Version: 1.0").contains("\n name.that.the.manifest.wraps"); // wrapped, in the order of the file

        new org.assertj.swing.fixture.JCheckBoxFixture(robot, beautify).check();
        waitUntil("the text is beautified", () -> !FilePagesGuiTest.pageText().equals(raw));
        // the headers are sorted and the wrapped values are put back on one line
        assertThat(FilePagesGuiTest.pageText()).startsWith("Created-By: the GUI tests")
                .contains("Premain-Class: org.example.agents.with.a.deliberately.very.long.package.name.that.the.manifest.wraps.LongNamedJavaAgent");

        new org.assertj.swing.fixture.JCheckBoxFixture(robot, beautify).uncheck();
        waitUntil("the raw text is back", () -> FilePagesGuiTest.pageText().equals(raw));
        assertThat(FilePagesGuiTest.pageText()).isEqualTo(raw);
    }

    /** Clicks (without any modifier) in the middle of the first occurrence of the text in the page that is shown. */
    private static void clickOn(String text) {
        RSyntaxTextArea area = findAll(RSyntaxTextArea.class, RSyntaxTextArea::isShowing).stream()
                .filter(a -> GuiActionRunner.execute(() -> a.getText().contains(text))).findFirst().orElseThrow();
        Point point = GuiActionRunner.execute(() -> {
            try {
                int offset = area.getText().indexOf(text) + text.length() / 2;
                java.awt.Rectangle r = area.modelToView2D(offset).getBounds();
                return new Point(r.x + 1, r.y + r.height / 2);
            } catch (javax.swing.text.BadLocationException e) {
                throw new IllegalStateException(e);
            }
        });
        robot.moveMouse(area, point); // the page highlights a link when the mouse is over it
        robot.click(area, point, MouseButton.LEFT_BUTTON, 1);
    }
}
