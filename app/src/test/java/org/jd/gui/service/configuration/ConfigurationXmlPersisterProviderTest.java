/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.configuration;

import org.jd.gui.Constants;
import org.jd.gui.model.configuration.Configuration;
import org.jd.gui.util.decompiler.GuiPreferences;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Toolkit;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The configuration file of the user: its defaults, what is saved and read back, and what is ignored. */
class ConfigurationXmlPersisterProviderTest {

    @TempDir
    Path dir;

    private Path file;

    private ConfigurationXmlPersisterProvider persister() {
        file = dir.resolve("jd-gui.cfg");
        return new ConfigurationXmlPersisterProvider() {
            @Override
            protected File getFile() {
                return file.toFile();
            }
        };
    }

    private static void needsAScreen() {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "the default window size comes from the screen");
    }

    private Path existingFile(String name) throws IOException {
        return Files.write(dir.resolve(name), new byte[] {1});
    }

    @Test
    void withoutAFileTheDefaultsAreGiven() {
        needsAScreen();
        Configuration config = persister().load();

        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        assertThat(config.getMainWindowSize().width).isEqualTo(Math.min(screen.width, Constants.DEFAULT_WIDTH));
        assertThat(config.getMainWindowSize().height).isEqualTo(Math.min(screen.height, Constants.DEFAULT_HEIGHT));
        // the window is centered
        assertThat(config.getMainWindowLocation().x).isEqualTo((screen.width - config.getMainWindowSize().width) / 2);
        assertThat(config.isMainWindowMaximize()).isFalse();
        assertThat(config.getRecentLoadDirectory()).isEqualTo(new File(System.getProperty("user.dir")));
        assertThat(config.getRecentSaveDirectory()).isEqualTo(new File(System.getProperty("user.dir")));
        assertThat(config.getRecentFiles()).isEmpty();
        assertThat(config.getPreferences()).containsEntry(GuiPreferences.ERROR_BACKGROUND_COLOR, "0xFF6666").containsKeys(jd.core.preferences.Preferences.JD_CORE_VERSION);
        assertThat(file).doesNotExist();
    }

    @Test
    void whatIsSavedIsReadBack() throws IOException {
        needsAScreen();
        ConfigurationXmlPersisterProvider persister = persister();
        Path recent = existingFile("recent.jar");
        Path open = existingFile("open.jar");
        Path loadDir = Files.createDirectory(dir.resolve("load"));
        Path saveDir = Files.createDirectory(dir.resolve("save"));
        Configuration config = new Configuration();
        config.setMainWindowLocation(new Point(5, 6));
        config.setMainWindowSize(new Dimension(300, 200));
        config.setMainWindowMaximize(true);
        config.setLookAndFeel("com.formdev.flatlaf.FlatDarkLaf");
        config.setRecentFiles(List.of(recent.toFile()));
        config.setOpenFiles(List.of(open.toFile()));
        config.setRecentLoadDirectory(loadDir.toFile());
        config.setRecentSaveDirectory(saveDir.toFile());
        config.getPreferences().put("Some.preference", "a value");
        config.getPreferences().put("Indentation", "    ");

        persister.save(config);
        Configuration read = persister.load();

        assertThat(read.getMainWindowLocation()).isEqualTo(new Point(5, 6));
        assertThat(read.getMainWindowSize()).isEqualTo(new Dimension(300, 200));
        assertThat(read.isMainWindowMaximize()).isTrue();
        assertThat(read.getLookAndFeel()).isEqualTo("com.formdev.flatlaf.FlatDarkLaf");
        assertThat(read.getRecentFiles()).containsExactly(recent.toFile());
        assertThat(read.getOpenFiles()).containsExactly(open.toFile());
        assertThat(read.getRecentLoadDirectory()).isEqualTo(loadDir.toFile());
        assertThat(read.getRecentSaveDirectory()).isEqualTo(saveDir.toFile());
        // a value is trimmed, except when it is only blanks (an indentation)
        assertThat(read.getPreferences()).containsEntry("Some.preference", "a value").containsEntry("Indentation", "    ");
    }

    @Test
    void theFilesThatNoLongerExistAreDroppedAndTheRecentFilesAreLimited() throws IOException {
        ConfigurationXmlPersisterProvider persister = persister();
        List<File> recent = new ArrayList<>();
        for (int i = 0; i < Constants.MAX_RECENT_FILES + 3; i++) {
            recent.add(existingFile("recent" + i + ".jar").toFile());
        }
        recent.add(dir.resolve("gone.jar").toFile());
        Configuration config = new Configuration();
        config.setMainWindowLocation(new Point(0, 0));
        config.setMainWindowSize(new Dimension(100, 100));
        config.setRecentFiles(recent);
        config.setOpenFiles(List.of(dir.resolve("gone-too.jar").toFile()));
        config.setRecentLoadDirectory(dir.resolve("no-such-directory").toFile());
        config.setRecentSaveDirectory(dir.resolve("no-such-directory").toFile());

        persister.save(config);
        Configuration read = persister.load();

        assertThat(read.getRecentFiles()).hasSize(Constants.MAX_RECENT_FILES).doesNotContain(dir.resolve("gone.jar").toFile());
        assertThat(read.getOpenFiles()).isEmpty();
        assertThat(read.getRecentLoadDirectory()).isEqualTo(new File(System.getProperty("user.dir")));
    }

    @Test
    void aWindowOutsideOfTheScreenIsReplacedByTheDefaultOne() throws IOException {
        needsAScreen();
        ConfigurationXmlPersisterProvider persister = persister();
        // a valid configuration (the directories are needed to save it), whose window is outside of the screen
        Configuration config = persister.load();
        config.setMainWindowLocation(new Point(-50, 10));
        config.setMainWindowSize(new Dimension(300, 200));
        config.setMainWindowMaximize(true);
        persister.save(config);
        assertThat(Files.readString(file)).contains("x=\"-50\"").contains("</configuration>");

        Configuration read = persister.load();

        // the bounds are refused, not the file: the rest of the configuration is read
        assertThat(read.getMainWindowLocation()).isNotEqualTo(new Point(-50, 10));
        assertThat(read.getMainWindowSize()).isNotEqualTo(new Dimension(300, 200));
        assertThat(read.isMainWindowMaximize()).isFalse();
        assertThat(read.getPreferences()).containsKey(GuiPreferences.ERROR_BACKGROUND_COLOR);
    }

    @Test
    void aFileThatIsNotWellFormedLeavesTheDefaults() throws IOException {
        ConfigurationXmlPersisterProvider persister = persister();
        Files.writeString(file, "<configuration><gui><mainWindow><location x=\"1\"", StandardCharsets.UTF_8);

        Configuration read = persister.load();

        assertThat(read.getPreferences()).containsKey(GuiPreferences.ERROR_BACKGROUND_COLOR);
        assertThat(read.getRecentFiles()).isEmpty();
    }

    @Test
    void thePreferencesThatTheFileDoesNotGiveGetTheirDefault() throws IOException {
        ConfigurationXmlPersisterProvider persister = persister();
        Files.writeString(file, "<configuration><preferences><" + GuiPreferences.ERROR_BACKGROUND_COLOR + ">0x123456</" + GuiPreferences.ERROR_BACKGROUND_COLOR + "></preferences></configuration>", StandardCharsets.UTF_8);

        Configuration read = persister.load();

        assertThat(read.getPreferences()).containsEntry(GuiPreferences.ERROR_BACKGROUND_COLOR, "0x123456");
        assertThat(read.getPreferences().keySet()).hasSizeGreaterThan(2);
    }

    @Test
    void theVersionOfJdCoreComesFromTheManifestOrIsSnapshot() {
        assertThat(persister().getJdCoreVersion()).isNotBlank();
    }

    @Test
    void theDefaultFileIsTheOneOfTheUser() {
        assertThat(new ConfigurationXmlPersisterProvider().getFile()).isEqualTo(ConfigurationXmlPersisterProvider.FILE);
    }
}
