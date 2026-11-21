/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.gui;

import org.junit.jupiter.api.Test;

import java.io.IOException;


import static org.assertj.core.api.Assertions.assertThat;

/** A debugging session: a breakpoint in the decompiled source stops a real JVM, which is then stepped. */
class DebugSessionGuiTest extends DebugGuiTestBase {

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
}
