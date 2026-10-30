/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */
package org.jd.gui.util.debug;

import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.BooleanValue;
import com.sun.jdi.ClassNotLoadedException;
import com.sun.jdi.ClassType;
import com.sun.jdi.CharValue;
import com.sun.jdi.DoubleValue;
import com.sun.jdi.Field;
import com.sun.jdi.FloatValue;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.InvalidTypeException;
import com.sun.jdi.InvocationException;
import com.sun.jdi.LocalVariable;
import com.sun.jdi.LongValue;
import com.sun.jdi.Method;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.PrimitiveValue;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.StackFrame;
import com.sun.jdi.StringReference;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.Value;
import com.sun.jdi.VirtualMachine;

import java.util.ArrayList;
import java.util.List;

/**
 * Small Java expression evaluator on top of JDI, enough for watch expressions:
 * literals, local variables, {@code this}, fields, static members ({@code Integer.MAX_VALUE}),
 * array indexing and {@code length}, method calls, unary/binary operators, {@code ?:} and string concatenation.
 */
public final class JdiExpressionEvaluator {

    public static final class EvaluationException extends Exception {
        private static final long serialVersionUID = 1L;

        public EvaluationException(String message) {
            super(message);
        }
    }

    private record ClassRef(ReferenceType type) {
    }

    private final VirtualMachine vm;
    private final ThreadReference thread;
    private final int frameIndex;
    private final String src;
    private int pos;
    /** > 0 while parsing an operand that must not be evaluated (short-circuit && || and the unselected ?: branch). */
    private int skipping;

    @FunctionalInterface
    private interface Operand {
        Object parse() throws EvaluationException, IncompatibleThreadStateException;
    }

    private JdiExpressionEvaluator(ThreadReference thread, int frameIndex, String src) {
        this.vm = thread.virtualMachine();
        this.thread = thread;
        this.frameIndex = frameIndex;
        this.src = src;
    }

    /** Evaluates {@code expression} in the given frame of a suspended thread. */
    public static Value evaluate(String expression, ThreadReference thread, int frameIndex) throws EvaluationException {
        JdiExpressionEvaluator evaluator = new JdiExpressionEvaluator(thread, frameIndex, expression);
        try {
            Object result = evaluator.parseTernary();
            evaluator.skipWs();
            if (evaluator.pos < evaluator.src.length()) {
                throw new EvaluationException("Unexpected '" + evaluator.src.charAt(evaluator.pos) + "' at position " + evaluator.pos);
            }
            if (result instanceof ClassRef classRef) {
                throw new EvaluationException(classRef.type().name() + " is a type, not a value");
            }
            return (Value) result;
        } catch (IncompatibleThreadStateException e) {
            throw new EvaluationException("The thread is not suspended");
        } catch (RuntimeException e) {
            String message = e.getMessage();
            throw new EvaluationException(message == null ? e.getClass().getSimpleName() : message);
        }
    }

    // --- Grammar --- //

    /** Parses an operand; when {@code skip} is set it is only parsed, never evaluated (no calls, no lookups). */
    private Object parseOperand(boolean skip, Operand operand) throws EvaluationException, IncompatibleThreadStateException {
        if (skip) {
            skipping++;
        }
        try {
            return operand.parse();
        } finally {
            if (skip) {
                skipping--;
            }
        }
    }

    private Object parseTernary() throws EvaluationException, IncompatibleThreadStateException {
        Object condition = parseOr();
        if (accept("?")) {
            boolean selectTrue = skipping == 0 && asBoolean(condition);
            Object whenTrue = parseOperand(!selectTrue, this::parseTernary);
            expect(":");
            Object whenFalse = parseOperand(selectTrue, this::parseTernary);
            return skipping > 0 ? null : selectTrue ? whenTrue : whenFalse;
        }
        return condition;
    }

    private Object parseOr() throws EvaluationException, IncompatibleThreadStateException {
        Object left = parseAnd();
        while (accept("||")) {
            boolean decided = skipping == 0 && asBoolean(left); // true || x: x is not evaluated
            Object right = parseOperand(decided, this::parseAnd);
            left = skipping > 0 ? null : vm.mirrorOf(decided || asBoolean(right));
        }
        return left;
    }

    private Object parseAnd() throws EvaluationException, IncompatibleThreadStateException {
        Object left = parseEquality();
        while (accept("&&")) {
            boolean decided = skipping == 0 && !asBoolean(left); // false && x: x is not evaluated
            Object right = parseOperand(decided, this::parseEquality);
            left = skipping > 0 ? null : vm.mirrorOf(!decided && asBoolean(right));
        }
        return left;
    }

    private Object parseEquality() throws EvaluationException, IncompatibleThreadStateException {
        Object left = parseRelational();
        while (true) {
            if (accept("==")) {
                Object right = parseRelational();
                left = skipping > 0 ? null : vm.mirrorOf(valuesEqual(value(left), value(right)));
            } else if (accept("!=")) {
                Object right = parseRelational();
                left = skipping > 0 ? null : vm.mirrorOf(!valuesEqual(value(left), value(right)));
            } else {
                return left;
            }
        }
    }

    private Object parseRelational() throws EvaluationException, IncompatibleThreadStateException {
        Object left = parseAdditive();
        while (true) {
            String op = acceptAny("<=", ">=", "<", ">");
            if (op == null) {
                return left;
            }
            Object right = parseAdditive();
            if (skipping > 0) {
                left = null;
                continue;
            }
            left = vm.mirrorOf(compare(op, value(left), value(right)));
        }
    }

    private Object parseAdditive() throws EvaluationException, IncompatibleThreadStateException {
        Object left = parseMultiplicative();
        while (true) {
            String op = acceptAny("+", "-");
            if (op == null) {
                return left;
            }
            Object right = parseMultiplicative();
            left = skipping > 0 ? null : arithmetic(op, left, right);
        }
    }

    private Object parseMultiplicative() throws EvaluationException, IncompatibleThreadStateException {
        Object left = parseUnary();
        while (true) {
            String op = acceptAny("*", "/", "%");
            if (op == null) {
                return left;
            }
            Object right = parseUnary();
            left = skipping > 0 ? null : arithmetic(op, left, right);
        }
    }

    private Object parseUnary() throws EvaluationException, IncompatibleThreadStateException {
        if (accept("!")) {
            Object operand = parseUnary();
            return skipping > 0 ? null : vm.mirrorOf(!asBoolean(operand));
        }
        if (accept("-")) {
            Object operandResult = parseUnary();
            if (skipping > 0) {
                return null;
            }
            Value operand = value(operandResult);
            if (operand instanceof DoubleValue d) {
                return vm.mirrorOf(-d.value());
            }
            if (operand instanceof FloatValue f) {
                return vm.mirrorOf(-f.value());
            }
            if (operand instanceof LongValue l) {
                return vm.mirrorOf(-l.value());
            }
            return vm.mirrorOf(-(int) numberAsLong(operand));
        }
        if (accept("+")) {
            return parseUnary();
        }
        return parsePostfix();
    }

    private Object parsePostfix() throws EvaluationException, IncompatibleThreadStateException {
        Object target = parsePrimary();
        while (true) {
            if (accept(".")) {
                String name = identifier();
                if (accept("(")) {
                    List<Value> arguments = parseArguments();
                    target = skipping > 0 ? null : invoke(target, name, arguments);
                } else {
                    target = skipping > 0 ? null : member(target, name);
                }
            } else if (accept("[")) {
                Object index = parseTernary();
                expect("]");
                if (skipping > 0) {
                    target = null;
                    continue;
                }
                Value array = value(target);
                if (!(array instanceof ArrayReference arrayReference)) {
                    throw new EvaluationException("Not an array");
                }
                int i = (int) numberAsLong(value(index));
                if (i < 0 || i >= arrayReference.length()) {
                    throw new EvaluationException("Index " + i + " out of bounds for length " + arrayReference.length());
                }
                target = arrayReference.getValue(i);
            } else {
                return target;
            }
        }
    }

    private Object parsePrimary() throws EvaluationException, IncompatibleThreadStateException {
        skipWs();
        if (pos >= src.length()) {
            throw new EvaluationException("Unexpected end of expression");
        }
        char c = src.charAt(pos);
        if (c == '(') {
            pos++;
            Object inner = parseTernary();
            expect(")");
            return inner;
        }
        if (Character.isDigit(c) || (c == '.' && pos + 1 < src.length() && Character.isDigit(src.charAt(pos + 1)))) {
            return parseNumber();
        }
        if (c == '"') {
            return vm.mirrorOf(parseQuoted('"'));
        }
        if (c == '\'') {
            String text = parseQuoted('\'');
            if (text.length() != 1) {
                throw new EvaluationException("Invalid character literal");
            }
            return vm.mirrorOf(text.charAt(0));
        }
        if (!Character.isJavaIdentifierStart(c)) {
            throw new EvaluationException("Unexpected '" + c + "' at position " + pos);
        }
        int start = pos;
        String name = identifier();
        switch (name) {
            case "true":
                return vm.mirrorOf(true);
            case "false":
                return vm.mirrorOf(false);
            case "null":
                return null;
            case "this":
                return frame().thisObject();
            default:
                break;
        }
        if (accept("(")) {
            List<Value> arguments = parseArguments();
            return skipping > 0 ? null : invokeImplicit(name, arguments);
        }
        if (skipping > 0) {
            return null;
        }
        Object resolved = resolveName(name);
        if (resolved != NOT_FOUND) {
            return resolved;
        }
        // Maybe a (qualified) class name such as System or java.util.Collections
        pos = start;
        ClassRef classRef = parseClassName();
        if (classRef != null) {
            return classRef;
        }
        throw new EvaluationException("'" + name + "' cannot be resolved");
    }

    private static final Object NOT_FOUND = new Object();

    private Object resolveName(String name) throws EvaluationException, IncompatibleThreadStateException {
        StackFrame frame = frame();
        try {
            LocalVariable variable = frame.visibleVariableByName(name);
            if (variable != null) {
                return frame.getValue(variable);
            }
        } catch (AbsentInformationException e) {
            // no local variable table: fall through to fields
        }
        ObjectReference self = frame.thisObject();
        ReferenceType declaring = frame.location().declaringType();
        if (self != null) {
            Field field = self.referenceType().fieldByName(name);
            if (field != null) {
                return field.isStatic() ? self.referenceType().getValue(field) : self.getValue(field);
            }
        }
        Field staticField = declaring.fieldByName(name);
        if (staticField != null && staticField.isStatic()) {
            return declaring.getValue(staticField);
        }
        return NOT_FOUND;
    }

    private ClassRef parseClassName() throws EvaluationException {
        int p = pos;
        String candidate = identifier();
        while (true) {
            ReferenceType type = findClass(candidate);
            if (type != null) {
                return new ClassRef(type);
            }
            int q = p = pos;
            while (q < src.length() && Character.isWhitespace(src.charAt(q))) {
                q++;
            }
            if (q + 1 < src.length() && src.charAt(q) == '.' && Character.isJavaIdentifierStart(src.charAt(q + 1))) {
                pos = q + 1;
                candidate = candidate + "." + identifier();
            } else {
                pos = p;
                return null;
            }
        }
    }

    private ReferenceType findClass(String name) {
        List<ReferenceType> types = vm.classesByName(name);
        if (types.isEmpty() && name.indexOf('.') < 0) {
            types = vm.classesByName("java.lang." + name);
        }
        return types.isEmpty() ? null : types.get(0);
    }

    private List<Value> parseArguments() throws EvaluationException, IncompatibleThreadStateException {
        List<Value> arguments = new ArrayList<>();
        if (accept(")")) {
            return arguments;
        }
        do {
            arguments.add(value(parseTernary()));
        } while (accept(","));
        expect(")");
        return arguments;
    }

    private Object parseNumber() throws EvaluationException {
        int start = pos;
        boolean floating = false;
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (Character.isDigit(c) || c == '_') {
                pos++;
            } else if (c == '.' && !floating) {
                floating = true;
                pos++;
            } else if ((c == 'e' || c == 'E') && pos + 1 < src.length()) {
                floating = true;
                pos++;
                if (src.charAt(pos) == '+' || src.charAt(pos) == '-') {
                    pos++;
                }
            } else {
                break;
            }
        }
        String text = src.substring(start, pos).replace("_", "");
        try {
            if (pos < src.length() && (src.charAt(pos) == 'L' || src.charAt(pos) == 'l')) {
                pos++;
                return vm.mirrorOf(Long.parseLong(text));
            }
            if (pos < src.length() && (src.charAt(pos) == 'f' || src.charAt(pos) == 'F')) {
                pos++;
                return vm.mirrorOf(Float.parseFloat(text));
            }
            if (pos < src.length() && (src.charAt(pos) == 'd' || src.charAt(pos) == 'D')) {
                pos++;
                return vm.mirrorOf(Double.parseDouble(text));
            }
            if (floating) {
                return vm.mirrorOf(Double.parseDouble(text));
            }
            long parsed = Long.parseLong(text);
            return parsed > Integer.MAX_VALUE ? vm.mirrorOf(parsed) : vm.mirrorOf((int) parsed);
        } catch (NumberFormatException e) {
            throw new EvaluationException("Invalid number '" + text + "'");
        }
    }

    private String parseQuoted(char quote) throws EvaluationException {
        pos++; // opening quote
        StringBuilder sb = new StringBuilder();
        while (pos < src.length() && src.charAt(pos) != quote) {
            char c = src.charAt(pos++);
            if (c == '\\' && pos < src.length()) {
                char escaped = src.charAt(pos++);
                sb.append(switch (escaped) {
                    case 'n' -> '\n';
                    case 't' -> '\t';
                    case 'r' -> '\r';
                    case '0' -> '\0';
                    default -> escaped;
                });
            } else {
                sb.append(c);
            }
        }
        if (pos >= src.length()) {
            throw new EvaluationException("Unterminated literal");
        }
        pos++; // closing quote
        return sb.toString();
    }

    // --- Members and invocation --- //

    private Object member(Object target, String name) throws EvaluationException {
        if (target instanceof ClassRef classRef) {
            Field field = classRef.type().fieldByName(name);
            if (field == null || !field.isStatic()) {
                throw new EvaluationException("No static field '" + name + "' in " + classRef.type().name());
            }
            return classRef.type().getValue(field);
        }
        Value value = value(target);
        if (value instanceof ArrayReference array && "length".equals(name)) {
            return vm.mirrorOf(array.length());
        }
        if (!(value instanceof ObjectReference object)) {
            throw new EvaluationException("Cannot read '" + name + "' on " + (value == null ? "null" : "a primitive value"));
        }
        Field field = object.referenceType().fieldByName(name);
        if (field == null) {
            throw new EvaluationException("No field '" + name + "' in " + object.referenceType().name());
        }
        return field.isStatic() ? object.referenceType().getValue(field) : object.getValue(field);
    }

    private Object invokeImplicit(String name, List<Value> arguments) throws EvaluationException, IncompatibleThreadStateException {
        StackFrame frame = frame();
        ObjectReference self = frame.thisObject();
        if (self != null) {
            return invoke(self, name, arguments);
        }
        return invoke(new ClassRef(frame.location().declaringType()), name, arguments);
    }

    private Object invoke(Object target, String name, List<Value> arguments) throws EvaluationException {
        ReferenceType type;
        ObjectReference receiver = null;
        if (target instanceof ClassRef classRef) {
            type = classRef.type();
        } else {
            Value value = value(target);
            if (!(value instanceof ObjectReference object)) {
                throw new EvaluationException("Cannot call '" + name + "' on " + (value == null ? "null" : "a primitive value"));
            }
            receiver = object;
            type = object.referenceType();
        }
        // Like Java: among the methods applicable to the argument types (no narrowing), call the most specific one.
        Method chosen = null;
        for (Method method : type.methodsByName(name)) {
            if ((method.isAbstract() && receiver == null) || (receiver == null && !method.isStatic())) {
                continue;
            }
            List<String> parameterTypes = method.argumentTypeNames();
            if (parameterTypes.size() != arguments.size() || !isApplicable(arguments, parameterTypes)) {
                continue;
            }
            if (chosen == null || isMoreSpecific(parameterTypes, chosen.argumentTypeNames())) {
                chosen = method;
            }
        }
        if (chosen == null) {
            throw new EvaluationException("No method " + name + "/" + arguments.size() + " in " + type.name() + " accepts these arguments");
        }
        try {
            List<String> parameterTypes = chosen.argumentTypeNames();
            List<Value> coerced = new ArrayList<>();
            for (int i = 0; i < arguments.size(); i++) {
                coerced.add(coerce(arguments.get(i), parameterTypes.get(i)));
            }
            if (chosen.isStatic()) {
                return ((ClassType) type).invokeMethod(thread, chosen, coerced, ObjectReference.INVOKE_SINGLE_THREADED);
            }
            return receiver.invokeMethod(thread, chosen, coerced, ObjectReference.INVOKE_SINGLE_THREADED);
        } catch (InvocationException e) {
            throw new EvaluationException("Exception in " + name + "(): " + e.exception().referenceType().name());
        } catch (InvalidTypeException | ClassNotLoadedException | IncompatibleThreadStateException e) {
            throw new EvaluationException("Cannot call " + name + "(): " + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : " " + e.getMessage()));
        }
    }

    private static final List<String> NUMERIC_ORDER = List.of("byte", "short", "int", "long", "float", "double");

    /** Widening primitive conversion (byte to short to int to long to float to double, char to int...). */
    private static boolean widens(String from, String to) {
        if (from.equals(to)) {
            return true;
        }
        if ("char".equals(from)) {
            return NUMERIC_ORDER.indexOf(to) >= NUMERIC_ORDER.indexOf("int");
        }
        int source = NUMERIC_ORDER.indexOf(from);
        int target = NUMERIC_ORDER.indexOf(to);
        return source >= 0 && target > source && !"char".equals(to);
    }

    private static boolean isPrimitiveName(String type) {
        return "boolean".equals(type) || "char".equals(type) || NUMERIC_ORDER.contains(type);
    }

    private boolean isApplicable(List<Value> arguments, List<String> parameterTypes) {
        for (int i = 0; i < arguments.size(); i++) {
            Value argument = arguments.get(i);
            String parameter = parameterTypes.get(i);
            if (isPrimitiveName(parameter)) {
                if (!(argument instanceof PrimitiveValue) || !widens(argument.type().name(), parameter)) {
                    return false;
                }
            } else if (argument instanceof PrimitiveValue) {
                return false; // no boxing
            } else if (argument instanceof ObjectReference object && !isSubtype(object.referenceType().name(), parameter)) {
                return false;
            }
        }
        return true;
    }

    private boolean isMoreSpecific(List<String> candidate, List<String> other) {
        for (int i = 0; i < candidate.size(); i++) {
            String from = candidate.get(i);
            String to = other.get(i);
            boolean ok = isPrimitiveName(from) && isPrimitiveName(to) ? widens(from, to) : !isPrimitiveName(from) && !isPrimitiveName(to) && isSubtype(from, to);
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    private boolean isSubtype(String typeName, String superName) {
        if (typeName.equals(superName) || "java.lang.Object".equals(superName)) {
            return true;
        }
        if (typeName.endsWith("[]")) {
            // arrays are Cloneable and Serializable, and covariant when their elements are references
            if ("java.lang.Cloneable".equals(superName) || "java.io.Serializable".equals(superName)) {
                return true;
            }
            String element = typeName.substring(0, typeName.length() - 2);
            return superName.endsWith("[]") && !isPrimitiveName(element)
                && isSubtype(element, superName.substring(0, superName.length() - 2));
        }
        ReferenceType type = findClass(typeName);
        if (type instanceof ClassType classType) {
            for (ClassType current = classType; current != null; current = current.superclass()) {
                if (current.name().equals(superName)) {
                    return true;
                }
                for (com.sun.jdi.InterfaceType declared : current.allInterfaces()) {
                    if (declared.name().equals(superName)) {
                        return true;
                    }
                }
            }
        } else if (type instanceof com.sun.jdi.InterfaceType interfaceType) {
            for (com.sun.jdi.InterfaceType declared : interfaceType.superinterfaces()) {
                if (isSubtype(declared.name(), superName)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Widens primitive arguments to the declared parameter type (int to long, to double...). */
    private Value coerce(Value value, String parameterType) {
        if (!(value instanceof PrimitiveValue primitive) || value instanceof BooleanValue) {
            return value;
        }
        return switch (parameterType) {
            case "long" -> vm.mirrorOf(primitive.longValue());
            case "double" -> vm.mirrorOf(primitive.doubleValue());
            case "float" -> vm.mirrorOf(primitive.floatValue());
            case "int" -> vm.mirrorOf(primitive.intValue());
            case "short" -> vm.mirrorOf(primitive.shortValue());
            case "byte" -> vm.mirrorOf(primitive.byteValue());
            case "char" -> vm.mirrorOf(primitive.charValue());
            default -> value;
        };
    }

    // --- Values and operators --- //

    private StackFrame frame() throws IncompatibleThreadStateException {
        // Invoking a method resumes the thread, which invalidates previously obtained frames.
        return thread.frame(frameIndex);
    }

    private static Value value(Object result) throws EvaluationException {
        if (result instanceof ClassRef classRef) {
            throw new EvaluationException(classRef.type().name() + " is a type, not a value");
        }
        return (Value) result;
    }

    private static boolean asBoolean(Object result) throws EvaluationException {
        if (value(result) instanceof BooleanValue booleanValue) {
            return booleanValue.value();
        }
        throw new EvaluationException("A boolean is required");
    }

    private static double number(Object result) throws EvaluationException {
        if (value(result) instanceof PrimitiveValue primitive && !(primitive instanceof BooleanValue)) {
            return primitive.doubleValue();
        }
        throw new EvaluationException("A number is required");
    }

    private static long numberAsLong(Value value) throws EvaluationException {
        if (value instanceof PrimitiveValue primitive && !(primitive instanceof BooleanValue)) {
            return primitive.longValue();
        }
        throw new EvaluationException("A number is required");
    }

    /** Relational operators: integral operands are compared as integers (a double would lose the low bits of a long). */
    private static boolean compare(String op, Value left, Value right) throws EvaluationException {
        if (isNumeric(left) && isNumeric(right) && !(left instanceof DoubleValue || left instanceof FloatValue
                || right instanceof DoubleValue || right instanceof FloatValue)) {
            long l = numberAsLong(left);
            long r = numberAsLong(right);
            return switch (op) {
                case "<=" -> l <= r;
                case ">=" -> l >= r;
                case "<" -> l < r;
                default -> l > r;
            };
        }
        double l = number(left);
        double r = number(right);
        return switch (op) {
            case "<=" -> l <= r;
            case ">=" -> l >= r;
            case "<" -> l < r;
            default -> l > r;
        };
    }

    private static boolean isNumeric(Value value) {
        return value instanceof PrimitiveValue && !(value instanceof BooleanValue);
    }

    private static boolean valuesEqual(Value left, Value right) {
        if (isNumeric(left) && isNumeric(right)) {
            boolean floating = left instanceof DoubleValue || left instanceof FloatValue || right instanceof DoubleValue || right instanceof FloatValue;
            return floating
                ? ((PrimitiveValue) left).doubleValue() == ((PrimitiveValue) right).doubleValue()
                : ((PrimitiveValue) left).longValue() == ((PrimitiveValue) right).longValue();
        }
        if (left instanceof BooleanValue l && right instanceof BooleanValue r) {
            return l.value() == r.value();
        }
        return left == null ? right == null : left.equals(right);
    }

    private Object arithmetic(String op, Object leftResult, Object rightResult) throws EvaluationException {
        Value left = value(leftResult);
        Value right = value(rightResult);
        if ("+".equals(op) && (left instanceof StringReference || right instanceof StringReference)) {
            return vm.mirrorOf(text(left) + text(right));
        }
        if (!isNumeric(left) || !isNumeric(right)) {
            throw new EvaluationException("Operator " + op + " needs numbers");
        }
        PrimitiveValue l = (PrimitiveValue) left;
        PrimitiveValue r = (PrimitiveValue) right;
        if (left instanceof DoubleValue || right instanceof DoubleValue) {
            return vm.mirrorOf(applyDouble(op, l.doubleValue(), r.doubleValue()));
        }
        if (left instanceof FloatValue || right instanceof FloatValue) {
            return vm.mirrorOf((float) applyDouble(op, l.floatValue(), r.floatValue()));
        }
        if (("/".equals(op) || "%".equals(op)) && r.longValue() == 0) {
            throw new EvaluationException("/ by zero");
        }
        long result = switch (op) {
            case "+" -> l.longValue() + r.longValue();
            case "-" -> l.longValue() - r.longValue();
            case "*" -> l.longValue() * r.longValue();
            case "/" -> l.longValue() / r.longValue();
            default -> l.longValue() % r.longValue();
        };
        return left instanceof LongValue || right instanceof LongValue ? vm.mirrorOf(result) : vm.mirrorOf((int) result);
    }

    private static double applyDouble(String op, double l, double r) {
        return switch (op) {
            case "+" -> l + r;
            case "-" -> l - r;
            case "*" -> l * r;
            case "/" -> l / r;
            default -> l % r;
        };
    }

    private String text(Value value) throws EvaluationException {
        if (value == null) {
            return "null";
        }
        if (value instanceof StringReference string) {
            return string.value();
        }
        if (value instanceof CharValue character) {
            return String.valueOf(character.value());
        }
        if (value instanceof PrimitiveValue) {
            return value.toString();
        }
        Object result = invoke(value, "toString", List.of());
        return result instanceof StringReference string ? string.value() : String.valueOf(result);
    }

    // --- Lexing helpers --- //

    private void skipWs() {
        while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
            pos++;
        }
    }

    private boolean accept(String token) {
        skipWs();
        if (!src.startsWith(token, pos)) {
            return false;
        }
        // Do not read "<" in "<=", "=" in "==", "&" in "&&", "!" in "!=" as the shorter token
        char next = pos + token.length() < src.length() ? src.charAt(pos + token.length()) : '\0';
        if ((token.equals("!") || token.equals("<") || token.equals(">")) && next == '=') {
            return false;
        }
        pos += token.length();
        return true;
    }

    private String acceptAny(String... tokens) {
        for (String token : tokens) {
            if (accept(token)) {
                return token;
            }
        }
        return null;
    }

    private void expect(String token) throws EvaluationException {
        if (!accept(token)) {
            throw new EvaluationException("'" + token + "' expected at position " + pos);
        }
    }

    private String identifier() throws EvaluationException {
        skipWs();
        int start = pos;
        if (pos >= src.length() || !Character.isJavaIdentifierStart(src.charAt(pos))) {
            throw new EvaluationException("Identifier expected at position " + pos);
        }
        while (pos < src.length() && Character.isJavaIdentifierPart(src.charAt(pos))) {
            pos++;
        }
        return src.substring(start, pos);
    }
}
