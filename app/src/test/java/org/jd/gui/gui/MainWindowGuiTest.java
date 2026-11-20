/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.gui;

import org.junit.jupiter.api.Test;

import java.awt.event.KeyEvent;

import static org.assertj.core.api.Assertions.assertThat;

class MainWindowGuiTest extends GuiTestBase {

    @Test
    void startsWithWelcomeScreenAndMainMenus() {
        window.requireVisible();
        assertThat(window.target().getTitle()).isEqualTo("Java Decompiler");
        window.label(org.assertj.swing.core.matcher.JLabelMatcher.withText("No files are open")).requireVisible();
        for (String menu : new String[] {"File", "Edit", "Navigation", "Search", "Debug", "Help"}) {
            window.menuItemWithPath(menu).requireVisible();
        }
    }

    @Test
    void actionsThatNeedAnOpenFileAreDisabledOnStartup() {
        assertThat(isMenuEnabled("File", "Open File...")).isTrue();
        assertThat(isMenuEnabled("File", "Close")).isFalse();
        assertThat(isMenuEnabled("File", "Save")).isFalse();
        assertThat(isMenuEnabled("File", "Save All Sources")).isFalse();
        assertThat(isMenuEnabled("Edit", "Find...")).isFalse();
        assertThat(isMenuEnabled("Navigation", "Open Type...")).isFalse();
        assertThat(isMenuEnabled("Navigation", "Go to Line...")).isFalse();
        assertThat(isMenuEnabled("Search", "Search...")).isFalse();
    }

    @Test
    void aboutDialogListsTheDecompilersAndClosesWithOk() {
        clickMenu("Help", "About...");
        org.assertj.swing.fixture.DialogFixture about = dialog("About Java Decompiler");
        about.label(org.assertj.swing.core.matcher.JLabelMatcher.withText("JD-GUI-DUO")).requireVisible();
        about.label(org.assertj.swing.core.matcher.JLabelMatcher.withText("Transformer API")).requireVisible();
        about.label(org.assertj.swing.core.matcher.JLabelMatcher.withText("CFR")).requireVisible();
        button(about, "Ok").click();
        waitUntil("the About dialog is closed", () -> !isDialogShowing("About Java Decompiler"));
    }

    @Test
    void aboutDialogOpensWithItsKeyboardShortcut() {
        pressKey(KeyEvent.VK_F1);
        dialog("About Java Decompiler").requireVisible();
    }

    @Test
    void toggleHighlightCheckboxMenuItemCanBeSwitchedOffAndOn() {
        org.assertj.swing.fixture.JMenuItemFixture item = window.menuItemWithPath("Search", "Toggle Highlight");
        boolean initial = item.target().isSelected();
        item.click();
        waitUntil("the check box item flips", () -> item.target().isSelected() != initial);
        window.menuItemWithPath("Search", "Toggle Highlight").click();
        waitUntil("the check box item flips back", () -> item.target().isSelected() == initial);
    }
}
