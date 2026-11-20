/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.gui;

import org.assertj.swing.core.BasicRobot;
import org.assertj.swing.core.GenericTypeMatcher;
import org.assertj.swing.core.Robot;
import org.assertj.swing.fixture.FrameFixture;
import org.assertj.swing.timing.Pause;
import org.jd.gui.App;

import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import javax.swing.JFrame;

/**
 * Starts the real application once per JVM, on the real desktop, and gives the tests a robot
 * that drives it with genuine OS-level mouse and keyboard events (no headless tricks).
 * <p>
 * The user configuration is redirected to a throw-away file so the tests never read or modify
 * the developer's real {@code jd-gui.cfg}.
 */
public final class GuiApp {

    private static final String CONFIG_PROPERTY = "jd-gui.cfg";
    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(60);

    private static GuiApp instance;

    private final Path workDir;
    private final Path fixtureJar;
    private final Robot robot;
    private final FrameFixture mainWindow;

    private GuiApp(Path workDir, Path fixtureJar, Robot robot, FrameFixture mainWindow) {
        this.workDir = workDir;
        this.fixtureJar = fixtureJar;
        this.robot = robot;
        this.mainWindow = mainWindow;
    }

    /** Returns the running application, starting it on first use. */
    public static synchronized GuiApp get() {
        if (instance == null) {
            instance = start();
        }
        return instance;
    }

    private static GuiApp start() {
        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException("GUI tests need a real desktop session: java.awt.headless must be false and a display must be available");
        }
        try {
            Path workDir = Files.createTempDirectory("jd-gui-duo-gui-test");
            Path config = workDir.resolve("jd-gui.cfg");
            Files.writeString(config, """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <configuration>
                      <gui>
                        <lookAndFeel>com.formdev.flatlaf.FlatLightLaf</lookAndFeel>
                      </gui>
                    </configuration>
                    """.stripLeading(), StandardCharsets.UTF_8);
            System.setProperty(CONFIG_PROPERTY, config.toString());
            Path fixtureJar = FixtureJar.create(workDir);

            App.main(new String[0]);

            Robot robot = BasicRobot.robotWithCurrentAwtHierarchy();
            robot.settings().delayBetweenEvents(30);
            robot.settings().idleTimeout(250);
            FrameFixture window = new FrameFixture(robot, findMainFrame(robot));
            window.show();
            return new GuiApp(workDir, fixtureJar, robot, window);
        } catch (IOException e) {
            throw new IllegalStateException("Could not prepare the GUI test environment", e);
        }
    }

    private static JFrame findMainFrame(Robot robot) {
        long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            for (java.awt.Component component : robot.finder().findAll(new GenericTypeMatcher<JFrame>(JFrame.class) {
                @Override
                protected boolean isMatching(JFrame frame) {
                    return frame.isShowing() && frame.getJMenuBar() != null;
                }
            })) {
                return (JFrame) component;
            }
            Pause.pause(200);
        }
        throw new IllegalStateException("The main window did not appear within " + STARTUP_TIMEOUT.toSeconds() + "s");
    }

    public Robot robot() {
        return robot;
    }

    public FrameFixture mainWindow() {
        return mainWindow;
    }

    public Path workDir() {
        return workDir;
    }

    /** The compiled classes of the fixture jar (a runnable {@code demo.Main} among them). */
    public Path fixtureClasses() {
        return FixtureJar.classesDir(workDir);
    }

    /** A small jar containing {@code demo.Greeter}, {@code demo.Greeter$Nested} and {@code demo.Shape}. */
    public Path fixtureJar() {
        return fixtureJar;
    }
}
