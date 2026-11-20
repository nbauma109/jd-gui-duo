/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.gui;
import org.assertj.swing.fixture.DialogFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.event.KeyEvent;

import javax.swing.JCheckBox;
import javax.swing.JTree;

import static org.assertj.core.api.Assertions.assertThat;

/** "Search..." searches the constant pools of the classes (types, fields, methods, string constants...). */
class SearchGuiTest extends GuiTestBase {

    @BeforeEach
    void openJar() {
        openFixtureJar();
        // The search relies on the indexes, which are built in the background after the file is opened
        waitUntil("the search is enabled", () -> isMenuEnabled("Search", "Search..."));
    }

    @Test
    void searchDialogOpensWithTheMenuAndWithItsShortcut() {
        clickMenu("Search", "Search...");
        dialog("Search").requireVisible();
        button(dialog("Search"), "Cancel").click();
        waitUntil("the dialog is closed", () -> !isDialogShowing("Search"));

        pressShortcut(KeyEvent.VK_S, KeyEvent.VK_SHIFT);
        dialog("Search").requireVisible();
        assertThat(isDialogShowing("Search")).isTrue();
    }

    @Test
    void searchDialogOffersAllTheSearchKinds() {
        clickMenu("Search", "Search...");
        DialogFixture dialog = dialog("Search");

        for (String option : new String[] {"Type", "Declarations", "References"}) {
            checkBox(dialog, option).requireVisible().requireSelected();
        }
        for (String option : new String[] {"Field", "Constructor", "Method", "String Constant", "Java Module"}) {
            checkBox(dialog, option).requireVisible().requireNotSelected();
        }
        button(dialog, "Open").requireDisabled();
    }

    @Test
    void findsAMethodDeclaration() {
        DialogFixture dialog = openSearch();
        checkBox(dialog, "Method").check();
        typeText(dialog.textBox(), "greet");

        JTree results = dialog.tree().target();
        waitUntil("results are listed", () -> !rows(results).isEmpty());
        expandAll(dialog.tree());
        assertThat(rows(results)).anyMatch(row -> row.contains("greet"));
    }

    @Test
    void findsAStringConstant() {
        DialogFixture dialog = openSearch();
        checkBox(dialog, "String Constant").check();
        typeText(dialog.textBox(), "Hello*");

        JTree results = dialog.tree().target();
        waitUntil("results are listed", () -> !rows(results).isEmpty());
        expandAll(dialog.tree());
        assertThat(rows(results)).anyMatch(row -> row.contains("Greeter"));
    }

    @Test
    void disablingTheMatchingKindRemovesTheResults() {
        DialogFixture dialog = openSearch();
        checkBox(dialog, "Method").check();
        typeText(dialog.textBox(), "greet");
        JTree results = dialog.tree().target();
        waitUntil("results are listed", () -> !rows(results).isEmpty());

        checkBox(dialog, "Method").uncheck();

        waitUntil("results are gone", () -> rows(results).isEmpty());
    }

    @Test
    void openingAResultShowsTheTypeInTheMainWindow() {
        DialogFixture dialog = openSearchAndType("Shape");
        JTree results = dialog.tree().target();
        waitUntil("results are listed", () -> !rows(results).isEmpty());
        expandAll(dialog.tree());

        int row = rows(results).indexOf("Shape");
        assertThat(row).as("rows " + rows(results)).isGreaterThanOrEqualTo(0);
        dialog.tree().doubleClickRow(row);

        waitUntil("the main window shows the type", () -> window.target().getTitle().startsWith("Shape.class"));
    }

    private DialogFixture openSearchAndType(String text) {
        DialogFixture dialog = openSearch();
        typeText(dialog.textBox(), text);
        return dialog;
    }

    private DialogFixture openSearch() {
        clickMenu("Search", "Search...");
        return dialog("Search");
    }

    private static org.assertj.swing.fixture.JCheckBoxFixture checkBox(DialogFixture dialog, String text) {
        return dialog.checkBox(new org.assertj.swing.core.GenericTypeMatcher<JCheckBox>(JCheckBox.class) {
            @Override
            protected boolean isMatching(JCheckBox box) {
                return box.isShowing() && text.equals(box.getText());
            }
        });
    }
}
