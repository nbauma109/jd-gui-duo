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
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Log files and pasted stack traces link their frames to the classes of the open archives; so do the service registrations. */
class LogAndServicePagesGuiTest extends GuiTestBase {

    private static final AtomicInteger FILES = new AtomicInteger();

    private static final String STACK_TRACE = String.join("\n",
            "Exception in thread \"main\" java.lang.IllegalStateException: boom",
            "\tat demo.Greeter.greet(Greeter.java:11)",
            "\tat demo.Main.main(Main.java:7)",
            "\tat java.base/java.lang.Thread.run(Thread.java:1474)",
            "\tat demo.Shape.area(Native Method)",
            "Caused by: java.io.IOException: nested",
            "\tat nowhere.Missing.call(Missing.java:1)",
            "");

    private Path logFile() throws IOException {
        return Files.writeString(app.workDir().resolve("trace-" + FILES.incrementAndGet() + ".log"), STACK_TRACE);
    }

    @Test
    void aLogFileShowsItsTextAndLinksItsFramesToTheOpenClasses() throws IOException {
        openFixtureJar();
        waitUntil("the indexes are built", () -> isMenuEnabled("Search", "Search..."));
        Path log = logFile();

        openLog(log);
        assertThat(FilePagesGuiTest.pageText()).contains("java.lang.IllegalStateException: boom").contains("at demo.Greeter.greet(Greeter.java:11)");
        clickOn("demo.Greeter.greet");

        waitUntil("the class is shown", () -> window.target().getTitle().startsWith("Greeter.class"));
        assertThat(sourceText()).contains("public class Greeter implements Shape");
        // the frame names line 11: the source is shown at that line
        assertThat(lineOfTheCaret()).contains("Hello, ");
    }

    @Test
    void aFrameOfAnUnknownTypeIsNotALink() throws IOException {
        openFixtureJar();
        waitUntil("the indexes are built", () -> isMenuEnabled("Search", "Search..."));
        Path log = logFile();
        openLog(log);
        String title = window.target().getTitle();

        clickOn("nowhere.Missing.call");
        clickOn("java.base/java.lang.Thread.run");
        robot.waitForIdle();

        assertThat(window.target().getTitle()).isEqualTo(title);
    }

    @Test
    void aNativeMethodFrameOpensTheMethodOfTheType() throws IOException {
        openFixtureJar();
        waitUntil("the indexes are built", () -> isMenuEnabled("Search", "Search..."));
        openLog(logFile());

        clickOn("demo.Shape.area");

        waitUntil("the interface is shown", () -> window.target().getTitle().startsWith("Shape.class"));
        assertThat(sourceText()).contains("public interface Shape");
    }

    @Test
    void pastingTextOpensItAsALogPage() {
        openFixtureJar();
        waitUntil("the indexes are built", () -> isMenuEnabled("Search", "Search..."));
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(STACK_TRACE), null);

        clickMenu("Edit", "Paste Log");

        waitUntil("the pasted text is shown", () -> mainTabTitles().stream().anyMatch(title -> title.startsWith("clipboard-") && title.endsWith(".log")));
        assertThat(FilePagesGuiTest.pageText()).contains("Caused by: java.io.IOException: nested");
    }

    @Test
    void serviceRegistrationListsOneTypePerLineAndLinksThem() throws IOException {
        openFile(FixtureArchives.createServicesJar(app.workDir(), app.fixtureClasses()));
        waitUntil("the indexes are built", () -> isMenuEnabled("Search", "Search..."));
        FilePagesGuiTest.openEntry("demo.Shape");
        assertThat(FilePagesGuiTest.pageText()).contains("demo.Greeter");

        clickOn("demo.Greeter");

        waitUntil("the class is shown", () -> window.target().getTitle().startsWith("Greeter.class"));
        assertThat(sourceText()).contains("public class Greeter implements Shape");
    }

    // -------------------------------------------------------------------

    /** Opens a log file with File > Open File...; the window title keeps its name, so the tab is what shows that it is open. */
    private static void openLog(Path log) {
        clickMenu("File", "Open File...");
        window.fileChooser().selectFile(log.toFile()).approve();
        String name = log.getFileName().toString();
        waitUntil("the log " + name + " is opened", () -> mainTabTitles().contains(name));
        robot.waitForIdle();
    }

    /** Clicks, without any modifier, in the middle of the first occurrence of the text in the page that is shown. */
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
        robot.moveMouse(area, point);
        robot.click(area, point, MouseButton.LEFT_BUTTON, 1);
    }

    /** The text of the line of the source in which the caret is. */
    private static String lineOfTheCaret() {
        return GuiActionRunner.execute(() -> {
            RSyntaxTextArea area = (RSyntaxTextArea) sourceArea();
            try {
                int line = area.getLineOfOffset(area.getCaretPosition());
                return area.getText(area.getLineStartOffset(line), area.getLineEndOffset(line) - area.getLineStartOffset(line));
            } catch (javax.swing.text.BadLocationException e) {
                throw new IllegalStateException(e);
            }
        });
    }
}
