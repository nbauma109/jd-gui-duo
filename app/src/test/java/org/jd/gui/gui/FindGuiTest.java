/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.gui;
import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.fixture.JButtonFixture;
import org.assertj.swing.fixture.JComboBoxFixture;
import org.assertj.swing.fixture.JTextComponentFixture;
import org.assertj.swing.fixture.JToggleButtonFixture;
import org.jd.gui.view.component.IconButton;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Objects;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;

import static org.assertj.core.api.Assertions.assertThat;

class FindGuiTest extends GuiTestBase {

    @BeforeEach
    void openGreeter() {
        openFixtureJar();
        openType("Greeter");
    }

    /** The find options and text are application-wide: leave them as they were for the next test. */
    @AfterEach
    void resetFindPanel() {
        if (!isFindPanelShowing()) {
            return;
        }
        for (JToggleButton toggle : findAll(JToggleButton.class, b -> b.isShowing() && b.getToolTipText() != null)) {
            new JToggleButtonFixture(robot, toggle).uncheck();
        }
        findField().deleteText();
        findButton(null).click();
        waitUntil("the find panel is hidden", () -> !isFindPanelShowing());
    }

    @Test
    void findMenuShowsTheFindPanelAndCloseButtonHidesIt() {
        clickMenu("Edit", "Find...");

        assertThat(isFindPanelShowing()).isTrue();

        findButton(null).click();
        waitUntil("the find panel is hidden", () -> !isFindPanelShowing());
    }

    @Test
    void findShortcutShowsTheFindPanel() {
        pressShortcut(KeyEvent.VK_F);

        assertThat(isFindPanelShowing()).isTrue();
    }

    @Test
    void typingASearchTextSelectsTheFirstMatchInTheSource() {
        clickMenu("Edit", "Find...");

        typeText(findField(), "Hello");
        pressKey(KeyEvent.VK_ENTER);

        waitUntil("the match is selected", () -> "Hello".equals(selectedText()));
        assertThat(selectedText()).isEqualTo("Hello");
    }

    @Test
    void findNextAndPreviousMoveBetweenMatches() {
        clickMenu("Edit", "Find...");
        typeText(findField(), "public");
        pressKey(KeyEvent.VK_ENTER);
        waitUntil("the first match is selected", () -> "public".equals(selectedText()));
        int first = selectionStart();

        findButton("Next").click();
        waitUntil("the next match is selected", () -> selectionStart() > first);
        int second = selectionStart();

        findButton("Previous").click();
        waitUntil("the previous match is selected again", () -> selectionStart() == first);
        assertThat(second).isGreaterThan(first);
    }

    @Test
    void searchTextThatIsNotInTheSourceSelectsNothing() {
        clickMenu("Edit", "Find...");

        typeText(findField(), "zzzNotThere");
        pressKey(KeyEvent.VK_ENTER);

        robot.waitForIdle();
        assertThat(selectedText()).isNullOrEmpty();
    }

    @Test
    void matchCaseOptionRestrictsTheMatches() {
        clickMenu("Edit", "Find...");
        List<JToggleButton> toggles = findAll(JToggleButton.class, b -> b.isShowing() && b.getToolTipText() != null);
        JToggleButton matchCase = toggles.stream().filter(b -> b.getToolTipText().toLowerCase().contains("case")).findFirst().orElseThrow();
        new JToggleButtonFixture(robot, matchCase).check();

        typeText(findField(), "hello");
        pressKey(KeyEvent.VK_ENTER);

        robot.waitForIdle();
        assertThat(selectedText()).isNullOrEmpty();

        new JToggleButtonFixture(robot, matchCase).uncheck();
        findField().focus();
        pressKey(KeyEvent.VK_ENTER);
        waitUntil("the case-insensitive match is selected", () -> "Hello".equals(selectedText()));
    }

    // -------------------------------------------------------------------

    private static boolean isFindPanelShowing() {
        return !findAll(JLabel.class, l -> l.isShowing() && l.getText() != null && l.getText().startsWith("Find:")).isEmpty();
    }

    private static JTextComponentFixture findField() {
        JComboBox<?> combo = findAll(JComboBox.class, c -> c.isShowing() && c.isEditable()).get(0);
        JComboBoxFixture fixture = new JComboBoxFixture(robot, combo);
        return new JTextComponentFixture(robot, (JTextField) GuiActionRunner.execute(() -> fixture.target().getEditor().getEditorComponent()));
    }

    /** The button of the find panel with the given label (null for the close button). */
    private static JButtonFixture findButton(String text) {
        List<IconButton> buttons = findAll(IconButton.class, b -> b.isShowing() && Objects.equals(text, b.getText()));
        assertThat(buttons).as("find panel button " + text).hasSize(1);
        return new JButtonFixture(robot, buttons.get(0));
    }

    private static String selectedText() {
        return GuiActionRunner.execute(() -> sourceArea().getSelectedText());
    }

    private static int selectionStart() {
        return GuiActionRunner.execute(() -> sourceArea().getSelectionStart());
    }
}
