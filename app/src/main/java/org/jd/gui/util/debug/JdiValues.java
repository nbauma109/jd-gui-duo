/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */
package org.jd.gui.util.debug;

import org.jd.gui.model.debug.DebugVariableNode;

import com.sun.jdi.ArrayReference;
import com.sun.jdi.CharValue;
import com.sun.jdi.Field;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.StringReference;
import com.sun.jdi.Value;
import com.sun.jdi.VoidValue;

import java.util.ArrayList;
import java.util.List;

/**
 * Formats JDI values and builds variable-tree nodes the way Eclipse's Variables view does:
 * {@code "text" (id=12)}, {@code Type (id=7)}, {@code int[3] (id=9)}, {@code 'c'}.
 */
public final class JdiValues {
    private static final int MAX_STRING_LENGTH = 1000;
    private static final int MAX_ARRAY_ELEMENTS = 200;

    private JdiValues() {
    }

    public static String format(Value value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof StringReference string) {
            return quote(string.value()) + " (id=" + string.uniqueID() + ")";
        }
        if (value instanceof ArrayReference array) {
            String typeName = array.referenceType().name();
            int bracket = typeName.indexOf("[]");
            String withLength = bracket < 0 ? typeName : typeName.substring(0, bracket) + "[" + array.length() + "]" + typeName.substring(bracket + 2);
            return simpleName(withLength) + " (id=" + array.uniqueID() + ")";
        }
        if (value instanceof ObjectReference object) {
            // Eclipse puts two spaces between the type name and the id of plain objects (not of strings and arrays)
            return simpleName(object.referenceType().name()) + "  (id=" + object.uniqueID() + ")";
        }
        if (value instanceof CharValue character) {
            return "'" + character.value() + "'";
        }
        if (value instanceof VoidValue) {
            return "void";
        }
        return value.toString();
    }

    /** Plain text of a value, as used inside messages (no quotes, no id). */
    public static String simpleName(String typeName) {
        int dot = typeName.lastIndexOf('.');
        return dot < 0 ? typeName : typeName.substring(dot + 1);
    }

    private static String quote(String text) {
        StringBuilder sb = new StringBuilder("\"");
        int length = Math.min(text.length(), MAX_STRING_LENGTH);
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                default -> sb.append(c);
            }
        }
        if (text.length() > length) {
            sb.append("...");
        }
        return sb.append('"').toString();
    }

    public static DebugVariableNode toNode(String name, Value value) {
        return toNode(name, value, DebugVariableNode.Kind.LOCAL);
    }

    public static DebugVariableNode toNode(String name, Value value, DebugVariableNode.Kind kind) {
        if (value instanceof ArrayReference array && array.length() > 0) {
            return new DebugVariableNode(name, format(value), kind, () -> arrayChildren(array));
        }
        if (value instanceof ObjectReference object && !(value instanceof StringReference) && !(value instanceof ArrayReference)) {
            return new DebugVariableNode(name, format(value), kind, () -> fieldChildren(object));
        }
        return new DebugVariableNode(name, format(value), kind);
    }

    private static DebugVariableNode.Kind kindOf(Field field) {
        if (field.isPublic()) {
            return DebugVariableNode.Kind.FIELD_PUBLIC;
        }
        if (field.isProtected()) {
            return DebugVariableNode.Kind.FIELD_PROTECTED;
        }
        return field.isPrivate() ? DebugVariableNode.Kind.FIELD_PRIVATE : DebugVariableNode.Kind.FIELD_DEFAULT;
    }

    private static List<DebugVariableNode> arrayChildren(ArrayReference array) {
        List<DebugVariableNode> children = new ArrayList<>();
        int length = array.length();
        int shown = Math.min(length, MAX_ARRAY_ELEMENTS);
        for (int i = 0; i < shown; i++) {
            children.add(toNode("[" + i + "]", array.getValue(i)));
        }
        if (length > shown) {
            children.add(new DebugVariableNode("...", (length - shown) + " more elements"));
        }
        return children;
    }

    private static List<DebugVariableNode> fieldChildren(ObjectReference object) {
        List<DebugVariableNode> children = new ArrayList<>();
        try {
            // allFields() keeps declaration order; ObjectReference.getValues() would return an unordered map.
            for (Field field : object.referenceType().allFields()) {
                if (!field.isStatic()) {
                    children.add(toNode(field.name(), object.getValue(field), kindOf(field)));
                }
            }
        } catch (RuntimeException e) {
            String message = e.getMessage();
            children.add(new DebugVariableNode("<error>", message == null ? e.getClass().getSimpleName() : message));
        }
        return children;
    }
}
