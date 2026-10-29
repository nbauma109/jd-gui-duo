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

    /** Source line number under the text caret, or 0 when it has no executable code. */
    default int getCaretSourceLine() {
        return 0;
    }
}
