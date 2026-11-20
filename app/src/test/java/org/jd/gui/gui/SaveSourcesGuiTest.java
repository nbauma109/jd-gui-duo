/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.gui;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;

class SaveSourcesGuiTest extends GuiTestBase {

    @BeforeEach
    void openGreeter() {
        openFixtureJar();
        openType("Greeter");
    }

    @Test
    void saveWritesTheDecompiledSourceOfTheCurrentTypeToTheChosenFile() throws IOException {
        Path target = app.workDir().resolve("saved-Greeter.java");
        Files.deleteIfExists(target);

        clickMenu("File", "Save");
        saveAs(target);

        waitUntil("the file is written", () -> Files.exists(target) && size(target) > 0);
        assertThat(Files.readString(target)).contains("public class Greeter implements Shape").contains("greet()");
    }

    @Test
    void saveCanBeCancelled() {
        Path target = app.workDir().resolve("never-written.java");

        clickMenu("File", "Save");
        window.fileChooser().cancel();

        robot.waitForIdle();
        assertThat(target).doesNotExist();
    }

    @Test
    void saveAllSourcesWritesAZipOfTheDecompiledSources() throws IOException {
        Path target = app.workDir().resolve("all-sources.zip");
        Files.deleteIfExists(target);

        clickMenu("File", "Save All Sources");
        saveAs(target);

        waitUntil("the archive is written", () -> Files.exists(target) && size(target) > 0 && isCompleteZip(target));
        try (ZipFile zip = new ZipFile(target.toFile())) {
            assertThat(zip.stream().map(e -> e.getName()).toList()).contains("demo/Greeter.java", "demo/Shape.java");
        }
    }

    private static long size(Path path) {
        try {
            return Files.size(path);
        } catch (IOException _) {
            return -1;
        }
    }

    private static boolean isCompleteZip(Path path) {
        try (ZipFile zip = new ZipFile(path.toFile())) {
            return zip.size() > 0;
        } catch (IOException _) {
            return false; // still being written
        }
    }
}
