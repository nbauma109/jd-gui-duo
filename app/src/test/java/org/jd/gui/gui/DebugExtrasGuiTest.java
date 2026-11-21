/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.gui;

import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.fixture.JTreeFixture;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import javax.swing.JTree;

import static org.assertj.core.api.Assertions.assertThat;

/** The less used debugging features: Run to Line, Drop to Frame, selecting a frame, Inspect, and removing breakpoints. */
class DebugExtrasGuiTest extends DebugGuiTestBase {

    /** Opens Main, puts a breakpoint on the call in the loop, starts the debuggee and waits until it stops there. */
    private void stopAtTheBreakpoint() throws IOException {
        openFixtureJar();
        openType("Main");
        placeCaretOn("greeter.greet()");
        clickMenu("Debug", "Toggle Breakpoint");
        attach(startDebuggee());
        waitForLocation("demo.Main.main() line: 7");
    }

    private static JTree threadTree() {
        return findAll(JTree.class, t -> t.isShowing() && t.getClass() == JTree.class).get(0);
    }

    private static void selectFrameRow(String prefix) {
        JTree tree = threadTree();
        int row = rows(tree).indexOf(rows(tree).stream().filter(r -> r.startsWith(prefix)).findFirst().orElseThrow());
        new JTreeFixture(robot, tree).clickRow(row);
    }

    @Test
    void runToLineStopsAgainInTheCallerAtALaterLine() throws IOException {
        stopAtTheBreakpoint();
        placeCaretOn("Thread.sleep");
        assertThat(isMenuEnabled("Debug", "Run to Line")).isTrue();

        clickMenu("Debug", "Run to Line");

        waitUntil("the thread is suspended again in main", () -> threadRows().stream().anyMatch(row -> row.startsWith("Thread \"main\" (Suspended"))
                && threadRows().stream().anyMatch(row -> row.startsWith("demo.Main.main() line:")));
    }

    @Test
    void selectingAFrameShowsItsVariablesAndDroppingToAFrameRerunsIt() throws IOException {
        stopAtTheBreakpoint();
        clickMenu("Debug", "Step Into");
        waitUntil("the callee is entered", () -> threadRows().stream().anyMatch(row -> row.startsWith("demo.Greeter.greet()")));

        selectFrameRow("demo.Main.main()");
        waitUntil("the variables of main are shown", () -> variableRows().stream().anyMatch(row -> row.startsWith("greeter")));
        selectFrameRow("demo.Greeter.greet()");
        waitUntil("the variables of the callee are shown", () -> variableRows().stream().noneMatch(row -> row.startsWith("greeter")));

        assertThat(isMenuEnabled("Debug", "Drop to Frame")).isTrue();
        clickMenu("Debug", "Drop to Frame");

        waitUntil("the callee frame is dropped", () -> threadRows().stream().noneMatch(row -> row.startsWith("demo.Greeter.greet()"))
                && threadRows().stream().anyMatch(row -> row.startsWith("demo.Main.main() line:")));
    }

    @Test
    void inspectingTheSelectedExpressionShowsItsValueInAPopup() throws IOException {
        stopAtTheBreakpoint();
        GuiActionRunner.execute(() -> {
            javax.swing.text.JTextComponent area = sourceArea();
            int start = area.getText().indexOf("greeter.greet()");
            area.requestFocusInWindow();
            area.select(start, start + "greeter.greet()".length());
        });
        assertThat(isMenuEnabled("Debug", "Inspect")).isTrue();

        clickMenu("Debug", "Inspect");

        waitUntil("the popup shows the value", () -> findAll(JTree.class, t -> t.isShowing() && rows(t).stream().anyMatch(r -> r.contains("Hello, debugger!"))).size() > 0);
        robot.pressAndReleaseKey(java.awt.event.KeyEvent.VK_ESCAPE);
    }

    @Test
    void aBreakpointIsDisabledAndRemovedFromTheBreakpointsTab() throws IOException {
        stopAtTheBreakpoint();
        selectDebugTab("Breakpoints");
        waitUntil("the breakpoint is listed", () -> breakpointRows().size() == 1);
        String label = breakpointRows().get(0);
        assertThat(label).contains("Main");

        javax.swing.JList<?> list = findAll(javax.swing.JList.class, l -> l.isShowing() && l.getParent() != null && l.getParent().getParent() != null
                && l.getParent().getParent().getParent() instanceof org.jd.gui.view.component.BreakpointListPanel).get(0);
        new org.assertj.swing.fixture.JListFixture(robot, list).selectItem(0);
        breakpointButton("Remove Selected Breakpoints").click();

        waitUntil("the breakpoint is removed", () -> breakpointRows().isEmpty());
    }
}
