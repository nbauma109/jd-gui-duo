/*
 * © 2008-2019 Emmanuel Dupuy
 * © 2021-2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.util.matcher;

/*
 * Descriptor format : @see jd.gui.api.feature.UriOpenable
 */
public final class DescriptorMatcher {

    /**
     * Marks the type of an argument that is only a hint in the descriptor of a call that has no binding: the types of the arguments
     * are often subtypes of (or widened to) the types of the parameters, so they may not be required to be equal.
     */
    public static final char HINT = '~';

    private DescriptorMatcher() {
    }

    public static boolean hasHints(String descriptor) {
        return descriptor != null && descriptor.indexOf(HINT) != -1;
    }

    /** The descriptor in which the hints are required types: (~I~Ljava/lang/String;)? becomes (ILjava/lang/String;)?. */
    public static String confirmHints(String descriptor) {
        return descriptor.replace(String.valueOf(HINT), "");
    }

    /** The descriptor in which the hints are unknown types: (~I~Ljava/lang/String;)? becomes (??)?. */
    public static String dropHints(String descriptor) {
        StringBuilder sb = new StringBuilder(descriptor.length());
        int i = 0;
        while (i < descriptor.length()) {
            char c = descriptor.charAt(i);
            if (c == HINT) {
                i = endOfType(descriptor, i + 1);
                sb.append('?');
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    /** The index just after the type that starts at {@code start}: arrays, primitives, and classes (which may have type arguments). */
    private static int endOfType(String descriptor, int start) {
        int i = start;
        while (i < descriptor.length() && descriptor.charAt(i) == '[') {
            i++;
        }
        if (i < descriptor.length() && descriptor.charAt(i) == 'L') {
            int depth = 0;
            while (i < descriptor.length()) {
                char c = descriptor.charAt(i++);
                if (c == '<') {
                    depth++;
                } else if (c == '>') {
                    depth--;
                } else if (c == ';' && depth == 0) {
                    break;
                }
            }
            return i;
        }
        return Math.min(i + 1, descriptor.length());
    }

    public static boolean matchFieldDescriptors(String d1, String d2) {
        return matchDescriptors(new CharBuffer(d1), new CharBuffer(d2));
    }

    private static boolean matchDescriptors(CharBuffer cb1, CharBuffer cb2) {
        if (cb1.read() == '?') {
            if (cb2.read() == '?') {
                return true;
            }
            cb2.unread();
            return cb2.skipType();
        }
        cb1.unread();

        if (cb2.read() == '?') {
            return cb1.skipType();
        }
        cb2.unread();
        return cb1.compareTypeWith(cb2);
    }

    public static boolean matchMethodDescriptors(String d1, String d2) {
        CharBuffer cb1 = new CharBuffer(d1);
        CharBuffer cb2 = new CharBuffer(d2);

        if (cb1.read() != '(' || cb2.read() != '(') {
            return false;
        }

        if (cb1.read() == '*') {
            return true;
        }
        if (cb2.read() == '*') {
            return true;
        }

        cb1.unread();
        cb2.unread();

        // Check parameter descriptors
        while (cb2.get() != ')') {
            if (!matchDescriptors(cb1, cb2)) {
                return false;
            }
        }

        if (cb1.read() != ')' || cb2.read() != ')') {
            return false;
        }

        // Check return descriptor
        return matchDescriptors(cb1, cb2);
    }

    protected static class CharBuffer {
        private final char[] buffer;
        private final int length;
        private int offset;

        public CharBuffer(String s) {
            this.buffer = s.toCharArray();
            this.length = buffer.length;
            this.offset = 0;
        }

        public char read() {
            if (offset < length) {
                return buffer[offset++];
            }
            return (char)0;
        }

        public boolean unread() {
            if (offset > 0) {
                offset--;
                return true;
            }
            return false;
        }

        public char get() {
            if (offset < length) {
                return buffer[offset];
            }
            return (char)0;
        }

        public boolean skipType() {
            if (offset < length) {
                char c = buffer[offset++];

                while (c == '[' && offset < length) {
                    c = buffer[offset++];
                }

                if (c == 'L') {
                    while (offset < length) {
                        if (buffer[offset++] == ';') {
                            return true;
                        }
                    }
                } else if (c != '[') {
                    return true;
                }
            }
            return false;
        }

        public boolean compareTypeWith(CharBuffer other) {
            if (offset >= length) {
                return false;
            }

            char c = buffer[offset++];

            if (c != other.read()) {
                return false;
            }

            if (c == '[') {
                // an array: its element type (or its next dimension) must be the same too, ([I is not [J nor [Ljava/lang/String;)
                return compareTypeWith(other);
            }

            if (c == 'L') {
                if (offset >= length || other.offset >= other.length) {
                    return false;
                }

                char[] otherBuffer = other.buffer;

                if (buffer[offset] != '*' && otherBuffer[other.offset] != '*') {
                    // Forward comparison
                    while (offset < length) {
                        c = buffer[offset++];
                        if (c != other.read()) {
                            return false;
                        }
                        if (c == ';') {
                            return true;
                        }
                    }
                    return false;
                }
                int start = offset;
                int otherStart = other.offset;

                // Search ';'
                if (!searchEndOfType() || !other.searchEndOfType()) {
                    return false;
                }

                int current = offset - 1;
                int otherCurrent = other.offset - 1;

                // Backward comparison
                while (start < current && otherStart < otherCurrent) {
                    c = buffer[--current];
                    if (c == '*') {
                        return true;
                    }

                    char otherC = otherBuffer[--otherCurrent];
                    if (otherC == '*') {
                        return true;
                    }
                    // L*/Inner; is a class of that name in any package, and a nested class: the '/' that follows the wildcard stands for the '$' that
                    // follows the name of the outer class (the other separators of the names are not interchangeable: pkg/Outer/Inner is not Outer$Inner)
                    if (c != otherC && !(isWildcardBoundary(buffer, current, c) && otherC == '$')
                            && !(isWildcardBoundary(otherBuffer, otherCurrent, otherC) && c == '$')) {
                        return false;
                    }
                }
            }

            return true;
        }

        /** Whether the character at the index is the '/' that follows the wildcard of a name (the one of "*\/Name"). */
        private static boolean isWildcardBoundary(char[] buffer, int index, char c) {
            return c == '/' && buffer[index - 1] == '*'; // the name of a type follows its 'L': the index is never 0
        }

        protected boolean searchEndOfType() {
            while (offset < length) {
                if (buffer[offset++] == ';') {
                    return true;
                }
            }
            return false;
        }

        @Override
        public String toString() {
            return new String(buffer, offset, length-offset);
        }
    }
}
