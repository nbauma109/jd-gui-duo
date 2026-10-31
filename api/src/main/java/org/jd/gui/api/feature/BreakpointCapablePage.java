/*
 * Copyright 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 */

package org.jd.gui.api.feature;

import java.util.Set;
import java.util.function.BiPredicate;

public interface BreakpointCapablePage {
    String getDebugTypeName();

    void setBreakpointToggleHandler(BiPredicate<String, Integer> handler);

    void setBreakpointLine(int lineNumber, boolean enabled);

    Set<Integer> getBreakpointLines();

    void setCurrentExecutionLine(int lineNumber);

    /** Toggles the breakpoint on the line holding the text caret (keyboard alternative to the gutter click). */
    default void toggleBreakpointAtCaret() {
    }

    /**
     * Eclipse-style description of the method holding a source line, e.g. {@code main(String[])}
     * (a lambda body is attributed to its enclosing method), or null when unknown.
     */
    default String getBreakpointMethodDescription(int lineNumber) {
        return null;
    }

    /**
     * Installs the editor's debug context menu (Watch, Inspect, Run to Line). The menu is only offered while
     * {@code suspended} says the debuggee is stopped; handlers receive the expression text.
     */
    default void setDebugExpressionHandlers(java.util.function.BooleanSupplier suspended,
            java.util.function.BiConsumer<String, java.awt.Point> inspect, java.util.function.Consumer<String> watch,
            java.util.function.IntConsumer runToLine) {
    }

    /** Inspects the selected expression (or the identifier at the caret): shortcut of the context menu entry. */
    default void inspectSelection() {
    }

    /** Source line number under the text caret, or 0 when it has no executable code. */
    default int getCaretSourceLine() {
        return 0;
    }
}
