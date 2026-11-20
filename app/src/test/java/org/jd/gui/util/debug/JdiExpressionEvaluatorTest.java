/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.util.debug;

import com.sun.jdi.Value;

import org.jd.gui.model.debug.DebugVariableNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Evaluates expressions in the frame of a real, suspended JVM ({@link EvalTarget}) through JDI, the way the debugger
 * does for conditional breakpoints, watch expressions and the "Display" view.
 */
class JdiExpressionEvaluatorTest {

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

    /** Evaluates the expression, formats the result and drops the object ids that change from one run to another. */
    private static String eval(String expression) {
        try {
            Value value = JdiExpressionEvaluator.evaluate(expression, session.thread(), session.mainFrame());
            String shown = JdiValues.format(value);
            JdiExpressionEvaluator.release(value);
            return shown.replaceAll("\\s+\\(id=\\d+\\)", "");
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
    void literalsOfEveryKind() {
        assertEvaluates(
                "10L", "10", "1.5f", "1.5", "2.0d", "2.0", "0x1F", "31", "0xFFL", "255", "017", "15", "0b101", "5", "1_000", "1000", "3.5e2", "350.0",
                "'a'", "'a'", "'\\u0041'", "'A'", "'\\101'", "'A'", "\"tab\\t\"", "\"tab\\t\"", "\"\\\"q\\\"\"", "\"\\\"q\\\"\"",
                "null", "null", "true", "true", "(1)", "1", "((i))", "7");
    }

    @Test
    void arithmeticFollowsTheJavaPromotionRules() {
        assertEvaluates(
                "i + 1", "8", "i % 4", "3", "i / 2", "3", "7 / 2.0", "3.5", "7 % 2.5", "2.0", "i * 1.0", "7.0", "(i + 2) * 3", "27",
                "d * 2", "5.0", "f * 2", "3.0", "f + d", "4.0", "l * 2", "246913578024", "l + 1", "123456789013", "i + l", "123456789019",
                "b + sh", "305", "b * 2", "10", "sh / 7", "42", "c + 1", "121", "'a' + 'b'", "195", "5.0 / 0", "Infinity",
                "+i", "7", "-i", "-7", "-d", "-2.5", "-(i + 1)", "-8", "- -i", "7", "+ +i", "7");
    }

    @Test
    void comparisonsAndLogicalOperatorsShortCircuit() {
        assertEvaluates(
                "i > 3 && i < 10", "true", "i < 3 || i > 5", "true", "i >= 7", "true", "i <= 7", "true", "i != 7", "false", "l > i", "true", "d < f", "false",
                "d >= 2.5", "true", "i == 7 && flag", "true", "!flag", "false", "!!flag", "true",
                "nul != null && nul.length() > 0", "false", "nul == null || nul.length() > 0", "true",
                "s == o", "true", "s == null", "false", "null != s", "true", "null == null", "true", "obj == obj.next", "false", "obj != null", "true",
                "list.size() == 2", "true");
    }

    @Test
    void conditionalExpressionsPromoteTheirBranches() {
        assertEvaluates(
                "flag ? 1 : 2", "1", "flag ? 1 : 2.5", "1.0", "flag ? boxed : 1.5", "42.0", "!flag ? s : null", "null",
                "i > 3 ? \"big\" : \"small\"", "\"big\"", "list.size() > 1 ? list.get(0) : \"none\"", "\"a\"");
    }

    @Test
    void stringsConcatenateAndCallTheirMethods() {
        assertEvaluates(
                "s + \" world\"", "\"hello world\"", "c + \"!\"", "\"x!\"", "i + \"x\"", "\"7x\"", "1 + 2 + \"x\"", "\"3x\"", "\"x\" + 1 + 2", "\"x12\"",
                "words[0] + words[1]", "\"ab\"", "s.length()", "5", "s.charAt(1)", "'e'", "s.substring(1, 3)", "\"el\"", "s.indexOf(\"l\")", "2",
                "s.toUpperCase()", "\"HELLO\"", "s.isEmpty()", "false", "s.concat(\" there\")", "\"hello there\"", "s.contains(\"ell\")", "true",
                "s.equals(\"hello\")", "true", "s.hashCode()", "99162322", "o.toString()", "\"hello\"", "o.equals(s)", "true",
                "o.getClass().getName()", "\"java.lang.String\"", "sb.append(\"!\").toString()", "\"sb!\"");
    }

    @Test
    void boxedValuesAreUnboxedInArithmetic() {
        assertEvaluates(
                "boxed + 1", "43", "boxed == 42", "true", "boxed + boxedLong", "47", "boxedLong * 2", "10", "boxedDouble + 1", "2.5",
                "boxedFlag && flag", "true", "!boxedFlag", "false", "boxedChar + 1", "114", "-boxed", "-42", "boxed == boxed", "true",
                "boxed", "Integer", "boxedLong", "Long", "boxedDouble", "Double", "boxedFlag", "Boolean", "boxedChar", "Character");
    }

    @Test
    void localsFieldsAndArraysAreRead() {
        assertEvaluates(
                "i", "7", "l", "123456789012", "d", "2.5", "f", "1.5", "c", "'x'", "flag", "true", "b", "5", "sh", "300", "s", "\"hello\"", "nul", "null",
                "arr[1]", "2", "arr.length", "3", "arr[arr[0]]", "2", "arr[i - 6]", "2", "arr.clone().length", "3", "grid[1][0]", "3", "grid.length", "2",
                "grid[0].length", "2", "grid[1][1] * 2", "8", "words[1]", "\"b\"", "words.length", "2", "words[0].length()", "1", "arr", "int[3]",
                "words", "String[2]", "grid", "int[2][]", "list", "ArrayList", "obj", "EvalTarget", "color", "EvalTarget$Color",
                "obj.name", "\"n\"", "obj.count", "3", "obj.next.name", "\"m\"", "obj.next.next", "null", "obj.name.length()", "1");
    }

    @Test
    void methodsAreInvokedInTheDebuggee() {
        assertEvaluates(
                "obj.getName()", "\"n\"", "obj.add(1, 2)", "3", "obj.add(1.0, 2.0)", "3.5", "obj.add(1, 2.5)", "4.0", "obj.add(obj.count, 4)", "7",
                "obj.add(i, i)", "14", "obj.self().name", "\"n\"", "obj.describe()", "\"n:3\"", "obj.compareTo(obj.next)", "-1", "obj.toString()", "\"Target(n)\"",
                "obj.next.getName()", "\"m\"", "EvalTarget.twice(4)", "8", "EvalTarget.twice(i + 1)", "16", "list.size()", "2", "list.get(1)", "\"b\"",
                "list.contains(\"a\")", "true", "list.isEmpty()", "false", "map.size()", "1", "map.containsKey(\"k\")", "true", "map.get(\"k\")", "Integer",
                "color.name()", "\"GREEN\"", "color.ordinal()", "1", "color.toString()", "\"GREEN\"");
    }

    @Test
    void staticMembersAndJdkClassesAreResolvedByName() {
        assertEvaluates(
                "EvalTarget.LIMIT", "10", "EvalTarget.GREETING", "\"hi\"", "EvalTarget.BIG", "1099511627776", "Integer.MAX_VALUE", "2147483647",
                "Long.MAX_VALUE", "9223372036854775807", "Boolean.TRUE", "Boolean", "Math.max(i, 9)", "9", "java.lang.Math.abs(-3)", "3",
                "Integer.parseInt(\"12\")", "12", "String.valueOf(i)", "\"7\"", "System.lineSeparator().length()", String.valueOf(System.lineSeparator().length()),
                "color == EvalTarget.Color.GREEN", "true", "EvalTarget.Color.RED", "EvalTarget$Color", "EvalTarget.Color.RED.ordinal()", "0",
                "EvalTarget.Color.valueOf(\"BLUE\")", "EvalTarget$Color");
    }

    @Test
    void invalidExpressionsAreReportedWithAMessage() {
        assertEvaluates(
                "i +", "ERROR: Unexpected end of expression", "i + * 2", "ERROR: Unexpected '*' at position 4", "i )", "ERROR: Unexpected ')' at position 2",
                "5 5", "ERROR: Unexpected '5' at position 2", "i = 3", "ERROR: Unexpected '=' at position 2", ".x", "ERROR: Unexpected '.' at position 0",
                "obj.", "ERROR: Identifier expected at position 4", "obj.name.", "ERROR: Identifier expected at position 9",
                "\"unterminated", "ERROR: Unterminated literal", "'ab'", "ERROR: Invalid character literal", "0x", "ERROR: Invalid number '0x'",
                "i++", "ERROR: Increment and decrement are not supported", "~i", "ERROR: Unexpected '~' at position 0",
                "i ? 1 : 2", "ERROR: A boolean is required", "1 ? 2", "ERROR: A boolean is required");
    }

    @Test
    void unknownNamesAndWrongTargetsAreReported() {
        assertEvaluates(
                "unknown", "ERROR: 'unknown' cannot be resolved", "nosuch.name", "ERROR: 'nosuch' cannot be resolved",
                "EvalTarget", "ERROR: org.jd.gui.util.debug.EvalTarget is a type, not a value",
                "EvalTarget.nothing", "ERROR: No static field 'nothing' in org.jd.gui.util.debug.EvalTarget",
                "EvalTarget.nothing()", "ERROR: No method nothing/0 in org.jd.gui.util.debug.EvalTarget accepts these arguments",
                "EvalTarget.twice(\"x\")", "ERROR: No method twice/1 in org.jd.gui.util.debug.EvalTarget accepts these arguments",
                "obj.nosuch", "ERROR: No field 'nosuch' in org.jd.gui.util.debug.EvalTarget", "arr.nosuch", "ERROR: No field 'nosuch' in int[]",
                "i.foo", "ERROR: Cannot read 'foo' on a primitive value", "i.foo()", "ERROR: Cannot call 'foo' on a primitive value",
                "null.x", "ERROR: Cannot read 'x' on null", "obj.next.next.name", "ERROR: Cannot read 'name' on null",
                "nul.length()", "ERROR: Cannot call 'length' on null", "null + 1", "ERROR: Operator + needs numbers");
    }

    @Test
    void arrayAccessIsChecked() {
        assertEvaluates(
                "arr[5]", "ERROR: Index 5 out of bounds for length 3", "arr[-1]", "ERROR: Index -1 out of bounds for length 3",
                "s[0]", "ERROR: Not an array", "i[0]", "ERROR: Not an array",
                "arr[\"a\"]", "ERROR: An array index must be an int, short, byte or char", "arr[1.5]", "ERROR: An array index must be an int, short, byte or char",
                "arr[l]", "ERROR: An array index must be an int, short, byte or char", "i / 0", "ERROR: / by zero", "i % 0", "ERROR: / by zero");
    }

    @Test
    void variableNodesExposeFieldsAndElements() throws Exception {
        Value object = JdiExpressionEvaluator.evaluate("obj", session.thread(), session.mainFrame());
        DebugVariableNode node = JdiValues.toNode("obj", object);
        List<DebugVariableNode> fields = node.getChildren();

        assertThat(node.valueText).startsWith("EvalTarget");
        assertThat(node.isLeaf()).isFalse();
        assertThat(fields).extracting(f -> f.name).containsExactly("name", "count", "next");
        assertThat(fields).extracting(f -> f.kind).containsOnly(DebugVariableNode.Kind.FIELD_PUBLIC);
        assertThat(fields.get(1).valueText).isEqualTo("3");

        Value array = JdiExpressionEvaluator.evaluate("arr", session.thread(), session.mainFrame());
        DebugVariableNode elements = JdiValues.toNode("arr", array);
        assertThat(elements.getChildren()).extracting(e -> e.name + "=" + e.valueText).containsExactly("[0]=1", "[1]=2", "[2]=3");

        Value text = JdiExpressionEvaluator.evaluate("s", session.thread(), session.mainFrame());
        assertThat(JdiValues.toNode("s", text).isLeaf()).isTrue();
        assertThat(JdiValues.toNode("nothing", null).valueText).isEqualTo("null");
        JdiExpressionEvaluator.release(object);
        JdiExpressionEvaluator.release(array);
        JdiExpressionEvaluator.release(text);
    }
}
