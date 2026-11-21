/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.gui;

import org.assertj.swing.fixture.DialogFixture;
import org.assertj.swing.fixture.JButtonFixture;
import org.assertj.swing.fixture.JTextComponentFixture;
import org.jd.gui.ControllerAccess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import javax.swing.JTextField;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "Show GAVs" (what dropping jars on the window does): the jars are looked up in a Nexus repository (a local stub here) and the
 * result is a zip with a Maven pom, a Gradle build and a deploy script for the jars that the repository does not know.
 */
class GavGenerationGuiTest extends GuiTestBase {

    private LocalNexus nexus;

    @BeforeEach
    void pointToTheLocalNexus() throws IOException {
        nexus = new LocalNexus();
        clickMenu("Help", "Secured Preferences...");
        DialogFixture dialog = dialog("Secured Preferences");
        JTextComponentFixture url = new JTextComponentFixture(robot,
                findIn(dialog.target(), JTextField.class, f -> f.isShowing() && !(f instanceof javax.swing.JPasswordField)).get(0));
        url.setText(nexus.baseUrl());
        button(dialog, "Ok").click();
        waitUntil("the dialog is closed", () -> !isDialogShowing("Secured Preferences"));
    }

    @AfterEach
    void restoreThePreferences() {
        clickMenu("Help", "Secured Preferences...");
        DialogFixture dialog = dialog("Secured Preferences");
        new JButtonFixture(robot, findIn(dialog.target(), javax.swing.JButton.class, b -> b.isShowing() && "Clear".equals(b.getText())).get(0)).click();
        button(dialog, "Ok").click();
        waitUntil("the dialog is closed", () -> !isDialogShowing("Secured Preferences"));
        nexus.close();
    }

    private static Path jar(Path dir, String name, String packageName) throws IOException {
        Path jar = dir.resolve(name);
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new JarEntry(packageName.replace('.', '/') + "/Thing.class"));
            out.write(new byte[] {1, 2, 3});
            out.closeEntry();
        }
        return jar;
    }

    @Test
    void theGeneratedZipListsTheKnownAndTheUnknownJars() throws Exception {
        Path dir = Files.createTempDirectory("jd-gui-gav");
        // two jars with the same content: the stub knows the first lookup of a checksum and answers nothing the second time
        Path known = jar(dir, "known-1.0.jar", "org.demo.known");
        Path unknown = dir.resolve("vendor-lib-2.3.4.jar");
        Files.copy(known, unknown);
        Set<File> files = new LinkedHashSet<>();
        files.add(known.toFile());
        files.add(unknown.toFile());
        AtomicBoolean done = new AtomicBoolean();

        Thread worker = new Thread(() -> {
            ControllerAccess.controller().showGAVs(ControllerAccess.controller(), files, new HashMap<>(), () -> 0.0, p -> { }, () -> false);
            done.set(true);
        }, "gav-test");
        worker.start();

        waitUntil("the generated archive is opened", () -> done.get() && window.target().getTitle().contains(".zip"));
        expandAll(fileTree());
        java.util.List<String> rows = rows(fileTree().target());
        assertThat(rows).anyMatch(row -> row.contains("pom.xml")).anyMatch(row -> row.contains("build.gradle")).anyMatch(row -> row.contains("mvn_deploy.bat"));
        assertThat(nexus.requests()).anyMatch(r -> r.contains("sha1="));
    }
}
