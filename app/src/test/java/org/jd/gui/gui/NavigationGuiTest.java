/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.gui;
import org.assertj.swing.fixture.DialogFixture;
import org.assertj.swing.fixture.JListFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.event.KeyEvent;

import javax.swing.JList;
import javax.swing.JTree;

import static org.assertj.core.api.Assertions.assertThat;

class NavigationGuiTest extends GuiTestBase {

    @BeforeEach
    void openGreeter() {
        openFixtureJar();
        openType("Greeter");
    }

    @Test
    void goToLineDialogMovesTheCaretToTheRequestedLine() {
        clickMenu("Navigation", "Go to Line...");
        DialogFixture dialog = dialog("Go to Line");

        typeText(dialog.textBox(), "5");
        button(dialog, "Ok").requireEnabled().click();

        waitUntil("the dialog is closed", () -> !isDialogShowing("Go to Line"));
        assertThat(caretLine()).isEqualTo(5);
    }

    @Test
    void goToLineDialogRejectsInvalidInput() {
        clickMenu("Navigation", "Go to Line...");
        DialogFixture dialog = dialog("Go to Line");

        typeText(dialog.textBox(), "not a number");

        button(dialog, "Ok").requireDisabled();
        button(dialog, "Cancel").click();
        waitUntil("the dialog is closed", () -> !isDialogShowing("Go to Line"));
        assertThat(isDialogShowing("Go to Line")).isFalse();
    }

    @Test
    void goToLineDialogOpensWithItsKeyboardShortcut() {
        pressShortcut(KeyEvent.VK_L);

        dialog("Go to Line").requireVisible();
        assertThat(isDialogShowing("Go to Line")).isTrue();
    }

    @Test
    void quickOutlineListsTheMembersAndCanBeFiltered() {
        clickMenu("Navigation", "Quick Outline...");
        DialogFixture dialog = dialog("Quick Outline");
        JTree outline = dialog.tree().target();
        expandTree(outline);
        assertThat(rows(outline)).anyMatch(row -> row.contains("greet()")).anyMatch(row -> row.contains("area()"));

        typeText(dialog.textBox(), "greet");

        waitUntil("the outline is filtered", () -> rows(outline).stream().noneMatch(row -> row.contains("area()")));
        assertThat(rows(outline)).anyMatch(row -> row.contains("greet()"));
    }

    @Test
    void openTypeDialogFindsTypesAndOpensTheChosenOne() {
        clickMenu("Navigation", "Open Type...");
        DialogFixture dialog = dialog("Open Type");

        typeText(dialog.textBox(), "Shape");

        JListFixture list = dialog.list();
        waitUntil("the matching types are listed", () -> listSize(list.target()) > 0);
        list.selectItem(0);
        button(dialog, "Open").requireEnabled().click();

        waitUntil("the type is displayed", () -> window.target().getTitle().startsWith("Shape.class"));
        assertThat(sourceText()).contains("interface Shape");
    }

    @Test
    void openTypeDialogCanBeCancelled() {
        clickMenu("Navigation", "Open Type...");
        DialogFixture dialog = dialog("Open Type");

        button(dialog, "Cancel").click();

        waitUntil("the dialog is closed", () -> !isDialogShowing("Open Type"));
        assertThat(window.target().getTitle()).startsWith("Greeter.class");
    }

    @Test
    void typeHierarchyShowsTheTypeAndItsSuperClass() {
        clickMenu("Navigation", "Open Type Hierarchy...");
        DialogFixture dialog = dialog("Hierarchy Type");
        JTree tree = dialog.tree().target();
        expandTree(tree);

        assertThat(rows(tree)).anyMatch(row -> row.contains("Object")).anyMatch(row -> row.contains("Greeter"));
    }

    @Test
    void backAndForwardNavigateBetweenVisitedTypes() {
        openType("Shape");
        assertThat(isMenuEnabled("Navigation", "Back")).isTrue();

        clickMenu("Navigation", "Back");
        waitUntil("the previous type is shown again", () -> window.target().getTitle().startsWith("Greeter.class"));
        assertThat(isMenuEnabled("Navigation", "Forward")).isTrue();

        clickMenu("Navigation", "Forward");
        waitUntil("the next type is shown again", () -> window.target().getTitle().startsWith("Shape.class"));
    }

    private static int caretLine() {
        return org.assertj.swing.edt.GuiActionRunner.execute(() -> {
            javax.swing.text.JTextComponent area = sourceArea();
            String before = area.getText().substring(0, area.getCaretPosition());
            return (int) before.chars().filter(c -> c == '\n').count() + 1;
        });
    }

    private static int listSize(JList<?> list) {
        return org.assertj.swing.edt.GuiActionRunner.execute(() -> list.getModel().getSize());
    }

    private static void expandTree(JTree tree) {
        org.assertj.swing.edt.GuiActionRunner.execute(() -> {
            for (int guard = 0; guard < 10; guard++) {
                for (int i = tree.getRowCount() - 1; i >= 0; i--) {
                    tree.expandRow(i);
                }
            }
        });
    }
}
