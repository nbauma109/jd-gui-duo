/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.gui;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.text.JTextComponent;

/**
 * When a GUI test fails, saves a screenshot of the whole desktop and a text dump of the windows
 * to {@code target/gui-failures}, which the CI uploads as an artifact.
 */
public final class FailureArtifacts implements AfterTestExecutionCallback {

    @Override
    public void afterTestExecution(ExtensionContext context) throws Exception {
        if (context.getExecutionException().isEmpty() || GraphicsEnvironment.isHeadless()) {
            return;
        }
        Path dir = Path.of("target", "gui-failures");
        Files.createDirectories(dir);
        String name = context.getRequiredTestClass().getSimpleName() + "." + context.getRequiredTestMethod().getName();
        try {
            Rectangle screen = new Rectangle(java.awt.Toolkit.getDefaultToolkit().getScreenSize());
            BufferedImage image = new Robot().createScreenCapture(screen);
            ImageIO.write(image, "png", dir.resolve(name + ".png").toFile());
        } catch (Exception e) {
            Files.writeString(dir.resolve(name + ".screenshot-error.txt"), String.valueOf(e));
        }
        StringBuilder dump = new StringBuilder();
        for (Window window : Window.getWindows()) {
            if (window.isShowing()) {
                dump(window, "", dump);
            }
        }
        Files.writeString(dir.resolve(name + ".windows.txt"), dump.toString());
    }

    private static void dump(Component component, String indent, StringBuilder out) {
        if (!component.isShowing()) {
            return;
        }
        out.append(indent).append(component.getClass().getName());
        if (component instanceof Frame frame) {
            out.append(" title=").append(frame.getTitle());
        } else if (component instanceof Dialog dialog) {
            out.append(" title=").append(dialog.getTitle());
        } else if (component instanceof AbstractButton button) {
            out.append(" text=").append(button.getText()).append(" enabled=").append(button.isEnabled());
        } else if (component instanceof JLabel label) {
            out.append(" text=").append(label.getText());
        } else if (component instanceof JTextComponent text) {
            String content = text.getText();
            out.append(" text=").append(content.substring(0, Math.min(content.length(), 200)).replace('\n', ' '));
        }
        out.append('\n');
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                dump(child, indent + "  ", out);
            }
        }
    }
}
