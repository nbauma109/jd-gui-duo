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
            } catch (com.sun.jdi.ObjectCollectedException | UnsupportedOperationException _) {
                // already gone, or not supported by the target: nothing to protect
            }
        }
        return value;
    }

    private void releasePinned() {
        for (ObjectReference object : pinned) {
            try {
                object.enableCollection();
            } catch (com.sun.jdi.ObjectCollectedException | UnsupportedOperationException _) {
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
            if (result instanceof ClassRef(ReferenceType type)) {
                throw new EvaluationException(type.name() + " is a type, not a value");
            }
            return value(result);
        } catch (IncompatibleThreadStateException _) {
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
        // a numeric wrapper next to a primitive (or another wrapper type) is unboxed first: Integer and int give an int
        boolean sameWrapper = selectedValue instanceof ObjectReference a && otherValue instanceof ObjectReference b
            && a.referenceType().equals(b.referenceType());
        Value selectedUnboxed = sameWrapper ? selectedValue : unbox(selectedValue);
        Value otherUnboxed = sameWrapper ? otherValue : unbox(otherValue);
        if (selectedUnboxed instanceof PrimitiveValue s && otherUnboxed instanceof PrimitiveValue o && isNumeric(s) && isNumeric(o)) {
            Value promoted = promoteNumeric(s, selected instanceof Constant, o, other instanceof Constant);
            if (promoted != null) {
                return promoted;
            }
            if (selectedValue instanceof ObjectReference) {
                return s;
            }
        }
        // Boolean next to boolean (or a null-free mix of Boolean and boolean) is a primitive boolean
        if (!sameWrapper && selectedUnboxed instanceof BooleanValue && otherUnboxed instanceof BooleanValue) {
            return selectedUnboxed;
        }
        // references: the conditional has the common type of both branches, whichever one is selected
        String selectedType = referenceTypeOf(selected, selectedValue);
        String otherType = referenceTypeOf(other, otherValue);
        if (selectedType != null && otherType != null && !selectedType.equals(otherType)) {
            return typed(selectedValue, commonType(selectedType, otherType));
        }
        return selected;
    }

    /** Binary numeric promotion of the two branches; null when the selected branch keeps its own type. */
    private Value promoteNumeric(PrimitiveValue s, boolean selectedConstant, PrimitiveValue o, boolean otherConstant) {
        if (s instanceof DoubleValue || o instanceof DoubleValue) {
            return vm.mirrorOf(s.doubleValue());
        }
        if (s instanceof FloatValue || o instanceof FloatValue) {
            return vm.mirrorOf(s.floatValue());
        }
        if (s instanceof LongValue || o instanceof LongValue) {
            return vm.mirrorOf(s.longValue());
        }
        Value narrowed = narrowedConditional(s, selectedConstant, o, otherConstant);
        if (narrowed != null) {
            return narrowed;
        }
        return s.type().name().equals(o.type().name()) ? null : vm.mirrorOf(s.intValue());
    }

    private static boolean fitsIn(String type, int value) {
        return switch (type) {
            case "byte" -> value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE;
            case SHORT_TYPE -> value >= Short.MIN_VALUE && value <= Short.MAX_VALUE;
            case "char" -> value >= Character.MIN_VALUE && value <= Character.MAX_VALUE;
            default -> false;
        };
    }

    private Value converted(PrimitiveValue value, String type) {
        return switch (type) {
            case "byte" -> vm.mirrorOf(value.byteValue());
            case SHORT_TYPE -> vm.mirrorOf(value.shortValue());
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
        boolean selectedNarrow = "byte".equals(selectedType) || SHORT_TYPE.equals(selectedType) || "char".equals(selectedType);
        boolean otherNarrow = "byte".equals(otherType) || SHORT_TYPE.equals(otherType) || "char".equals(otherType);
        if (selectedNarrow && "int".equals(otherType) && otherConstant && fitsIn(selectedType, other.intValue())) {
            return selected;
        }
        if (otherNarrow && "int".equals(selectedType) && selectedConstant && fitsIn(otherType, selected.intValue())) {
            return converted(selected, otherType);
        }
        if (("byte".equals(selectedType) && SHORT_TYPE.equals(otherType)) || (SHORT_TYPE.equals(selectedType) && "byte".equals(otherType))) {
            return converted(selected, SHORT_TYPE);
        }
        return null;
    }

    private static Value unwrap(Object result) {
        if (result instanceof Typed(Value typedValue, _)) {
            return typedValue;
        }
        if (result instanceof Constant(Value constantValue)) {
            return constantValue;
        }
        return result instanceof Value v ? v : null;
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
        if (first.contains("&") || second.contains("&")) {
            return OBJECT_TYPE;
        }
        return findClass(first) instanceof ClassType type ? commonSuperType(type, second) : OBJECT_TYPE;
    }

    private String commonSuperType(ClassType type, String second) {
        for (ClassType candidate = type.superclass(); candidate != null && !OBJECT_TYPE.equals(candidate.name()); candidate = candidate.superclass()) {
            if (isSubtype(second, candidate.name())) {
                return candidate.name();
            }
        }
        // only Object in common: the type is the intersection of the interfaces both classes implement
        List<String> shared = new ArrayList<>();
        for (com.sun.jdi.InterfaceType declared : type.allInterfaces()) {
            if (isSubtype(second, declared.name())) {
                shared.add(declared.name());
            }
        }
        return shared.isEmpty() ? OBJECT_TYPE : String.join("&", shared);
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
            left = skipping > 0 ? skippedArithmetic(op, left, right) : arithmetic(op, left, right, leftText);
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
            left = skipping > 0 ? skippedArithmetic(op, left, right) : arithmetic(op, left, right, null);
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
            return parseNegation();
        }
        if (accept("+")) {
            return parseUnaryPlus();
        }
        return parsePostfix();
    }

    private Object parseNegation() throws EvaluationException, IncompatibleThreadStateException {
        Object minimum = minimumLiteral();
        if (minimum != NOT_FOUND) {
            return minimum;
        }
        Object operandResult = parseUnary();
        if (skipping > 0 && !(unwrap(operandResult) instanceof PrimitiveValue)) {
            return null; // a number can be negated without side effect: the type of -2.0 matters in a skipped branch
        }
        Value operand = unbox(value(operandResult)); // unary operators unbox: -boxed, +boxed
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

    private Object parseUnaryPlus() throws EvaluationException, IncompatibleThreadStateException {
        Object operandResult = parseUnary();
        if (skipping > 0) {
            return null;
        }
        Value operand = unbox(value(operandResult)); // unary operators unbox: -boxed, +boxed
        if (operand instanceof com.sun.jdi.ByteValue || operand instanceof com.sun.jdi.ShortValue || operand instanceof CharValue) {
            return vm.mirrorOf(((PrimitiveValue) operand).intValue()); // unary numeric promotion
        }
        numberAsLong(operand); // only numbers
        return operand;
    }

    private Object parsePostfix() throws EvaluationException, IncompatibleThreadStateException {
        Object target = parsePrimary();
        while (true) {
            if (accept(".")) {
                target = parseMemberAccess(target);
            } else if (accept("[")) {
                target = parseIndex(target);
            } else {
                return target;
            }
        }
    }

    private Object parseMemberAccess(Object target) throws EvaluationException, IncompatibleThreadStateException {
        String name = identifier();
        if (accept("(")) {
            Arguments arguments = parseArguments();
            return skipping > 0 ? skippedInvoke(target, name, arguments) : invoke(target, name, arguments.values(), arguments.declaredTypes());
        }
        return skipping > 0 ? skippedMember(target, name) : member(target, name);
    }

    private Object parseIndex(Object target) throws EvaluationException, IncompatibleThreadStateException {
        Object index = parseTernary();
        expect("]");
        if (skipping > 0) {
            return null;
        }
        String declaredArray = declaredTypeOf(target);
        if (!(value(target) instanceof ArrayReference arrayReference)) {
            throw new EvaluationException("Not an array");
        }
        int i = (int) numberAsLong(unbox(value(index)));
        if (i < 0 || i >= arrayReference.length()) {
            throw new EvaluationException("Index " + i + " out of bounds for length " + arrayReference.length());
        }
        return typed(arrayReference.getValue(i), componentTypeOf(declaredArray, arrayReference));
    }

    /** Object[] a = new String[1]: a[0] is an Object for Java, whatever the array really is. */
    private static String componentTypeOf(String declaredArray, ArrayReference array) {
        if (declaredArray != null && declaredArray.endsWith("[]")) {
            return declaredArray.substring(0, declaredArray.length() - 2);
        }
        return array.referenceType() instanceof com.sun.jdi.ArrayType arrayType ? arrayType.componentTypeName() : null;
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
        Object literal = parseLiteral(c);
        if (literal != NOT_FOUND) {
            return literal;
        }
        if (!Character.isJavaIdentifierStart(c)) {
            throw new EvaluationException("Unexpected '" + c + "' at position " + pos);
        }
        return parseNameOrCall();
    }

    /** A number, string or character literal starting with {@code c}; NOT_FOUND when {@code c} starts none of them. */
    private Object parseLiteral(char c) throws EvaluationException {
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
        return NOT_FOUND;
    }

    private Object parseNameOrCall() throws EvaluationException, IncompatibleThreadStateException {
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
            return skipping > 0 ? skippedInvokeImplicit(name, arguments) : invokeImplicit(name, arguments, false);
        }
        if (skipping > 0) {
            // not evaluated, but reading a variable has no side effect and tells the type of a ?: branch
            try {
                Object read = resolveName(name);
                return read instanceof Value || read instanceof Typed || read == null ? read : null;
            } catch (IncompatibleThreadStateException | RuntimeException _) {
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
        } catch (InvalidTypeException | ClassNotLoadedException | IncompatibleThreadStateException | InvocationException _) {
            // keep the plain copy: comparisons by content still work
        }
        return literal;
    }

    private static final String SHORT_TYPE = "short";
    private static final String OBJECT_TYPE = "java.lang.Object";
    private static final String DOUBLE_TYPE = "double";
    private static final String FLOAT_TYPE = "float";
    private static final Object NOT_FOUND = new Object();

    private Object resolveName(String name) throws IncompatibleThreadStateException {
        StackFrame frame = frame();
        try {
            LocalVariable variable = frame.visibleVariableByName(name);
            if (variable != null) {
                return typed(frame.getValue(variable), variable.typeName());
            }
        } catch (AbsentInformationException _) {
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
        StringBuilder candidate = new StringBuilder(identifier());
        while (true) {
            ReferenceType type = findClass(candidate.toString());
            if (type != null) {
                return new ClassRef(type);
            }
            int q = p = pos;
            while (q < src.length() && Character.isWhitespace(src.charAt(q))) {
                q++;
            }
            if (q + 1 < src.length() && src.charAt(q) == '.' && Character.isJavaIdentifierStart(src.charAt(q + 1))) {
                pos = q + 1;
                candidate.append('.').append(identifier());
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
            } catch (IncompatibleThreadStateException | RuntimeException _) {
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
    private ReferenceType resolveType(String declaredName, ObjectReference receiver) {
        // an intersection type (A&B): the members of its first part, the others are reached through the runtime type
        String name = declaredName.indexOf('&') < 0 ? declaredName : declaredName.substring(0, declaredName.indexOf('&'));
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
            return parseRadixNumber(start);
        }
        boolean floating = skipDecimalDigits();
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
        } catch (NumberFormatException _) {
            throw new EvaluationException("Invalid number '" + text + "'");
        }
    }

    /** 0x1F and 0b101: {@code pos} is on the leading 0. */
    private Object parseRadixNumber(int start) throws EvaluationException {
        int radix = Character.toLowerCase(src.charAt(pos + 1)) == 'x' ? 16 : 2;
        pos += 2;
        int digitsStart = pos;
        while (pos < src.length() && (Character.digit(src.charAt(pos), radix) >= 0 || src.charAt(pos) == '_')) {
            pos++;
        }
        String digits = src.substring(digitsStart, pos).replace("_", "");
        try {
            return integer(digits, radix);
        } catch (NumberFormatException _) {
            throw new EvaluationException("Invalid number '" + src.substring(start, pos) + "'");
        }
    }

    /** Moves past the digits, the fraction and the exponent of a decimal literal; true when it is a floating-point one. */
    private boolean skipDecimalDigits() {
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
        return floating;
    }

    /** The character of a Java escape sequence; {@code pos} is just after the backslash. */
    private char parseEscape() throws EvaluationException {
        char escaped = src.charAt(pos++);
        return switch (escaped) {
            case 'b' -> '\b';
            case 't' -> '\t';
            case 'n' -> '\n';
            case 'f' -> '\f';
            case 'r' -> '\r';
            case 's' -> ' ';
            case '"', '\'', '\\' -> escaped;
            case 'u' -> parseUnicodeEscape();
            default -> {
                if (escaped < '0' || escaped > '7') {
                    throw new EvaluationException("Invalid escape sequence \\" + escaped);
                }
                yield parseOctalEscape(escaped);
            }
        };
    }

    private char parseUnicodeEscape() throws EvaluationException {
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
        } catch (NumberFormatException _) {
            throw new EvaluationException("Invalid unicode escape");
        }
    }

    /** Octal escape: 0 to 377. */
    private char parseOctalEscape(char first) {
        int value = first - '0';
        int maxDigits = first <= '3' ? 2 : 1;
        for (int i = 0; i < maxDigits && pos < src.length() && src.charAt(pos) >= '0' && src.charAt(pos) <= '7'; i++) {
            value = value * 8 + (src.charAt(pos++) - '0');
        }
        return (char) value;
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
        if (target instanceof ClassRef(ReferenceType type)) {
            Field field = type.fieldByName(name);
            if (field == null || !field.isStatic()) {
                throw new EvaluationException("No static field '" + name + "' in " + type.name());
            }
            return typed(type.getValue(field), field.typeName());
        }
        String declaredName = declaredTypeOf(target);
        Value value = value(target);
        if (value instanceof ArrayReference array && "length".equals(name)) {
            return vm.mirrorOf(array.length());
        }
        Field staticField = value == null && declaredName != null ? staticFieldOf(declaredName, name) : null;
        if (staticField != null) {
            return typed(staticField.declaringType().getValue(staticField), staticField.typeName());
        }
        if (!(value instanceof ObjectReference object)) {
            throw new EvaluationException("Cannot read '" + name + "' on " + (value == null ? "null" : "a primitive value"));
        }
        Field field = instanceField(object, declaredName, name);
        if (field == null) {
            throw new EvaluationException("No field '" + name + "' in " + object.referenceType().name());
        }
        return typed(field.isStatic() ? field.declaringType().getValue(field) : object.getValue(field), field.typeName());
    }

    /** Java reads a static field through a null reference without complaint. */
    private Field staticFieldOf(String declaredName, String name) {
        ReferenceType declaredType = findClass(declaredName);
        Field field = declaredType == null ? null : declaredType.fieldByName(name);
        return field != null && field.isStatic() ? field : null;
    }

    /** Java picks the field from the declared type (Base b = new Child(): b.x is Base.x), the receiver only holds the value. */
    private Field instanceField(ObjectReference object, String declaredName, String name) {
        ReferenceType declaredType = declaredName == null ? null : resolveType(declaredName, object);
        Field field = declaredType == null ? null : declaredType.fieldByName(name);
        return field != null ? field : object.referenceType().fieldByName(name);
    }

    private Object invokeImplicit(String name, Arguments arguments, boolean dryRun) throws EvaluationException, IncompatibleThreadStateException {
        StackFrame frame = frame();
        ObjectReference self = frame.thisObject();
        if (self != null) {
            // the receiver of an unqualified call has the type of the class that contains the call
            return invoke(typed(self, frame.location().declaringType().name()), name, arguments.values(), arguments.declaredTypes(), dryRun);
        }
        return invoke(new ClassRef(frame.location().declaringType()), name, arguments.values(), arguments.declaredTypes(), dryRun);
    }

    // A branch that is not selected is not executed, but its type still decides the type of the conditional:
    // reading variables and doing arithmetic on numbers has no side effect, and a call is only resolved (not run).

    private Object skippedArithmetic(String op, Object left, Object right) {
        try {
            if (unwrap(left) instanceof PrimitiveValue && unwrap(right) instanceof PrimitiveValue) {
                return arithmetic(op, left, right, null);
            }
        } catch (EvaluationException | RuntimeException _) {
            // e.g. a division by zero: the type is unknown
        }
        return null;
    }

    private Object skippedMember(Object target, String name) {
        try {
            return member(target, name);
        } catch (EvaluationException | RuntimeException _) {
            return null;
        }
    }

    private Object skippedInvoke(Object target, String name, Arguments arguments) {
        try {
            return invoke(target, name, arguments.values(), arguments.declaredTypes(), true);
        } catch (EvaluationException | RuntimeException _) {
            return null;
        }
    }

    private Object skippedInvokeImplicit(String name, Arguments arguments) {
        try {
            return invokeImplicit(name, arguments, true);
        } catch (EvaluationException | IncompatibleThreadStateException | RuntimeException _) {
            return null;
        }
    }

    /** What a call that was not run stands for: a zero of its primitive return type, or a null of its reference type. */
    private Object placeholder(String returnType) {
        return switch (returnType) {
            case "int" -> vm.mirrorOf(0);
            case "long" -> vm.mirrorOf(0L);
            case DOUBLE_TYPE -> vm.mirrorOf(0.0);
            case FLOAT_TYPE -> vm.mirrorOf(0f);
            case "boolean" -> vm.mirrorOf(false);
            case "byte" -> vm.mirrorOf((byte) 0);
            case SHORT_TYPE -> vm.mirrorOf((short) 0);
            case "char" -> vm.mirrorOf('\0');
            case "void" -> null;
            default -> typed(null, returnType);
        };
    }

    private Object invoke(Object target, String name, List<Value> arguments) throws EvaluationException {
        return invoke(target, name, arguments, null);
    }

    private Object invoke(Object target, String name, List<Value> arguments, List<String> declaredTypes) throws EvaluationException {
        return invoke(target, name, arguments, declaredTypes, false);
    }

    private Object invoke(Object target, String name, List<Value> arguments, List<String> declaredTypes, boolean dryRun) throws EvaluationException {
        Receiver receiver = resolveReceiver(target, name);
        ReferenceType type = receiver.type();
        ReferenceType declaredReceiver = receiver.declared();
        boolean nullReceiver = receiver.nullReceiver();
        // Like Java: the signature is chosen among the methods of the declared type of the receiver that are applicable
        // to the arguments (no narrowing), the most specific one; invoking it still dispatches on the runtime type.
        // a null receiver is an instance context: the overload is chosen normally, and rejected below if it needs the instance
        Method chosen = chooseMethod(declaredReceiver != null ? declaredReceiver : type, name, arguments, declaredTypes, receiver.object() == null && !nullReceiver);
        if (chosen == null && declaredReceiver != null) {
            chosen = chooseMethod(type, name, arguments, declaredTypes, false); // e.g. Object methods on an interface type
        }
        if (nullReceiver && (chosen == null || !chosen.isStatic())) {
            throw new EvaluationException("Cannot call '" + name + "' on null");
        }
        if (chosen == null) {
            throw new EvaluationException("No method " + name + "/" + arguments.size() + " in " + type.name() + " accepts these arguments");
        }
        String returnType = returnTypeOf(target, receiver, chosen);
        if (dryRun) {
            return placeholder(returnType);
        }
        try {
            return callRetrying(receiver, chosen, arguments, returnType);
        } catch (InvocationException e) {
            throw new EvaluationException("Exception in " + name + "(): " + e.exception().referenceType().name());
        } catch (InvalidTypeException | ClassNotLoadedException | IncompatibleThreadStateException e) {
            throw new EvaluationException("Cannot call " + name + "(): " + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : " " + e.getMessage()));
        }
    }

    /** The receiver of a call: the type that declares the method, the object (null for a static call) and its declared type. */
    private record Receiver(ReferenceType type, ObjectReference object, ReferenceType declared, boolean nullReceiver) {
    }

    /** The type whose methods are looked up: an array has none of its own, the ones of Object are run on the array. */
    private ReferenceType declaredReceiver(ObjectReference object, String declaredName) {
        if (object instanceof ArrayReference) {
            return findClass(OBJECT_TYPE);
        }
        return declaredName == null ? null : resolveType(declaredName, object);
    }

    private Receiver resolveReceiver(Object target, String name) throws EvaluationException {
        if (target instanceof ClassRef(ReferenceType type)) {
            return new Receiver(type, null, null, false);
        }
        String declaredName = declaredTypeOf(target);
        Value value = value(target);
        if (value instanceof ObjectReference object) {
            // an array has no methods of its own: the ones of Object are looked up in Object and run on the array
            return new Receiver(object.referenceType(), object, declaredReceiver(object, declaredName), false);
        }
        // a null reference still reaches the static methods of its declared type
        ReferenceType declaredType = value == null && declaredName != null ? findClass(declaredName) : null;
        if (declaredType == null) {
            throw new EvaluationException("Cannot call '" + name + "' on " + (value == null ? "null" : "a primitive value"));
        }
        return new Receiver(declaredType, null, null, true);
    }

    /** The clone of an array has the type of the array, not the Object of the method that was found. */
    private String returnTypeOf(Object target, Receiver receiver, Method chosen) {
        if (receiver.object() instanceof ArrayReference array && "clone".equals(chosen.name()) && chosen.argumentTypeNames().isEmpty()) {
            String declared = declaredTypeOf(target);
            return declared != null && declared.endsWith("[]") ? declared : array.referenceType().name();
        }
        return chosen.returnTypeName();
    }

    private Object callRetrying(Receiver receiver, Method chosen, List<Value> arguments, String returnType) throws InvalidTypeException,
            ClassNotLoadedException, IncompatibleThreadStateException, InvocationException {
        try {
            return call(receiver, chosen, arguments, returnType);
        } catch (ClassNotLoadedException _) {
            // the loader of the method never resolved one of its parameter types (java.lang.CharSequence...): make it do so
            preloadParameterTypes(chosen);
        }
        return call(receiver, chosen, arguments, returnType);
    }

    private Object call(Receiver receiver, Method chosen, List<Value> arguments, String returnType) throws InvalidTypeException,
            ClassNotLoadedException, IncompatibleThreadStateException, InvocationException {
        List<String> parameterTypes = chosen.argumentTypeNames();
        List<Value> coerced = new ArrayList<>();
        for (int i = 0; i < arguments.size(); i++) {
            coerced.add(coerce(arguments.get(i), parameterTypes.get(i)));
        }
        Value result;
        if (!chosen.isStatic()) {
            result = receiver.object().invokeMethod(thread, chosen, coerced, ObjectReference.INVOKE_SINGLE_THREADED);
        } else if (receiver.type() instanceof com.sun.jdi.InterfaceType interfaceType) {
            result = interfaceType.invokeMethod(thread, chosen, coerced, ObjectReference.INVOKE_SINGLE_THREADED);
        } else {
            result = ((ClassType) receiver.type()).invokeMethod(thread, chosen, coerced, ObjectReference.INVOKE_SINGLE_THREADED);
        }
        return typed(pin(result), returnType);
    }

    /** Class.forName(type, false, loader) for the parameter types: afterwards JDI sees them as loaded by that loader. */
    private void preloadParameterTypes(Method method) {
        List<ReferenceType> classClasses = vm.classesByName("java.lang.Class");
        if (classClasses.isEmpty() || !(classClasses.get(0) instanceof ClassType classClass)) {
            return;
        }
        Method forName = classClass.concreteMethodByName("forName", "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;");
        if (forName == null) {
            return;
        }
        com.sun.jdi.ClassLoaderReference loader = method.declaringType().classLoader();
        for (String typeName : method.argumentTypeNames()) {
            if (isPrimitiveName(typeName) || typeName.endsWith("[]")) {
                continue;
            }
            try {
                classClass.invokeMethod(thread, forName, java.util.Arrays.<Value>asList(vm.mirrorOf(typeName), vm.mirrorOf(false), loader), ObjectReference.INVOKE_SINGLE_THREADED);
            } catch (InvalidTypeException | ClassNotLoadedException | IncompatibleThreadStateException | InvocationException | RuntimeException _) {
                // not loadable that way: the retry reports the original failure
            }
        }
    }

    /** The most specific applicable method; like the compiler, an evaluation error when none of them is more specific than all the others. */
    private Method chooseMethod(ReferenceType type, String name, List<Value> arguments, List<String> declaredTypes, boolean staticContext) throws EvaluationException {
        List<Method> candidates = new ArrayList<>();
        for (Method method : type.methodsByName(name)) {
            if (isCandidate(method, arguments, declaredTypes, staticContext)) {
                candidates.add(method);
            }
        }
        for (Method candidate : candidates) {
            if (candidates.stream().allMatch(other -> isMoreSpecific(candidate.argumentTypeNames(), other.argumentTypeNames()))) {
                return candidate;
            }
        }
        if (candidates.size() > 1) {
            throw new EvaluationException("The call of " + name + " is ambiguous");
        }
        return null;
    }

    private boolean isCandidate(Method method, List<Value> arguments, List<String> declaredTypes, boolean staticContext) {
        // a bridge only forwards to the source method (and has the erased types)
        if (method.isBridge() || (staticContext && (method.isAbstract() || !method.isStatic()))) {
            return false;
        }
        List<String> parameterTypes = method.argumentTypeNames();
        return parameterTypes.size() == arguments.size() && isApplicable(arguments, declaredTypes, parameterTypes);
    }

    private static final List<String> NUMERIC_ORDER = List.of("byte", SHORT_TYPE, "int", "long", FLOAT_TYPE, DOUBLE_TYPE);

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
            if (!isApplicable(arguments.get(i), declaredTypes == null ? null : declaredTypes.get(i), parameterTypes.get(i))) {
                return false;
            }
        }
        return true;
    }

    private boolean isApplicable(Value argument, String declared, String parameter) {
        if (isPrimitiveName(parameter)) {
            return argument instanceof PrimitiveValue && widens(argument.type().name(), parameter);
        }
        if (argument instanceof PrimitiveValue) {
            return false; // no boxing
        }
        if (argument == null) {
            return declared == null || isSubtype(declared, parameter); // a null of a declared type still selects the overload by that type
        }
        return !(argument instanceof ObjectReference object) || isObjectApplicable(object, declared, parameter);
    }

    private boolean isObjectApplicable(ObjectReference object, String declared, String parameter) {
        if (declared == null) {
            return isSubtypeOf(object.referenceType(), parameter);
        }
        // the declared type of an intersection is accepted when one of its parts is
        for (String part : declared.split("&")) {
            ReferenceType from = resolveType(part, object);
            if (from != null && isSubtypeOf(from, parameter)) {
                return true;
            }
        }
        return false;
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
        if (typeName.equals(superName) || OBJECT_TYPE.equals(superName)) {
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
        if (type.name().equals(superName) || OBJECT_TYPE.equals(superName)) {
            return true;
        }
        if (type instanceof com.sun.jdi.ArrayType) {
            return isSubtype(type.name(), superName);
        }
        if (type instanceof ClassType classType) {
            return isClassSubtypeOf(classType, superName);
        }
        if (type instanceof com.sun.jdi.InterfaceType interfaceType) {
            return interfaceType.superinterfaces().stream().anyMatch(declared -> isSubtype(declared.name(), superName));
        }
        return false;
    }

    private static boolean isClassSubtypeOf(ClassType classType, String superName) {
        for (ClassType current = classType; current != null; current = current.superclass()) {
            if (current.name().equals(superName) || current.allInterfaces().stream().anyMatch(declared -> declared.name().equals(superName))) {
                return true;
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
            case DOUBLE_TYPE -> vm.mirrorOf(primitive.doubleValue());
            case FLOAT_TYPE -> vm.mirrorOf(primitive.floatValue());
            case "int" -> vm.mirrorOf(primitive.intValue());
            case SHORT_TYPE -> vm.mirrorOf(primitive.shortValue());
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
        if (result instanceof Typed(Value typedValue, _)) {
            return typedValue;
        }
        if (result instanceof Constant(Value constantValue)) {
            return constantValue;
        }
        if (result instanceof ClassRef(ReferenceType type)) {
            throw new EvaluationException(type.name() + " is a type, not a value");
        }
        return (Value) result;
    }

    private static boolean asBoolean(Object result) throws EvaluationException {
        if (unbox(value(result)) instanceof BooleanValue booleanValue) {
            return booleanValue.value();
        }
        throw new EvaluationException("A boolean is required");
    }

    private static double number(Object result) throws EvaluationException {
        if (unbox(value(result)) instanceof PrimitiveValue primitive && !(primitive instanceof BooleanValue)) {
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

    private static boolean valuesEqual(Value originalLeft, Value originalRight) throws EvaluationException {
        Value left = originalLeft;
        Value right = originalRight;
        if ((left instanceof PrimitiveValue) != (right instanceof PrimitiveValue)) {
            // a primitive on one side: the other side is unboxed (Integer.valueOf(1) == 1); two references stay a reference comparison
            left = unbox(left);
            right = unbox(right);
            if (left == null || right == null) {
                throw new EvaluationException("NullPointerException: a null cannot be unboxed");
            }
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
            || result instanceof Constant(Value constantValue) && constantValue instanceof StringReference
            || result instanceof Typed(Value typedValue, String declaredType) && (typedValue instanceof StringReference || "java.lang.String".equals(declaredType));
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
        return numericArithmetic(op, unbox(left), unbox(right));
    }

    private Value numericArithmetic(String op, Value left, Value right) throws EvaluationException {
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
