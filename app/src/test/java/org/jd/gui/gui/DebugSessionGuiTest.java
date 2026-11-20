/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.gui;

import org.assertj.swing.core.GenericTypeMatcher;
import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.fixture.DialogFixture;
import org.assertj.swing.fixture.JTextComponentFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JFormattedTextField;
import javax.swing.JTree;

import static org.assertj.core.api.Assertions.assertThat;

/** A debugging session: a breakpoint in the decompiled source stops a real JVM, which is then stepped. */
class DebugSessionGuiTest extends GuiTestBase {

    private Process debuggee;

    @AfterEach
    void stopDebuggee() {
        if (isMenuEnabled("Debug", "Disconnect")) {
            clickMenu("Debug", "Disconnect");
            waitUntil("the debugger is disconnected", () -> !isMenuEnabled("Debug", "Disconnect"));
        }
        removeAllBreakpoints(); // the application is shared by the whole suite
        if (debuggee != null) {
            debuggee.destroyForcibly();
            debuggee = null;
        }
    }

    /** Opens Main, puts a breakpoint on the call in the loop, starts the debuggee and waits until it stops there. */
    private void stopAtTheBreakpoint() throws IOException {
        openFixtureJar();
        openType("Main");
        placeCaretOn("greeter.greet()");
        clickMenu("Debug", "Toggle Breakpoint");
        attach(startDebuggee());
        waitForLocation("demo.Main.main() line: 7");
    }

    @Test
    void breakpointStopsTheJvmAndShowsThreadsAndVariables() throws IOException {
        stopAtTheBreakpoint();

        assertThat(threadRows()).contains("Thread \"main\" (Suspended, at breakpoint)", "demo.Main.main() line: 7");
        assertThat(variableRows()).anyMatch(row -> row.startsWith("args")).anyMatch(row -> row.startsWith("greeter") && row.contains("Greeter"));
        assertThat(isMenuEnabled("Debug", "Resume")).isTrue();
        assertThat(isMenuEnabled("Debug", "Step Over")).isTrue();
        assertThat(isMenuEnabled("Debug", "Step Into")).isTrue();
        assertThat(isMenuEnabled("Debug", "Step Return")).isTrue();
    }

    @Test
    void stepOverMovesToTheNextLineAndBack() throws IOException {
        stopAtTheBreakpoint();

        clickMenu("Debug", "Step Over");
        waitForLocation("demo.Main.main() line: 8");
        clickMenu("Debug", "Step Over");
        waitForLocation("demo.Main.main() line: 7");

        assertThat(threadRows()).contains("demo.Main.main() line: 7");
    }

    @Test
    void stepIntoEntersTheCalleeAndStepReturnLeavesIt() throws IOException {
        stopAtTheBreakpoint();

        clickMenu("Debug", "Step Into");
        waitUntil("the callee is entered", () -> threadRows().stream().anyMatch(row -> row.startsWith("demo.Greeter.greet()")));
        assertThat(threadRows()).anyMatch(row -> row.startsWith("demo.Main.main()"));

        clickMenu("Debug", "Step Return");
        waitUntil("the caller is shown again", () -> threadRows().stream().noneMatch(row -> row.startsWith("demo.Greeter.greet()"))
                && threadRows().stream().anyMatch(row -> row.startsWith("demo.Main.main() line:")));
        assertThat(threadRows()).noneMatch(row -> row.startsWith("demo.Greeter.greet()"));
    }

    @Test
    void resumeRunsUntilTheBreakpointIsHitAgain() throws IOException {
        stopAtTheBreakpoint();

        clickMenu("Debug", "Resume");

        // the loop reaches the breakpoint again within milliseconds
        waitForLocation("demo.Main.main() line: 7");
        assertThat(threadRows()).contains("Thread \"main\" (Suspended, at breakpoint)");
    }

    @Test
    void skippingAllBreakpointsLetsTheJvmRun() throws IOException {
        stopAtTheBreakpoint();
        selectDebugTab("Breakpoints");
        breakpointButton("Skip All Breakpoints").click();

        clickMenu("Debug", "Resume");

        selectDebugTab("Variables");
        waitUntil("the thread runs freely", () -> threadRows().contains("Thread \"main\" (Running)"));
        assertThat(isMenuEnabled("Debug", "Step Over")).isFalse();
        selectDebugTab("Breakpoints");
        breakpointButton("Skip All Breakpoints").click();
    }

    @Test
    void breakpointsTabListsTheLineBreakpointAndTheExceptionBreakpoint() throws IOException {
        stopAtTheBreakpoint();
        selectDebugTab("Breakpoints");

        breakpointButton("Add Java Exception Breakpoint...").click();
        confirm("Add Java Exception Breakpoint"); // nothing typed: refused
        dismissWarning("Enter a fully qualified class name.");
        typeText(dialog("Add Java Exception Breakpoint").textBox(), "java.lang.IllegalStateException");
        dialog("Add Java Exception Breakpoint").checkBox(checkBoxNamed("Caught")).uncheck();
        dialog("Add Java Exception Breakpoint").checkBox(checkBoxNamed("Uncaught")).uncheck();
        confirm("Add Java Exception Breakpoint"); // neither caught nor uncaught: refused
        dismissWarning("Select Caught, Uncaught or both.");
        dialog("Add Java Exception Breakpoint").checkBox(checkBoxNamed("Caught")).check();
        confirm("Add Java Exception Breakpoint");

        waitUntil("both breakpoints are listed", () -> breakpointRows().size() == 2);
        assertThat(breakpointRows()).anyMatch(row -> row.contains("IllegalStateException")).anyMatch(row -> row.contains("Main"));
    }

    @Test
    void watchExpressionsAreEvaluatedAtTheStop() throws IOException {
        stopAtTheBreakpoint();
        selectDebugTab("Expressions");

        addWatch("greeter.greet()");
        addWatch("1 +");

        waitUntil("the expressions are evaluated", () -> variableRows().size() >= 2);
        assertThat(variableRows()).anyMatch(row -> row.contains("greeter.greet()") && row.contains("Hello, debugger!"))
                .anyMatch(row -> row.startsWith("1 +"));
        breakpointButton("Remove all watch expressions").click();
        waitUntil("the expressions are removed", () -> variableRows().stream().noneMatch(row -> row.contains("greeter.greet()")));
        assertThat(variableRows()).containsExactly("Add new expression");
    }

    @Test
    void disconnectingEndsTheSession() throws IOException {
        stopAtTheBreakpoint();

        clickMenu("Debug", "Disconnect");

        waitUntil("the debugger is disconnected", () -> !isMenuEnabled("Debug", "Disconnect"));
        assertThat(isMenuEnabled("Debug", "Step Over")).isFalse();
        assertThat(isMenuEnabled("Debug", "Resume")).isFalse();
    }

    // -------------------------------------------------------------------

    private static void attach(int port) {
        clickMenu("Debug", "Remote Debug...");
        DialogFixture dialog = dialog("Remote Debug");
        typeText(dialog.textBox(new GenericTypeMatcher<javax.swing.JTextField>(javax.swing.JTextField.class) {
            @Override
            protected boolean isMatching(javax.swing.JTextField field) {
                return field.isShowing() && field.getClass() == javax.swing.JTextField.class;
            }
        }), "127.0.0.1");
        JFormattedTextField portField = (JFormattedTextField) dialog.spinner().target().getEditor().getComponent(0);
        JTextComponentFixture port_ = new JTextComponentFixture(robot, portField);
        port_.focus();
        typeText(port_, String.valueOf(port));
        button(dialog, "OK").click();
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
        GuiActionRunner.execute(() -> {
            javax.swing.text.JTextComponent area = sourceArea();
            int index = area.getText().indexOf(text);
            assertThat(index).isGreaterThanOrEqualTo(0);
            area.requestFocusInWindow();
            area.setCaretPosition(index);
        });
    }

    /** Rows of the thread tree (plain JTree of the Debug tab). */
    private static java.util.List<String> threadRows() {
        java.util.List<JTree> trees = findAll(JTree.class, t -> t.isShowing() && t.getClass() == JTree.class);
        return trees.isEmpty() ? java.util.List.of() : rows(trees.get(0));
    }

    private static java.util.List<String> variableRows() {
        java.util.List<JTree> trees = findAll(JTree.class, t -> t.isShowing() && t instanceof org.jd.gui.view.component.DebugValueTree);
        return trees.isEmpty() ? java.util.List.of() : rows(trees.get(0));
    }

    private static void waitForLocation(String location) {
        waitUntil("the JVM is stopped at " + location, () -> threadRows().contains(location));
    }

    private static void selectDebugTab(String title) {
        window.tabbedPane(new GenericTypeMatcher<javax.swing.JTabbedPane>(javax.swing.JTabbedPane.class) {
            @Override
            protected boolean isMatching(javax.swing.JTabbedPane pane) {
                return pane.isShowing() && pane.getTabCount() == 3 && "Breakpoints".equals(pane.getTitleAt(1));
            }
        }).selectTab(title);
    }

    /** A button of the debug panels, found by its tool tip (the Skip All toggle included). */
    private static org.assertj.swing.fixture.AbstractButtonFixture<?, ?> breakpointButton(String toolTip) {
        javax.swing.AbstractButton button = robot.finder().find(window.target(), new GenericTypeMatcher<javax.swing.AbstractButton>(javax.swing.AbstractButton.class) {
            @Override
            protected boolean isMatching(javax.swing.AbstractButton candidate) {
                return candidate.isShowing() && toolTip.equals(candidate.getToolTipText());
            }
        });
        return button instanceof javax.swing.JToggleButton toggle ? new org.assertj.swing.fixture.JToggleButtonFixture(robot, toggle)
                : new org.assertj.swing.fixture.JButtonFixture(robot, (javax.swing.JButton) button);
    }

    private static void removeAllBreakpoints() {
        selectDebugTab("Breakpoints");
        org.assertj.swing.fixture.AbstractButtonFixture<?, ?> removeAll = breakpointButton("Remove All Breakpoints");
        if (removeAll.target().isEnabled()) {
            removeAll.click();
        }
        waitUntil("no breakpoint is left", () -> breakpointRows().isEmpty());
    }

    /** Labels of the listed breakpoints. */
    private static java.util.List<String> breakpointRows() {
        java.util.List<javax.swing.JList> lists = findAll(javax.swing.JList.class, l -> l.isShowing() && l.getParent() != null && l.getParent().getParent() != null
                && l.getParent().getParent().getParent() instanceof org.jd.gui.view.component.BreakpointListPanel);
        if (lists.isEmpty()) {
            return java.util.List.of();
        }
        return GuiActionRunner.execute(() -> {
            javax.swing.JList<?> list = lists.get(0);
            java.util.List<String> labels = new java.util.ArrayList<>();
            for (int i = 0; i < list.getModel().getSize(); i++) {
                java.awt.Component renderer = ((javax.swing.ListCellRenderer) list.getCellRenderer()).getListCellRendererComponent(list, list.getModel().getElementAt(i), i, false, false);
                labels.add(renderer instanceof javax.swing.JComponent c && c.getToolTipText() != null ? c.getToolTipText() : String.valueOf(list.getModel().getElementAt(i)));
            }
            return labels;
        });
    }

    private static GenericTypeMatcher<javax.swing.JCheckBox> checkBoxNamed(String text) {
        return new GenericTypeMatcher<>(javax.swing.JCheckBox.class) {
            @Override
            protected boolean isMatching(javax.swing.JCheckBox box) {
                return box.isShowing() && text.equals(box.getText());
            }
        };
    }

    private static void addWatch(String expression) {
        button(window, "Add...").click();
        DialogFixture dialog = dialog("Add Watch Expression");
        typeText(dialog.textBox(), expression);
        button(dialog, "OK").click();
        waitUntil("the dialog is closed", () -> !isDialogShowing("Add Watch Expression"));
    }

    private static void confirm(String title) {
        button(dialog(title), "OK").click();
    }

    /** Dismisses the warning that a rejected entry shows (the entry dialog, which has a text field, comes back right after). */
    private static void dismissWarning(String message) {
        waitUntil("the warning '" + message + "' is shown", () -> exceptionDialogs(false));
        button(dialog("Add Java Exception Breakpoint"), "OK").click();
        waitUntil("the entry dialog is back", () -> exceptionDialogs(true));
    }

    /** Whether the showing "Add Java Exception Breakpoint" dialog is the entry form (with its text field) or the warning. */
    private static boolean exceptionDialogs(boolean entryForm) {
        return GuiActionRunner.execute(() -> {
            for (java.awt.Window w : java.awt.Window.getWindows()) {
                if (w.isShowing() && w instanceof javax.swing.JDialog d && "Add Java Exception Breakpoint".equals(d.getTitle())) {
                    return hasTextField(d) == entryForm;
                }
            }
            return false;
        });
    }

    private static boolean hasTextField(java.awt.Container container) {
        for (java.awt.Component c : container.getComponents()) {
            if (c instanceof javax.swing.JTextField || c instanceof java.awt.Container child && hasTextField(child)) {
                return true;
            }
        }
        return false;
    }
}
