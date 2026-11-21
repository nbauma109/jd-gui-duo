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

/** What the debugging tests have in common: a JVM to debug, the attach dialog and the readers of the debug panels. */
abstract class DebugGuiTestBase extends GuiTestBase {

    protected Process debuggee;

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


    protected static void attach(int port) {
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

    protected int startDebuggee() throws IOException {
        return startDebuggee("demo.Main", app.fixtureClasses());
    }

    /** Starts a JVM that listens for a debugger, running the main class from the given classes, and returns the port. */
    protected int startDebuggee(String mainClass, Path classes) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=127.0.0.1:0",
                "-cp", classes.toString(), mainClass);
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

    protected static void placeCaretOn(String text) {
        GuiActionRunner.execute(() -> {
            javax.swing.text.JTextComponent area = sourceArea();
            int index = area.getText().indexOf(text);
            assertThat(index).isGreaterThanOrEqualTo(0);
            area.requestFocusInWindow();
            area.setCaretPosition(index);
        });
    }

    /** Rows of the thread tree (plain JTree of the Debug tab). */
    protected static java.util.List<String> threadRows() {
        java.util.List<JTree> trees = findAll(JTree.class, t -> t.isShowing() && t.getClass() == JTree.class);
        return trees.isEmpty() ? java.util.List.of() : rows(trees.get(0));
    }

    protected static java.util.List<String> variableRows() {
        java.util.List<JTree> trees = findAll(JTree.class, t -> t.isShowing() && t instanceof org.jd.gui.view.component.DebugValueTree);
        return trees.isEmpty() ? java.util.List.of() : rows(trees.get(0));
    }

    protected static void waitForLocation(String location) {
        waitUntil("the JVM is stopped at " + location, () -> threadRows().contains(location));
    }

    protected static void selectDebugTab(String title) {
        window.tabbedPane(new GenericTypeMatcher<javax.swing.JTabbedPane>(javax.swing.JTabbedPane.class) {
            @Override
            protected boolean isMatching(javax.swing.JTabbedPane pane) {
                return pane.isShowing() && pane.getTabCount() == 3 && "Breakpoints".equals(pane.getTitleAt(1));
            }
        }).selectTab(title);
    }

    /** A button of the debug panels, found by its tool tip (the Skip All toggle included). */
    protected static org.assertj.swing.fixture.AbstractButtonFixture<?, ?> breakpointButton(String toolTip) {
        javax.swing.AbstractButton button = robot.finder().find(window.target(), new GenericTypeMatcher<javax.swing.AbstractButton>(javax.swing.AbstractButton.class) {
            @Override
            protected boolean isMatching(javax.swing.AbstractButton candidate) {
                return candidate.isShowing() && toolTip.equals(candidate.getToolTipText());
            }
        });
        return button instanceof javax.swing.JToggleButton toggle ? new org.assertj.swing.fixture.JToggleButtonFixture(robot, toggle)
                : new org.assertj.swing.fixture.JButtonFixture(robot, (javax.swing.JButton) button);
    }

    protected static void removeAllBreakpoints() {
        selectDebugTab("Breakpoints");
        org.assertj.swing.fixture.AbstractButtonFixture<?, ?> removeAll = breakpointButton("Remove All Breakpoints");
        if (removeAll.target().isEnabled()) {
            removeAll.click();
        }
        waitUntil("no breakpoint is left", () -> breakpointRows().isEmpty());
    }

    /** Labels of the listed breakpoints. */
    protected static java.util.List<String> breakpointRows() {
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

    protected static GenericTypeMatcher<javax.swing.JCheckBox> checkBoxNamed(String text) {
        return new GenericTypeMatcher<>(javax.swing.JCheckBox.class) {
            @Override
            protected boolean isMatching(javax.swing.JCheckBox box) {
                return box.isShowing() && text.equals(box.getText());
            }
        };
    }

    protected static void addWatch(String expression) {
        button(window, "Add...").click();
        DialogFixture dialog = dialog("Add Watch Expression");
        typeText(dialog.textBox(), expression);
        button(dialog, "OK").click();
        waitUntil("the dialog is closed", () -> !isDialogShowing("Add Watch Expression"));
    }

    protected static void confirm(String title) {
        button(dialog(title), "OK").click();
    }

    /** Dismisses the warning that a rejected entry shows (the entry dialog, which has a text field, comes back right after). */
    protected static void dismissWarning(String message) {
        waitUntil("the warning '" + message + "' is shown", () -> exceptionDialogs(false));
        button(dialog("Add Java Exception Breakpoint"), "OK").click();
        waitUntil("the entry dialog is back", () -> exceptionDialogs(true));
    }

    /** Whether the showing "Add Java Exception Breakpoint" dialog is the entry form (with its text field) or the warning. */
    protected static boolean exceptionDialogs(boolean entryForm) {
        return GuiActionRunner.execute(() -> {
            for (java.awt.Window w : java.awt.Window.getWindows()) {
                if (w.isShowing() && w instanceof javax.swing.JDialog d && "Add Java Exception Breakpoint".equals(d.getTitle())) {
                    return hasTextField(d) == entryForm;
                }
            }
            return false;
        });
    }

    protected static boolean hasTextField(java.awt.Container container) {
        for (java.awt.Component c : container.getComponents()) {
            if (c instanceof javax.swing.JTextField || c instanceof java.awt.Container child && hasTextField(child)) {
                return true;
            }
        }
        return false;
    }
}
