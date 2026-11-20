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

/** The less common paths of the expression evaluator: skipped operands, conditional typing, narrowing, escapes. */
class JdiExpressionEvaluatorEdgeCasesTest {

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

    private static String eval(String expression) {
        try {
            Value value = JdiExpressionEvaluator.evaluate(expression, session.thread(), session.mainFrame());
            String shown = JdiValues.format(value);
            JdiExpressionEvaluator.release(value);
            return shown.replaceAll("\\s++\\(id=\\d++\\)", "");
        } catch (JdiExpressionEvaluator.EvaluationException e) {
            return "ERROR: " + e.getMessage();
        }
    }

    /** Pairs of expression and expected result; one comparison for the whole table. */
    private static void assertEvaluates(String... expressionsAndResults) {
        Map<String, String> expected = new LinkedHashMap<>();
        Map<String, String> actual = new LinkedHashMap<>();
        for (int i = 0; i < expressionsAndResults.length; i += 2) {
            expected.put(expressionsAndResults[i], expressionsAndResults[i + 1]);
            actual.put(expressionsAndResults[i], eval(expressionsAndResults[i]));
        }
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void operandsThatAreNotSelectedAreOnlyParsed() {
        assertEvaluates(
                "true ? 1 : -i", "1", "flag || -i > 0", "true", "false && -b > 0", "false", "flag ? \"a\" + i : s", "\"a7\"",
                "!flag ? +c : -c", "-120", "flag ? -i : +c", "-7", "false ? +b : 1", "1", "true ? +sh : -sh", "300");
    }

    @Test
    void conditionalExpressionsGetTheTypeTheLanguageGivesThem() {
        assertEvaluates(
                "flag ? obj : s", "EvalTarget", "flag ? arr : words", "int[3]", "flag ? list : map", "ArrayList", "flag ? boxed : boxedLong", "42",
                "flag ? boxedChar : boxedFlag", "Character", "flag ? o : obj", "\"hello\"", "flag ? s : sb", "\"hello\"", "flag ? sb : s", "StringBuilder",
                "flag ? b : 1", "5", "flag ? c : 65", "'x'", "flag ? sh : 70000", "300", "flag ? b : sh", "5", "flag ? c : b", "120", "flag ? 1 : b", "1",
                "flag ? i : l", "7", "flag ? f : d", "1.5", "flag ? boxed : 7", "42", "flag ? 'a' : 98", "'a'",
                "flag ? null : 1", "ERROR: A conditional of a primitive and null is not supported");
    }

    @Test
    void characterAndStringEscapes() {
        assertEvaluates(
                "'\\b'", "'\b'", "'\\t'", "'\t'", "'\\f'", "'\f'", "'\\r'", "'\r'", "'\\s'", "' '", "'\\0'", "'\0'", "\"\\0\"", "\"\0\"",
                "\"\\b\\f\\r\\s\"", "\"\b\f\\r \"", "'\\''", "'''", "'\\\\'", "'\\'");
    }

    @Test
    void numbersCharactersAndStringsAreCompared() {
        assertEvaluates(
                "f < 2", "true", "f >= f", "true", "f <= 1.5f", "true", "f > 1", "true", "d < 3", "true", "d >= d", "true", "l >= 5", "true", "c < 'y'", "true",
                "c == 'x'", "true", "b == 5", "true", "sh != 300", "false", "i == 7.0", "true", "d == 2.5", "true", "f == 1.5", "true", "boxed == 42.0", "true",
                "s == \"hello\"", "true", "\"a\" == \"a\"", "true", "\"a\" + \"b\" == \"ab\"", "true", "\"ab\" != \"a\" + \"b\"", "false",
                "s != \"x\"", "true", "\"x\" + 1 == \"x1\"", "true");
    }

    @Test
    void stringsConcatenateEveryKindOfOperand() {
        assertEvaluates(
                "\"x\" + 'c'", "\"xc\"", "\"x\" + 1.5", "\"x1.5\"", "\"x\" + null", "\"xnull\"", "\"x\" + true", "\"xtrue\"", "null + \"x\"", "\"nullx\"");
    }

    @Test
    void unqualifiedNamesAndCallsAreResolvedInTheFrameClass() {
        assertEvaluates(
                "twice(i)", "14", "LIMIT", "10", "GREETING", "\"hi\"", "this", "null",
                "getName()", "ERROR: No method getName/0 in org.jd.gui.util.debug.EvalTarget accepts these arguments");
    }

    @Test
    void exceptionsAndArrayArgumentsOfInvokedMethods() {
        assertEvaluates(
                "obj.fail()", "ERROR: Exception in fail(): java.lang.IllegalStateException", "java.util.Arrays.asList(words)", "Arrays$ArrayList",
                "java.util.Arrays.toString(arr)", "\"[1, 2, 3]\"", "String.join(\"-\", words)", "\"a-b\"", "String.valueOf(c)", "\"x\"",
                "String.valueOf(arr.length)", "\"3\"", "Math.abs(-b)", "5", "Math.max(sh, 5)", "300", "Math.max(l, 5)", "123456789012", "Math.max(f, 1)", "1.5",
                "Math.min(d, 1)", "1.0");
    }

    @Test
    void unsupportedOperatorsAreReported() {
        assertEvaluates(
                "i++", "ERROR: Increment and decrement are not supported", "++i", "ERROR: Increment and decrement are not supported",
                "i += 1", "ERROR: Unexpected '=' at position 3", "i << 2", "ERROR: Unexpected '<' at position 3", "i & 3", "ERROR: Unexpected '&' at position 2",
                "i | 8", "ERROR: Unexpected '|' at position 2", "i ^ 2", "ERROR: Unexpected '^' at position 2", "!i", "ERROR: A boolean is required",
                "-flag", "ERROR: A number is required", "+s", "ERROR: A number is required", "!s", "ERROR: A boolean is required", "-nul", "ERROR: A number is required");
    }
}
