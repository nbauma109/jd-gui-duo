/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.gui;

import org.assertj.swing.fixture.JTreeFixture;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClassViewGuiTest extends GuiTestBase {

    @Test
    void doubleClickingATypeShowsItsDecompiledSource() {
        openFixtureJar();

        openType("Greeter");

        assertThat(sourceText())
                .contains("package demo;")
                .contains("public class Greeter implements Shape")
                .contains("public String greet()")
                .contains("Hello, ");
    }

    @Test
    void interfaceIsDecompiledToo() {
        openFixtureJar();

        openType("Shape");

        assertThat(sourceText()).contains("public interface Shape").contains("double area();");
    }

    @Test
    void sourceTabCanBeSwitchedBetweenOpenedTypes() {
        openFixtureJar();
        openType("Greeter");
        openType("Shape");

        assertThat(sourceText()).contains("interface Shape");
        assertThat(window.target().getTitle()).startsWith("Shape.class");
    }

    @Test
    void typeNodeExposesItsMembersInTheTree() {
        openFixtureJar();
        JTreeFixture tree = fileTree();
        expandAll(tree);

        assertThat(rows(tree.target())).anyMatch(row -> row.contains("greet()"));
    }

    @Test
    void sourcePanelShowsLineNumbersAndSelectionCanBeCopied() {
        openFixtureJar();
        openType("Greeter");

        assertThat(sourceArea().getText().lines().count()).isGreaterThan(5);
    }
}
