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

/** The evaluator on types that share several supertypes, on calls that are not run (typed by their return type) and on a failing initializer. */
class JdiExpressionEvaluatorRound4Test {

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
    void conditionalsOfSiblingTypesCallTheMembersTheyShare() {
        assertEvaluates(session.mainFrame(),
                "(flag ? left : right).base()", "\"base\"", "(flag ? left : right).tag()", "\"tag\"",
                "(flag ? right : left).base()", "\"base\"", "(flag ? left : other).tag()", "\"tag\"",
                "(flag ? (flag ? left : right) : other).tag()", "\"tag\"", "(flag ? (flag ? left : right) : left).base()", "\"base\"",
                "(flag ? left : (flag ? right : other)).tag()", "\"tag\"", "flag ? left : right", "EvalTarget$Left",
                "(flag ? left : right).hashCode() == left.hashCode()", "true");
    }

    @Test
    void arraysOfSiblingTypesShareAnArrayOfTheirCommonType() {
        assertEvaluates(session.mainFrame(),
                "flag ? lefts : rights", "EvalTarget$Left[1]", "(flag ? lefts : rights).length", "1", "(flag ? lefts : rights)[0].base()", "\"base\"",
                "flag ? lefts : words", "EvalTarget$Left[1]", "(flag ? arr : lefts).length", "3");
    }

    @Test
    void aCallThatIsNotRunStillGivesItsReturnTypeToTheConditional() {
        assertEvaluates(session.mainFrame(),
                "flag ? 1 : obj.byteOne()", "1", "flag ? 1 : obj.shortOne()", "1", "flag ? 1 : obj.longOne()", "1", "flag ? 1 : obj.floatOne()", "1.0",
                "flag ? 1 : obj.doubleOne()", "1.0", "flag ? 'a' : obj.charOne()", "'a'", "flag ? true : obj.boolOne()", "true", "flag ? \"a\" : obj.text()", "\"a\"",
                "!flag ? obj.byteOne() : 9", "9", "!flag ? obj.longOne() : 9", "9", "!flag ? obj.doubleOne() : 9", "9.0", "!flag ? obj.charOne() : 'z'", "'z'",
                "!flag ? obj.boolOne() : false", "false", "!flag ? obj.shortOne() : 8", "8", "!flag ? obj.floatOne() : 7", "7.0", "!flag ? obj.text() : \"b\"", "\"b\"");
    }

    @Test
    void aNullBoxThatAnOperatorUnboxesIsANullPointerException() {
        for (String expression : new String[] {"-boxedNull", "+boxedNull", "1 + boxedNull", "1 > boxedNull", "boxedNull < 1", "arr[boxedNull]",
                "longNull - 1", "true ? -boxedNull : 0"}) {
            assertThat(eval(expression, session.mainFrame())).as(expression).isEqualTo("ERROR: NullPointerException: a null cannot be unboxed");
        }
        assertThat(eval("-null", session.mainFrame())).isEqualTo("ERROR: A number is required");
    }

    @Test
    void aMemberClassThatTheProgramHasNotUsedYetCanBeRead() {
        assertThat(eval("EvalTarget.Loaded.word", session.mainFrame())).contains("seen");
        assertThat(eval("EvalTarget.Unused.answer", session.mainFrame())).isEqualTo("42");
        // what does not exist is still reported, and a branch that is not evaluated loads nothing
        assertThat(eval("EvalTarget.Missing.x", session.mainFrame())).startsWith("ERROR: No static field 'Missing' in ");
        assertThat(eval("false ? EvalTarget.Missing.x : 1", session.mainFrame())).doesNotContain("Exception");
    }

    @Test
    void aFailingInitializerIsReportedWithItsException() {
        String result = eval("EvalTarget.Boom.x", session.mainFrame());

        assertThat(result).startsWith("ERROR: Exception in the initializer of org.jd.gui.util.debug.EvalTarget$Boom: ");
    }
}
