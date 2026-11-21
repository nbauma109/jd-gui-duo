/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.util.debug;

import com.sun.jdi.Value;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The evaluator on the paths that need a null box, an unselected operand that would fail, mixed reference types, or an instance frame. */
class JdiExpressionEvaluatorRound3Test {

    private static DebuggeeSession session;

    @BeforeAll
    static void launch() throws Exception {
        session = DebuggeeSession.start(EvalTarget.class.getName());
    }

    @AfterAll
    static void shutdown() {
        if (session != null) {
            session.close();
        }
    }

    private static String eval(String expression, int frame) {
        try {
            Value value = JdiExpressionEvaluator.evaluate(expression, session.thread(), frame);
            String shown = DebuggeeSession.withoutId(JdiValues.format(value));
            JdiExpressionEvaluator.release(value);
            return shown;
        } catch (JdiExpressionEvaluator.EvaluationException e) {
            return "ERROR: " + e.getMessage();
        }
    }

    /** Pairs of expression and expected result, evaluated in the given frame; one comparison for the whole table. */
    private static void assertEvaluates(int frame, String... expressionsAndResults) {
        Map<String, String> expected = new LinkedHashMap<>();
        Map<String, String> actual = new LinkedHashMap<>();
        for (int i = 0; i < expressionsAndResults.length; i += 2) {
            expected.put(expressionsAndResults[i], expressionsAndResults[i + 1]);
            actual.put(expressionsAndResults[i], eval(expressionsAndResults[i], frame));
        }
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void anUnselectedOperandIsNeverEvaluatedEvenWhenItWouldFail() {
        assertEvaluates(session.mainFrame(),
                "true ? 1 : 2 + 3", "1", "false ? 1 + 2 : 3", "3", "true ? 1 : i / 0", "1", "true ? 1 : l / 0L", "1", "true ? 1 : 5 % 0", "1", "true ? 1 : i % 0", "1",
                "true ? \"a\" : \"b\" + 1", "\"a\"", "false ? \"x\" + \"y\" : \"z\"", "\"z\"", "false ? (true ? 1 : 2) : 3", "3", "true ? 1 : (false ? 2 : 3)", "1",
                "flag ? 1 : arr[9]", "1", "flag ? 1 : grid[0][5]", "1", "flag ? 1 : nul.length()", "1", "flag ? s : s + 1", "\"hello\"",
                "flag ? 1 : boxedNull + 1", "1", "flag ? 1 : +boxedNull", "1", "false && i / 0 > 1", "false", "true || i / 0 > 1", "true");
    }

    @Test
    void aConditionalOfAPrimitiveAndAReferenceIsRefused() {
        assertEvaluates(session.mainFrame(),
                "flag ? 1 : obj.nosuch", "ERROR: A conditional of a primitive and a reference is not supported",
                "flag ? 1 : -nul", "ERROR: A conditional of a primitive and a reference is not supported");
    }

    @Test
    void unboxingANullBoxIsANullPointerException() {
        assertEvaluates(session.mainFrame(),
                "boxedNull == 1", "ERROR: NullPointerException: a null cannot be unboxed", "boxedNull + 1", "ERROR: NullPointerException: a null cannot be unboxed",
                "boxedNull > 0", "ERROR: NullPointerException: a null cannot be unboxed", "1 == boxedNull", "ERROR: NullPointerException: a null cannot be unboxed",
                "longNull * 2", "ERROR: NullPointerException: a null cannot be unboxed", "boxedNull == null", "true", "boxedNull", "null", "longNull", "null");
    }

    @Test
    void conditionalsOfUnrelatedReferencesGetTheirCommonType() {
        assertEvaluates(session.mainFrame(),
                "flag ? (flag ? list : map) : words", "ArrayList", "flag ? (flag ? list : map) : obj", "ArrayList",
                "flag ? (flag ? list : map) : (flag ? words : arr)", "ArrayList", "flag ? arr : list", "int[3]", "flag ? (flag ? arr : words) : grid", "int[3]",
                "flag ? words : grid", "String[2]", "flag ? arr : grid", "int[3]",
                // the members of the common type are the ones of Object (and of the interfaces that both types share)
                "(flag ? list : map).hashCode()", String.valueOf(eval("list.hashCode()", session.mainFrame())),
                "(flag ? list : map).size()", "ERROR: No method size/0 in java.util.ArrayList accepts these arguments",
                "(flag ? words : arr).length", "2", "(flag ? obj : s).toString()", "\"Target(n)\"",
                "flag ? 1 : (flag ? list : map).nosuch", "ERROR: A conditional of a primitive and a reference is not supported");
    }

    @Test
    void charactersAreNumbersInArithmeticAndConditionals() {
        assertEvaluates(session.mainFrame(), "'a' + 1", "98", "flag ? c : 'b'", "'x'", "flag ? 'a' : i", "97");
    }

    @Test
    void anInstanceFrameReadsItsFieldsAndCallsItsMethodsWithoutAQualifier() {
        int hold = session.frameOf("hold");
        assertThat(hold).isGreaterThanOrEqualTo(0);

        assertEvaluates(hold,
                "this", "EvalTarget", "name", "\"n\"", "count", "3", "next", "EvalTarget", "this.name", "\"n\"", "this.count", "3", "this.next.name", "\"m\"", "next.next", "null",
                "getName()", "\"n\"", "describe()", "\"n:3\"", "add(1, 2)", "3", "self().name", "\"n\"", "compareTo(next)", "-1", "toString()", "\"Target(n)\"",
                "equals(this)", "true", "twice(3)", "6", "LIMIT", "10", "this == self()", "true", "count + 1", "4", "name + count", "\"n3\"", "name.length()", "1",
                "fail()", "ERROR: Exception in fail(): java.lang.IllegalStateException");
    }
}
