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

    /**
     * A reference value with the declared type of the variable, field or method that produced it. Java chooses
     * an overload from the declared type ({@code Object value = "x"; pick(value)} calls pick(Object)), the
     * runtime type only decides which implementation runs.
     */
    private record Typed(Value value, String declaredType) {
    }

    private static Object typed(Value value, String declaredType) {
        if (declaredType == null || !(value == null || value instanceof ObjectReference)) {
            return value;
        }
        if (value instanceof ObjectReference object && declaredType.equals(object.referenceType().name())) {
            return value; // same type: nothing to remember
        }
        return new Typed(value, declaredType);
    }

    private static String declaredTypeOf(Object result) {
        return result instanceof Typed typed ? typed.declaredType() : null;
    }

    /** A literal or a constant expression: Java interns constant strings, so "a" + "b" == "ab". */
    private record Constant(Value value) {
    }

    /** Objects created for the evaluation (results of calls, string copies) are only reachable through their mirror: pin them. */
    private final List<ObjectReference> pinned = new ArrayList<>();

    private <T extends Value> T pin(T value) {
        if (value instanceof ObjectReference object) {
            try {
                object.disableCollection();
                pinned.add(object);
            } catch (com.sun.jdi.ObjectCollectedException | UnsupportedOperationException e) {
                // already gone, or not supported by the target: nothing to protect
            }
        }
        return value;
    }

    private void releasePinned() {
        for (ObjectReference object : pinned) {
            try {
                object.enableCollection();
            } catch (com.sun.jdi.ObjectCollectedException | UnsupportedOperationException e) {
                // collected meanwhile
            }
        }
        pinned.clear();
    }

    private record Arguments(List<Value> values, List<String> declaredTypes) {
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
            return value(result);
        } catch (IncompatibleThreadStateException e) {
            throw new EvaluationException("The thread is not suspended");
        } catch (RuntimeException e) {
            String message = e.getMessage();
            throw new EvaluationException(message == null ? e.getClass().getSimpleName() : message);
        } finally {
            evaluator.releasePinned();
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
            if (skipping > 0) {
                return null;
            }
            return promoteConditional(selectTrue ? whenTrue : whenFalse, selectTrue ? whenFalse : whenTrue);
        }
        return condition;
    }

    /**
     * The type of {@code c ? a : b} is the promotion of both branch types: {@code (true ? 1 : 2.0)} is a double.
     * The branch that is not selected is only parsed, so its type is known when it is a literal or a plain name.
     */
    private Object promoteConditional(Object selected, Object other) {
        Value selectedValue = unwrap(selected);
        Value otherValue = unwrap(other);
        if (selectedValue instanceof PrimitiveValue s && otherValue instanceof PrimitiveValue o && isNumeric(s) && isNumeric(o)) {
            if (s instanceof DoubleValue || o instanceof DoubleValue) {
                return vm.mirrorOf(s.doubleValue());
            }
            if (s instanceof FloatValue || o instanceof FloatValue) {
                return vm.mirrorOf(s.floatValue());
            }
            if (s instanceof LongValue || o instanceof LongValue) {
                return vm.mirrorOf(s.longValue());
            }
            Value narrowed = narrowedConditional(s, selected instanceof Constant, o, other instanceof Constant);
            if (narrowed != null) {
                return narrowed;
            }
            if (!s.type().name().equals(o.type().name())) {
                return vm.mirrorOf(s.intValue());
            }
        }
        // references: the conditional has the common type of both branches, whichever one is selected
        String selectedType = referenceTypeOf(selected, selectedValue);
        String otherType = referenceTypeOf(other, otherValue);
        if (selectedType != null && otherType != null && !selectedType.equals(otherType)) {
            return typed(selectedValue, commonType(selectedType, otherType));
        }
        return selected;
    }

    private static boolean fitsIn(String type, int value) {
        return switch (type) {
            case "byte" -> value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE;
            case "short" -> value >= Short.MIN_VALUE && value <= Short.MAX_VALUE;
            case "char" -> value >= Character.MIN_VALUE && value <= Character.MAX_VALUE;
            default -> false;
        };
    }

    private Value converted(PrimitiveValue value, String type) {
        return switch (type) {
            case "byte" -> vm.mirrorOf(value.byteValue());
            case "short" -> vm.mirrorOf(value.shortValue());
            default -> vm.mirrorOf(value.charValue());
        };
    }

    /**
     * JLS 15.25: a byte, short or char branch and an int constant that fits it give the narrower type, and a byte
     * with a short gives a short. Returns null when the usual numeric promotion applies.
     */
    private Value narrowedConditional(PrimitiveValue selected, boolean selectedConstant, PrimitiveValue other, boolean otherConstant) {
        String selectedType = selected.type().name();
        String otherType = other.type().name();
        boolean selectedNarrow = "byte".equals(selectedType) || "short".equals(selectedType) || "char".equals(selectedType);
        boolean otherNarrow = "byte".equals(otherType) || "short".equals(otherType) || "char".equals(otherType);
        if (selectedNarrow && "int".equals(otherType) && otherConstant && fitsIn(selectedType, other.intValue())) {
            return selected;
        }
        if (otherNarrow && "int".equals(selectedType) && selectedConstant && fitsIn(otherType, selected.intValue())) {
            return converted(selected, otherType);
        }
        if (("byte".equals(selectedType) && "short".equals(otherType)) || ("short".equals(selectedType) && "byte".equals(otherType))) {
            return converted(selected, "short");
        }
        return null;
    }

    private static Value unwrap(Object result) {
        return result instanceof Typed t ? t.value() : result instanceof Constant c ? c.value() : result instanceof Value v ? v : null;
    }

    private static String referenceTypeOf(Object result, Value value) {
        String declared = declaredTypeOf(result);
        if (declared != null) {
            return declared;
        }
        return value instanceof ObjectReference object ? object.referenceType().name() : null;
    }

    private String commonType(String first, String second) {
        if (isSubtype(first, second)) {
            return second;
        }
        if (isSubtype(second, first)) {
            return first;
        }
        if (findClass(first) instanceof ClassType type) {
            for (ClassType candidate = type.superclass(); candidate != null; candidate = candidate.superclass()) {
                if (isSubtype(second, candidate.name())) {
                    return candidate.name();
                }
            }
        }
        return "java.lang.Object";
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
            skipWs();
            if (src.startsWith("++", pos) || src.startsWith("--", pos)) {
                // a postfix increment: otherwise the first character would be read as an addition
                throw new EvaluationException("Increment and decrement are not supported");
            }
            String op = acceptAny("+", "-");
            if (op == null) {
                return left;
            }
            // Java converts the left operand to a string before the right one is evaluated (obj + obj.change())
            String leftText = null;
            if (skipping == 0 && "+".equals(op) && !(left instanceof ClassRef) && value(left) instanceof ObjectReference object
                    && !(object instanceof StringReference)) {
                leftText = text(object);
            }
            Object right = parseMultiplicative();
            left = skipping > 0 ? null : arithmetic(op, left, right, leftText);
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
            left = skipping > 0 ? null : arithmetic(op, left, right, null);
        }
    }

    /** -2147483648 and -9223372036854775808L: only the negated literal fits its type (an int and a long). */
    private Object minimumLiteral() {
        skipWs();
        if (src.startsWith("2147483648", pos) && literalEnds(pos + 10)) {
            pos += 10;
            return skipping > 0 ? null : vm.mirrorOf(Integer.MIN_VALUE);
        }
        if (src.startsWith("9223372036854775808", pos) && pos + 19 < src.length()
                && (src.charAt(pos + 19) == 'L' || src.charAt(pos + 19) == 'l') && literalEnds(pos + 20)) {
            pos += 20;
            return skipping > 0 ? null : vm.mirrorOf(Long.MIN_VALUE);
        }
        return NOT_FOUND;
    }

    private boolean literalEnds(int end) {
        return end >= src.length() || !(Character.isLetterOrDigit(src.charAt(end)) || src.charAt(end) == '.' || src.charAt(end) == '_');
    }

    private Object parseUnary() throws EvaluationException, IncompatibleThreadStateException {
        skipWs();
        if (src.startsWith("++", pos) || src.startsWith("--", pos)) {
            // would otherwise be read as two unary operators and silently evaluate something else
            throw new EvaluationException("Increment and decrement are not supported");
        }
        if (accept("!")) {
            Object operand = parseUnary();
            return skipping > 0 ? null : vm.mirrorOf(!asBoolean(operand));
        }
        if (accept("-")) {
            Object minimum = minimumLiteral();
            if (minimum != NOT_FOUND) {
                return minimum;
            }
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
            Object operandResult = parseUnary();
            if (skipping > 0) {
                return null;
            }
            Value operand = value(operandResult);
            if (operand instanceof com.sun.jdi.ByteValue || operand instanceof com.sun.jdi.ShortValue || operand instanceof CharValue) {
                return vm.mirrorOf(((PrimitiveValue) operand).intValue()); // unary numeric promotion
            }
            numberAsLong(operand); // only numbers
            return operand;
        }
        return parsePostfix();
    }

    private Object parsePostfix() throws EvaluationException, IncompatibleThreadStateException {
        Object target = parsePrimary();
        while (true) {
            if (accept(".")) {
                String name = identifier();
                if (accept("(")) {
                    Arguments arguments = parseArguments();
                    target = skipping > 0 ? null : invoke(target, name, arguments.values(), arguments.declaredTypes());
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
                String declaredArray = declaredTypeOf(target);
                Value array = value(target);
                if (!(array instanceof ArrayReference arrayReference)) {
                    throw new EvaluationException("Not an array");
                }
                int i = (int) numberAsLong(value(index));
                if (i < 0 || i >= arrayReference.length()) {
                    throw new EvaluationException("Index " + i + " out of bounds for length " + arrayReference.length());
                }
                // Object[] a = new String[1]: a[0] is an Object for Java, whatever the array really is
                String component = declaredArray != null && declaredArray.endsWith("[]")
                    ? declaredArray.substring(0, declaredArray.length() - 2)
                    : arrayReference.referenceType() instanceof com.sun.jdi.ArrayType arrayType ? arrayType.componentTypeName() : null;
                target = typed(arrayReference.getValue(i), component);
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
            return new Constant((Value) parseNumber());
        }
        if (c == '"') {
            String text = parseQuoted('"');
            return skipping > 0 ? null : new Constant(internedLiteral(text));
        }
        if (c == '\'') {
            String text = parseQuoted('\'');
            if (text.length() != 1) {
                throw new EvaluationException("Invalid character literal");
            }
            return new Constant(vm.mirrorOf(text.charAt(0)));
        }
        if (!Character.isJavaIdentifierStart(c)) {
            throw new EvaluationException("Unexpected '" + c + "' at position " + pos);
        }
        int start = pos;
        String name = identifier();
        switch (name) {
            case "true":
                return new Constant(vm.mirrorOf(true));
            case "false":
                return new Constant(vm.mirrorOf(false));
            case "null":
                return null;
            case "this":
                return typed(frame().thisObject(), frame().location().declaringType().name());
            default:
                break;
        }
        if (accept("(")) {
            Arguments arguments = parseArguments();
            return skipping > 0 ? null : invokeImplicit(name, arguments);
        }
        if (skipping > 0) {
            // not evaluated, but reading a variable has no side effect and tells the type of a ?: branch
            try {
                Object read = resolveName(name);
                return read instanceof Value || read instanceof Typed || read == null ? read : null;
            } catch (EvaluationException | IncompatibleThreadStateException | RuntimeException e) {
                return null;
            }
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

    /** Java interns string literals, so that "a" == "a" and s == "a" hold when s was initialised with that literal. */
    private Value internedLiteral(String text) {
        StringReference literal = pin(vm.mirrorOf(text));
        try {
            List<Method> intern = literal.referenceType().methodsByName("intern", "()Ljava/lang/String;");
            if (!intern.isEmpty()) {
                return literal.invokeMethod(thread, intern.get(0), List.of(), ObjectReference.INVOKE_SINGLE_THREADED);
            }
        } catch (InvalidTypeException | ClassNotLoadedException | IncompatibleThreadStateException | InvocationException e) {
            // keep the plain copy: comparisons by content still work
        }
        return literal;
    }

    private static final Object NOT_FOUND = new Object();

    private Object resolveName(String name) throws EvaluationException, IncompatibleThreadStateException {
        StackFrame frame = frame();
        try {
            LocalVariable variable = frame.visibleVariableByName(name);
            if (variable != null) {
                return typed(frame.getValue(variable), variable.typeName());
            }
        } catch (AbsentInformationException e) {
            // no local variable table: fall through to fields
        }
        ObjectReference self = frame.thisObject();
        ReferenceType declaring = frame.location().declaringType();
        // the field the executing method sees: looked up in its class, not in the (sub)class of the receiver
        Field lexical = declaring.fieldByName(name);
        if (lexical != null) {
            if (lexical.isStatic()) {
                return typed(declaring.getValue(lexical), lexical.typeName());
            }
            if (self != null) {
                return typed(self.getValue(lexical), lexical.typeName());
            }
        }
        if (self != null) {
            Field field = self.referenceType().fieldByName(name);
            if (field != null) {
                return typed(field.isStatic() ? self.referenceType().getValue(field) : self.getValue(field), field.typeName());
            }
        }
        Field staticField = declaring.fieldByName(name);
        if (staticField != null && staticField.isStatic()) {
            return typed(declaring.getValue(staticField), staticField.typeName());
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

    private com.sun.jdi.ClassLoaderReference contextLoader;
    private boolean contextLoaderKnown;

    /** The class loader of the code that is executing: names in the expression are resolved there first. */
    private com.sun.jdi.ClassLoaderReference contextLoader() {
        if (!contextLoaderKnown) {
            contextLoaderKnown = true;
            try {
                contextLoader = frame().location().declaringType().classLoader();
            } catch (IncompatibleThreadStateException | RuntimeException e) {
                contextLoader = null;
            }
        }
        return contextLoader;
    }

    private ReferenceType findClass(String name) {
        List<ReferenceType> types = vm.classesByName(name);
        if (types.isEmpty() && name.indexOf('.') < 0) {
            types = vm.classesByName("java.lang." + name);
        }
        if (types.size() > 1) {
            // the same name loaded by several class loaders: the one the executing code sees
            com.sun.jdi.ClassLoaderReference loader = contextLoader();
            for (ReferenceType candidate : types) {
                if (loader != null && loader.equals(candidate.classLoader())) {
                    return candidate;
                }
            }
        }
        return types.isEmpty() ? null : types.get(0);
    }

    /** The declared type of a receiver: found in the receiver's own hierarchy, so it is the very class it was loaded as. */
    private ReferenceType resolveType(String name, ObjectReference receiver) {
        if (receiver != null && receiver.referenceType() instanceof ClassType classType) {
            for (ClassType current = classType; current != null; current = current.superclass()) {
                if (current.name().equals(name)) {
                    return current;
                }
                for (com.sun.jdi.InterfaceType declared : current.allInterfaces()) {
                    if (declared.name().equals(name)) {
                        return declared;
                    }
                }
            }
        }
        return findClass(name);
    }

    private Arguments parseArguments() throws EvaluationException, IncompatibleThreadStateException {
        List<Value> values = new ArrayList<>();
        List<String> declaredTypes = new ArrayList<>();
        if (accept(")")) {
            return new Arguments(values, declaredTypes);
        }
        do {
            Object argument = parseTernary();
            values.add(value(argument));
            declaredTypes.add(declaredTypeOf(argument));
        } while (accept(","));
        expect(")");
        return new Arguments(values, declaredTypes);
    }

    /** Integer literal in the given radix, with the optional L suffix; hex, octal and binary literals may fill 32 bits. */
    private Value integer(String digits, int radix) {
        boolean isLong = pos < src.length() && (src.charAt(pos) == 'L' || src.charAt(pos) == 'l');
        if (isLong) {
            pos++;
        }
        // 0xFFFFFFFFFFFFFFFFL is the 64-bit pattern of -1L: hex, octal and binary literals are unsigned bit patterns
        long parsed = isLong && radix != 10 ? Long.parseUnsignedLong(digits, radix) : Long.parseLong(digits, radix);
        if (isLong) {
            return vm.mirrorOf(parsed);
        }
        if (radix == 10) {
            return parsed > Integer.MAX_VALUE ? vm.mirrorOf(parsed) : vm.mirrorOf((int) parsed);
        }
        return parsed <= 0xFFFFFFFFL ? vm.mirrorOf((int) parsed) : vm.mirrorOf(parsed);
    }

    private Object parseNumber() throws EvaluationException {
        int start = pos;
        if (src.charAt(pos) == '0' && pos + 1 < src.length() && "xXbB".indexOf(src.charAt(pos + 1)) >= 0) {
            int radix = Character.toLowerCase(src.charAt(pos + 1)) == 'x' ? 16 : 2;
            pos += 2;
            int digitsStart = pos;
            while (pos < src.length() && (Character.digit(src.charAt(pos), radix) >= 0 || src.charAt(pos) == '_')) {
                pos++;
            }
            String digits = src.substring(digitsStart, pos).replace("_", "");
            try {
                return integer(digits, radix);
            } catch (NumberFormatException e) {
                throw new EvaluationException("Invalid number '" + src.substring(start, pos) + "'");
            }
        }
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
            // a leading 0 makes the literal octal, as in Java
            return integer(text, text.length() > 1 && text.charAt(0) == '0' ? 8 : 10);
        } catch (NumberFormatException e) {
            throw new EvaluationException("Invalid number '" + text + "'");
        }
    }

    /** The character of a Java escape sequence; {@code pos} is just after the backslash. */
    private char parseEscape() throws EvaluationException {
        char escaped = src.charAt(pos++);
        switch (escaped) {
            case 'b':
                return '\b';
            case 't':
                return '\t';
            case 'n':
                return '\n';
            case 'f':
                return '\f';
            case 'r':
                return '\r';
            case 's':
                return ' ';
            case '"':
            case '\'':
            case '\\':
                return escaped;
            case 'u':
                while (pos < src.length() && src.charAt(pos) == 'u') {
                    pos++; // several u are allowed
                }
                if (pos + 4 > src.length()) {
                    throw new EvaluationException("Invalid unicode escape");
                }
                try {
                    char decoded = (char) Integer.parseInt(src.substring(pos, pos + 4), 16);
                    pos += 4;
                    return decoded;
                } catch (NumberFormatException e) {
                    throw new EvaluationException("Invalid unicode escape");
                }
            default:
                if (escaped >= '0' && escaped <= '7') {
                    // octal escape: 0 to 377
                    int value = escaped - '0';
                    int maxDigits = escaped <= '3' ? 2 : 1;
                    for (int i = 0; i < maxDigits && pos < src.length() && src.charAt(pos) >= '0' && src.charAt(pos) <= '7'; i++) {
                        value = value * 8 + (src.charAt(pos++) - '0');
                    }
                    return (char) value;
                }
                throw new EvaluationException("Invalid escape sequence \\" + escaped);
        }
    }

    private String parseQuoted(char quote) throws EvaluationException {
        pos++; // opening quote
        StringBuilder sb = new StringBuilder();
        while (pos < src.length() && src.charAt(pos) != quote) {
            char c = src.charAt(pos++);
            if (c == '\\' && pos < src.length()) {
                sb.append(parseEscape());
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
            return typed(classRef.type().getValue(field), field.typeName());
        }
        String declaredName = declaredTypeOf(target);
        Value value = value(target);
        if (value instanceof ArrayReference array && "length".equals(name)) {
            return vm.mirrorOf(array.length());
        }
        if (value == null && declaredName != null) {
            // Java reads a static field through a null reference without complaint
            ReferenceType declaredType = findClass(declaredName);
            Field staticField = declaredType == null ? null : declaredType.fieldByName(name);
            if (staticField != null && staticField.isStatic()) {
                return typed(staticField.declaringType().getValue(staticField), staticField.typeName());
            }
        }
        if (!(value instanceof ObjectReference object)) {
            throw new EvaluationException("Cannot read '" + name + "' on " + (value == null ? "null" : "a primitive value"));
        }
        // Java picks the field from the declared type (Base b = new Child(): b.x is Base.x), the receiver only holds the value
        Field field = null;
        ReferenceType declaredType = declaredName == null ? null : resolveType(declaredName, object);
        if (declaredType != null) {
            field = declaredType.fieldByName(name);
        }
        if (field == null) {
            field = object.referenceType().fieldByName(name);
        }
        if (field == null) {
            throw new EvaluationException("No field '" + name + "' in " + object.referenceType().name());
        }
        return typed(field.isStatic() ? field.declaringType().getValue(field) : object.getValue(field), field.typeName());
    }

    private Object invokeImplicit(String name, Arguments arguments) throws EvaluationException, IncompatibleThreadStateException {
        StackFrame frame = frame();
        ObjectReference self = frame.thisObject();
        if (self != null) {
            // the receiver of an unqualified call has the type of the class that contains the call
            return invoke(typed(self, frame.location().declaringType().name()), name, arguments.values(), arguments.declaredTypes());
        }
        return invoke(new ClassRef(frame.location().declaringType()), name, arguments.values(), arguments.declaredTypes());
    }

    private Object invoke(Object target, String name, List<Value> arguments) throws EvaluationException {
        return invoke(target, name, arguments, null);
    }

    private Object invoke(Object target, String name, List<Value> arguments, List<String> declaredTypes) throws EvaluationException {
        ReferenceType type;
        ObjectReference receiver = null;
        ReferenceType declaredReceiver = null;
        boolean nullReceiver = false;
        if (target instanceof ClassRef classRef) {
            type = classRef.type();
        } else {
            String declaredName = declaredTypeOf(target);
            Value value = value(target);
            if (value instanceof ObjectReference object) {
                receiver = object;
                type = object.referenceType();
                declaredReceiver = declaredName == null ? null : resolveType(declaredName, object);
            } else {
                // a null reference still reaches the static methods of its declared type
                ReferenceType declaredType = value == null && declaredName != null ? findClass(declaredName) : null;
                if (declaredType == null) {
                    throw new EvaluationException("Cannot call '" + name + "' on " + (value == null ? "null" : "a primitive value"));
                }
                type = declaredType;
                nullReceiver = true;
            }
        }
        // Like Java: the signature is chosen among the methods of the declared type of the receiver that are applicable
        // to the arguments (no narrowing), the most specific one; invoking it still dispatches on the runtime type.
        // a null receiver is an instance context: the overload is chosen normally, and rejected below if it needs the instance
        Method chosen = chooseMethod(declaredReceiver != null ? declaredReceiver : type, name, arguments, declaredTypes, receiver == null && !nullReceiver);
        if (chosen == null && declaredReceiver != null) {
            chosen = chooseMethod(type, name, arguments, declaredTypes, false); // e.g. Object methods on an interface type
        }
        if (nullReceiver && (chosen == null || !chosen.isStatic())) {
            throw new EvaluationException("Cannot call '" + name + "' on null");
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
            Value result;
            if (chosen.isStatic()) {
                if (type instanceof com.sun.jdi.InterfaceType interfaceType) {
                    result = interfaceType.invokeMethod(thread, chosen, coerced, ObjectReference.INVOKE_SINGLE_THREADED);
                } else {
                    result = ((ClassType) type).invokeMethod(thread, chosen, coerced, ObjectReference.INVOKE_SINGLE_THREADED);
                }
            } else {
                result = receiver.invokeMethod(thread, chosen, coerced, ObjectReference.INVOKE_SINGLE_THREADED);
            }
            return typed(pin(result), chosen.returnTypeName());
        } catch (InvocationException e) {
            throw new EvaluationException("Exception in " + name + "(): " + e.exception().referenceType().name());
        } catch (InvalidTypeException | ClassNotLoadedException | IncompatibleThreadStateException e) {
            throw new EvaluationException("Cannot call " + name + "(): " + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : " " + e.getMessage()));
        }
    }

    private Method chooseMethod(ReferenceType type, String name, List<Value> arguments, List<String> declaredTypes, boolean staticContext) {
        Method chosen = null;
        for (Method method : type.methodsByName(name)) {
            if (method.isBridge() || (method.isAbstract() && staticContext) || (staticContext && !method.isStatic())) {
                continue; // a bridge only forwards to the source method (and has the erased types)
            }
            List<String> parameterTypes = method.argumentTypeNames();
            if (parameterTypes.size() != arguments.size() || !isApplicable(arguments, declaredTypes, parameterTypes)) {
                continue;
            }
            if (chosen == null
                    || (isMoreSpecific(parameterTypes, chosen.argumentTypeNames()) && !isMoreSpecific(chosen.argumentTypeNames(), parameterTypes))) {
                chosen = method;
            }
        }
        return chosen;
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

    private boolean isApplicable(List<Value> arguments, List<String> declaredTypes, List<String> parameterTypes) {
        for (int i = 0; i < arguments.size(); i++) {
            Value argument = arguments.get(i);
            String parameter = parameterTypes.get(i);
            String declared = declaredTypes == null ? null : declaredTypes.get(i);
            if (isPrimitiveName(parameter)) {
                if (!(argument instanceof PrimitiveValue) || !widens(argument.type().name(), parameter)) {
                    return false;
                }
            } else if (argument instanceof PrimitiveValue) {
                return false; // no boxing
            } else if (argument == null) {
                if (declared != null && !isSubtype(declared, parameter)) {
                    return false; // a null of a declared type still selects the overload by that type
                }
            } else if (argument instanceof ObjectReference object) {
                ReferenceType from = declared != null ? resolveType(declared, object) : object.referenceType();
                if (from == null || !isSubtypeOf(from, parameter)) {
                    return false;
                }
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
        return type != null && isSubtypeOf(type, superName);
    }

    private boolean isSubtypeOf(ReferenceType type, String superName) {
        if (type.name().equals(superName) || "java.lang.Object".equals(superName)) {
            return true;
        }
        if (type instanceof com.sun.jdi.ArrayType) {
            return isSubtype(type.name(), superName);
        }
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
        if (result instanceof Typed typed) {
            return typed.value();
        }
        if (result instanceof Constant constant) {
            return constant.value();
        }
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

    private static float asFloat(Value value) {
        return value instanceof FloatValue f ? f.value() : (float) ((PrimitiveValue) value).longValue();
    }

    /** Relational operators: integral operands are compared as integers (a double would lose the low bits of a long). */
    private static boolean compare(String op, Value originalLeft, Value originalRight) throws EvaluationException {
        Value left = unbox(originalLeft);
        Value right = unbox(originalRight);
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
        if (!(left instanceof DoubleValue || right instanceof DoubleValue) && (left instanceof FloatValue || right instanceof FloatValue)) {
            number(left); // both must be numbers
            number(right);
            float l = asFloat(left);
            float r = asFloat(right);
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

    private static final java.util.Set<String> WRAPPERS = java.util.Set.of("java.lang.Boolean", "java.lang.Byte", "java.lang.Short",
        "java.lang.Character", "java.lang.Integer", "java.lang.Long", "java.lang.Float", "java.lang.Double");

    /** Unboxing conversion: the value of an Integer, Boolean... (anything else is returned as is). */
    private static Value unbox(Value value) {
        if (value instanceof ObjectReference object && WRAPPERS.contains(object.referenceType().name())) {
            Field field = object.referenceType().fieldByName("value");
            if (field != null) {
                return object.getValue(field);
            }
        }
        return value;
    }

    private static boolean valuesEqual(Value originalLeft, Value originalRight) {
        Value left = originalLeft;
        Value right = originalRight;
        if ((left instanceof PrimitiveValue) != (right instanceof PrimitiveValue)) {
            // a primitive on one side: the other side is unboxed (Integer.valueOf(1) == 1); two references stay a reference comparison
            left = unbox(left);
            right = unbox(right);
        }
        if (isNumeric(left) && isNumeric(right)) {
            if (left instanceof DoubleValue || right instanceof DoubleValue) {
                return ((PrimitiveValue) left).doubleValue() == ((PrimitiveValue) right).doubleValue();
            }
            if (left instanceof FloatValue || right instanceof FloatValue) {
                return asFloat(left) == asFloat(right); // Java rounds the other operand to float
            }
            return ((PrimitiveValue) left).longValue() == ((PrimitiveValue) right).longValue();
        }
        if (left instanceof BooleanValue l && right instanceof BooleanValue r) {
            return l.value() == r.value();
        }
        return left == null ? right == null : left.equals(right);
    }

    /** A String operand, also a null one: its declared type makes + a concatenation. */
    private static boolean isString(Object result) {
        return result instanceof StringReference
            || result instanceof Constant constant && constant.value() instanceof StringReference
            || result instanceof Typed typed && (typed.value() instanceof StringReference || "java.lang.String".equals(typed.declaredType()));
    }

    private Object arithmetic(String op, Object leftResult, Object rightResult, String leftText) throws EvaluationException {
        boolean constant = leftResult instanceof Constant && rightResult instanceof Constant;
        Value result = arithmeticValue(op, leftResult, rightResult, constant, leftText);
        return constant ? new Constant(result) : result;
    }

    private Value arithmeticValue(String op, Object leftResult, Object rightResult, boolean constant, String leftText) throws EvaluationException {
        Value left = value(leftResult);
        Value right = value(rightResult);
        if ("+".equals(op) && (isString(leftResult) || isString(rightResult))) {
            String concatenated = (leftText != null ? leftText : text(left)) + text(right);
            return constant ? internedLiteral(concatenated) : pin(vm.mirrorOf(concatenated));
        }
        left = unbox(left);
        right = unbox(right);
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
        Value result = value(invoke(value, "toString", List.of())); // unwrapped: the call is typed (a null result is "null")
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
