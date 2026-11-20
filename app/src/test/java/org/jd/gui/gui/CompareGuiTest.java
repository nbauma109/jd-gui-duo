/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.gui;
import org.assertj.swing.core.GenericTypeMatcher;
import org.assertj.swing.finder.WindowFinder;
import org.assertj.swing.fixture.FrameFixture;
import org.junit.jupiter.api.Test;

import javax.swing.JFrame;

import static org.assertj.core.api.Assertions.assertThat;

class CompareGuiTest extends GuiTestBase {

    @Test
    void compareFilesOpensTheJarComparerAndItsFileSelection() {
        clickMenu("File", "Compare Files...");

        FrameFixture comparer = frame("Jar Comparer");
        comparer.requireVisible();
        dialog("Select files to compare - Drag and drop").requireVisible();
        assertThat(comparer.target().isShowing()).isTrue();
    }

    @Test
    void compareJdCoreVersionsIsOnlyEnabledOnATypeAndOpensADiff() {
        assertThat(isMenuEnabled("File", "Compare JD-Core V0 vs V1...")).isFalse();
        openFixtureJar();
        openType("Greeter");
        assertThat(isMenuEnabled("File", "Compare JD-Core V0 vs V1...")).isTrue();

        clickMenu("File", "Compare JD-Core V0 vs V1...");

        FrameFixture diff = frame("Comparison view for class demo/Greeter.class");
        diff.requireVisible();
    }

    private static FrameFixture frame(String titlePrefix) {
        return WindowFinder.findFrame(new GenericTypeMatcher<JFrame>(JFrame.class) {
            @Override
            protected boolean isMatching(JFrame frame) {
                return frame.isShowing() && frame.getTitle() != null && frame.getTitle().startsWith(titlePrefix);
            }
        }).withTimeout(TIMEOUT_MS).using(robot);
    }
}
