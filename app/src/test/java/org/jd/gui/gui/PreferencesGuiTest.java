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
import org.junit.jupiter.api.Test;

import java.awt.event.KeyEvent;

import javax.swing.JCheckBox;
import javax.swing.JTable;
import javax.swing.JTextField;

import static org.assertj.core.api.Assertions.assertThat;

class PreferencesGuiTest extends GuiTestBase {

    @Test
    void preferencesDialogOpensWithTheMenuAndWithItsShortcut() {
        clickMenu("Help", "Preferences...");
        DialogFixture dialog = dialog("Preferences");
        button(dialog, "Ok").requireVisible();
        button(dialog, "Cancel").requireVisible().click();
        waitUntil("the dialog is closed", () -> !isDialogShowing("Preferences"));

        pressShortcut(KeyEvent.VK_P, KeyEvent.VK_SHIFT);
        dialog("Preferences").requireVisible();
        assertThat(isDialogShowing("Preferences")).isTrue();
    }

    @Test
    void preferencesShowTheDecompilerAndViewerSettings() {
        clickMenu("Help", "Preferences...");
        DialogFixture dialog = dialog("Preferences");

        dialog.label(org.assertj.swing.core.matcher.JLabelMatcher.withText("Maximum depth for directory exploration (1..30): ")).requireVisible();
        dialog.label(org.assertj.swing.core.matcher.JLabelMatcher.withText("Font Size Of Text Area (2..40): ")).requireVisible();
        dialog.checkBox(new GenericTypeMatcher<JCheckBox>(JCheckBox.class) {
            @Override
            protected boolean isMatching(JCheckBox box) {
                return box.isShowing() && "Dark Mode (needs restart)".equals(box.getText());
            }
        }).requireVisible();
    }

    @Test
    void changedPreferenceIsKeptWhenConfirmedWithOk() {
        setDirectoryDepth("15", "16");
        try {
            clickMenu("Help", "Preferences...");
            DialogFixture dialog = dialog("Preferences");
            assertThat(field(dialog, "16").target().isShowing()).isTrue();
            button(dialog, "Cancel").click();
        } finally {
            setDirectoryDepth("16", "15");
        }
    }

    @Test
    void changedPreferenceIsDiscardedWhenCancelled() {
        clickMenu("Help", "Preferences...");
        DialogFixture dialog = dialog("Preferences");
        typeText(field(dialog, "15"), "17");
        button(dialog, "Cancel").click();
        waitUntil("the dialog is closed", () -> !isDialogShowing("Preferences"));

        clickMenu("Help", "Preferences...");
        assertThat(field(dialog("Preferences"), "15").target().isShowing()).isTrue();
    }

    @Test
    void fontSizePreferenceIsAppliedToTheSourceView() {
        openFixtureJar();
        openType("Greeter");
        int original = GuiActionRunner.execute(() -> sourceArea().getFont().getSize());

        clickMenu("Help", "Preferences...");
        DialogFixture dialog = dialog("Preferences");
        typeText(field(dialog, String.valueOf(original)), "20");
        button(dialog, "Ok").click();
        waitUntil("the dialog is closed", () -> !isDialogShowing("Preferences"));

        waitUntil("the font size is applied", () -> sourceArea().getFont().getSize() == 20);

        clickMenu("Help", "Preferences...");
        dialog = dialog("Preferences");
        typeText(field(dialog, "20"), String.valueOf(original));
        button(dialog, "Ok").click();
        waitUntil("the font size is restored", () -> sourceArea().getFont().getSize() == original);
        assertThat(GuiActionRunner.execute(() -> sourceArea().getFont().getSize())).isEqualTo(original);
    }

    @Test
    void keyBindingsDialogListsTheShortcuts() {
        clickMenu("Help", "Key Bindings...");
        DialogFixture dialog = dialog("Key Bindings");

        JTable table = dialog.table().target();
        String content = GuiActionRunner.execute(() -> {
            StringBuilder sb = new StringBuilder();
            for (int r = 0; r < table.getRowCount(); r++) {
                for (int c = 0; c < table.getColumnCount(); c++) {
                    sb.append(table.getValueAt(r, c)).append('|');
                }
                sb.append('\n');
            }
            return sb.toString();
        });
        assertThat(content).contains("Open File...").contains("Quick Outline...").contains("Find Next");
        button(dialog, "Restore Defaults").requireEnabled();
        button(dialog, "Cancel").click();
        waitUntil("the dialog is closed", () -> !isDialogShowing("Key Bindings"));
    }

    @Test
    void securedAndEclipsePreferencesDialogsOpenAndCanBeCancelled() {
        for (String title : new String[] {"Secured Preferences", "Eclipse Preferences"}) {
            clickMenu("Help", title + "...");
            DialogFixture dialog = dialog(title);
            button(dialog, "Cancel").click();
            waitUntil("the " + title + " dialog is closed", () -> !isDialogShowing(title));
            assertThat(isDialogShowing(title)).isFalse();
        }
    }

    private void setDirectoryDepth(String from, String to) {
        clickMenu("Help", "Preferences...");
        DialogFixture dialog = dialog("Preferences");
        typeText(field(dialog, from), to);
        button(dialog, "Ok").click();
        waitUntil("the dialog is closed", () -> !isDialogShowing("Preferences"));
    }

    /** The text field of the dialog showing the given value. */
    private static JTextComponentFixture field(DialogFixture dialog, String value) {
        // several fields can show the same value (the font sizes): the first one is the first in the panel
        JTextField first = robot.finder().findAll(dialog.target(), new GenericTypeMatcher<JTextField>(JTextField.class) {
            @Override
            protected boolean isMatching(JTextField field) {
                return field.isShowing() && value.equals(field.getText());
            }
        }).stream().map(JTextField.class::cast).findFirst().orElseThrow(() -> new AssertionError("No field showing " + value));
        return new JTextComponentFixture(robot, first);
    }
}
