/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.gui;
import org.assertj.swing.fixture.DialogFixture;
import org.assertj.swing.fixture.JTabbedPaneFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JFormattedTextField;
import javax.swing.JList;
import javax.swing.JTabbedPane;

import static org.assertj.core.api.Assertions.assertThat;

/** Remote debugging (JDWP): the dialog, a real attach to a JVM started by the test, and breakpoints. */
class RemoteDebugGuiTest extends GuiTestBase {

    private static final String TITLE = "Remote Debug";

    private Process debuggee;

    @AfterEach
    void stopDebuggee() {
        if (isMenuEnabled("Debug", "Disconnect")) {
            clickMenu("Debug", "Disconnect");
            waitUntil("the debugger is disconnected", () -> !isMenuEnabled("Debug", "Disconnect"));
        }
        if (debuggee != null) {
            debuggee.destroyForcibly();
            debuggee = null;
        }
    }

    @Test
    void remoteDebugDialogShowsTheHostAndPortAndCanBeCancelled() {
        clickMenu("Debug", "Remote Debug...");
        DialogFixture dialog = dialog(TITLE);

        dialog.label(org.assertj.swing.core.matcher.JLabelMatcher.withText("Host:")).requireVisible();
        // "localhost" by default; the other tests save 127.0.0.1 in the preferences
        assertThat(hostField(dialog).text()).isIn("localhost", "127.0.0.1");
        dialog.label(org.assertj.swing.core.matcher.JLabelMatcher.withText("Port:")).requireVisible();
        dialog.spinner().requireVisible();
        optionButton(dialog, "Cancel").click();

        waitUntil("the dialog is closed", () -> !isDialogShowing(TITLE));
        assertThat(isMenuEnabled("Debug", "Disconnect")).isFalse();
    }

    @Test
    void remoteDebugDialogOpensWithItsKeyboardShortcut() {
        pressShortcut(java.awt.event.KeyEvent.VK_D, java.awt.event.KeyEvent.VK_SHIFT);

        dialog(TITLE).requireVisible();
        assertThat(isDialogShowing(TITLE)).isTrue();
    }

    @Test
    void attachingToAClosedPortReportsAnError() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }

        attach(closedPort);

        DialogFixture error = dialog(TITLE);
        error.label(new org.assertj.swing.core.GenericTypeMatcher<javax.swing.JLabel>(javax.swing.JLabel.class) {
            @Override
            protected boolean isMatching(javax.swing.JLabel label) {
                return label.isShowing() && label.getText() != null && !label.getText().isBlank();
            }
        }).requireVisible();
        optionButton(error, "OK").click();
        waitUntil("the error is dismissed", () -> !isDialogShowing(TITLE));
        assertThat(isMenuEnabled("Debug", "Disconnect")).isFalse();
    }

    @Test
    void attachesToARunningJvmAndDisconnects() throws IOException {
        int port = startDebuggee();

        attach(port);

        waitUntil("the debugger is connected", () -> isMenuEnabled("Debug", "Disconnect"));
        clickMenu("Debug", "Disconnect");
        waitUntil("the debugger is disconnected", () -> !isMenuEnabled("Debug", "Disconnect"));
        assertThat(isMenuEnabled("Debug", "Disconnect")).isFalse();
    }

    @Test
    void attachingTwiceIsRefused() throws IOException {
        int port = startDebuggee();
        attach(port);
        waitUntil("the debugger is connected", () -> isMenuEnabled("Debug", "Disconnect"));

        clickMenu("Debug", "Remote Debug...");

        DialogFixture info = dialog(TITLE);
        optionButton(info, "OK").click();
        waitUntil("the message is dismissed", () -> !isDialogShowing(TITLE));
        assertThat(isDialogShowing(TITLE)).isFalse();
    }

    @Test
    void breakpointCanBeToggledOnASourceLineAndIsListed() {
        openFixtureJar();
        openType("Greeter");
        placeCaretOn("Hello, ");

        clickMenu("Debug", "Toggle Breakpoint");

        JTabbedPaneFixture tabs = window.tabbedPane(new org.assertj.swing.core.GenericTypeMatcher<JTabbedPane>(JTabbedPane.class) {
            @Override
            protected boolean isMatching(JTabbedPane pane) {
                return pane.isShowing() && pane.getTabCount() == 3 && "Breakpoints".equals(pane.getTitleAt(1));
            }
        });
        tabs.selectTab("Breakpoints");
        waitUntil("the breakpoint is listed", () -> breakpointCount() == 1);

        clickMenu("Debug", "Toggle Breakpoint");
        waitUntil("the breakpoint is removed", () -> breakpointCount() == 0);
    }

    // -------------------------------------------------------------------

    private static void attach(int port) {
        clickMenu("Debug", "Remote Debug...");
        DialogFixture dialog = dialog(TITLE);
        typeText(hostField(dialog), "127.0.0.1"); // the debuggee listens on the IPv4 loopback address
        JFormattedTextField portField = (JFormattedTextField) dialog.spinner().target().getEditor().getComponent(0);
        new org.assertj.swing.fixture.JTextComponentFixture(robot, portField).focus();
        typeText(new org.assertj.swing.fixture.JTextComponentFixture(robot, portField), String.valueOf(port));
        optionButton(dialog, "OK").click();
    }

    private static org.assertj.swing.fixture.JTextComponentFixture hostField(DialogFixture dialog) {
        return dialog.textBox(new org.assertj.swing.core.GenericTypeMatcher<javax.swing.JTextField>(javax.swing.JTextField.class) {
            @Override
            protected boolean isMatching(javax.swing.JTextField field) {
                return field.isShowing() && field.getClass() == javax.swing.JTextField.class;
            }
        });
    }

    /** The buttons of a JOptionPane dialog (the title of the dialog is the one of the pane). */
    private static org.assertj.swing.fixture.JButtonFixture optionButton(DialogFixture dialog, String text) {
        return button(dialog, text);
    }

    private int startDebuggee() throws IOException {
        ProcessBuilder builder = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=127.0.0.1:0",
                "-cp", app.fixtureClasses().toString(), "demo.Main");
        builder.redirectErrorStream(true);
        debuggee = builder.start();
        Pattern listening = Pattern.compile("Listening for transport dt_socket at address: (\\d+)");
        BufferedReader reader = new BufferedReader(new InputStreamReader(debuggee.getInputStream(), StandardCharsets.UTF_8));
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            String line = reader.readLine();
            if (line == null) {
                break;
            }
            Matcher matcher = listening.matcher(line);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        }
        throw new IllegalStateException("The JDWP debuggee did not start");
    }

    private static void placeCaretOn(String text) {
        org.assertj.swing.edt.GuiActionRunner.execute(() -> {
            javax.swing.text.JTextComponent area = sourceArea();
            int index = area.getText().indexOf(text);
            assertThat(index).isGreaterThanOrEqualTo(0);
            area.requestFocusInWindow();
            area.setCaretPosition(index);
        });
    }

    private static int breakpointCount() {
        java.util.List<JList> lists = findAll(JList.class, l -> l.isShowing() && l.getModel().getSize() >= 0
                && l.getParent() != null && l.getParent().getParent() != null
                && l.getParent().getParent().getParent() instanceof org.jd.gui.view.component.BreakpointListPanel);
        return lists.isEmpty() ? -1 : org.assertj.swing.edt.GuiActionRunner.execute(() -> lists.get(0).getModel().getSize());
    }
}
