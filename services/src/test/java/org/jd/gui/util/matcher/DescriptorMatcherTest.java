package org.jd.gui.util.matcher;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DescriptorMatcherTest {

    @Test
    void testMatchFieldDescriptors() {
        assertTrue(DescriptorMatcher.matchFieldDescriptors("?", "?"));

        assertTrue(DescriptorMatcher.matchFieldDescriptors("I", "I"));
        assertTrue(DescriptorMatcher.matchFieldDescriptors("?", "I"));
        assertTrue(DescriptorMatcher.matchFieldDescriptors("I", "?"));

        assertTrue(DescriptorMatcher.matchFieldDescriptors("Ltest/Test;", "Ltest/Test;"));
        assertTrue(DescriptorMatcher.matchFieldDescriptors("?", "Ltest/Test;"));
        assertTrue(DescriptorMatcher.matchFieldDescriptors("Ltest/Test;", "?"));
        assertTrue(DescriptorMatcher.matchFieldDescriptors("L*/Test;", "Ltest/Test;"));
        assertTrue(DescriptorMatcher.matchFieldDescriptors("Ltest/Test;", "L*/Test;"));

        assertTrue(DescriptorMatcher.matchFieldDescriptors("L*/Test;", "L*/Test;"));
        assertTrue(DescriptorMatcher.matchFieldDescriptors("?", "L*/Test;"));
        assertTrue(DescriptorMatcher.matchFieldDescriptors("L*/Test;", "?"));

        assertTrue(DescriptorMatcher.matchFieldDescriptors("[Z", "[Z"));
        assertTrue(DescriptorMatcher.matchFieldDescriptors("[Z", "?"));
        assertTrue(DescriptorMatcher.matchFieldDescriptors("?", "[Z"));

        assertTrue(DescriptorMatcher.matchFieldDescriptors("Ltest/Test;", "Ltest/Test;"));
        assertTrue(DescriptorMatcher.matchFieldDescriptors("Ltest/Test;", "?"));
        assertTrue(DescriptorMatcher.matchFieldDescriptors("?", "Ltest/Test;"));

        assertTrue(DescriptorMatcher.matchFieldDescriptors("[[[Ltest/Test;", "[[[Ltest/Test;"));
        assertTrue(DescriptorMatcher.matchFieldDescriptors("[[[Ltest/Test;", "?"));
        assertTrue(DescriptorMatcher.matchFieldDescriptors("?", "[[[Ltest/Test;"));

        assertTrue(DescriptorMatcher.matchFieldDescriptors("[[[L*/Test;", "[[[L*/Test;"));
        assertTrue(DescriptorMatcher.matchFieldDescriptors("[[[L*/Test;", "?"));
        assertTrue(DescriptorMatcher.matchFieldDescriptors("?", "[[[L*/Test;"));
    }

    @Test
    void testMatchMethodDescriptors() {
        assertFalse(DescriptorMatcher.matchMethodDescriptors("I", "I"));

        assertTrue(DescriptorMatcher.matchMethodDescriptors("()I", "()I"));
        assertTrue(DescriptorMatcher.matchMethodDescriptors("(*)?", "()I"));
        assertTrue(DescriptorMatcher.matchMethodDescriptors("()I", "(*)?"));

        assertTrue(DescriptorMatcher.matchMethodDescriptors("(I)I", "(I)I"));
        assertTrue(DescriptorMatcher.matchMethodDescriptors("(*)?", "(I)I"));
        assertTrue(DescriptorMatcher.matchMethodDescriptors("(I)I", "(*)?"));

        assertTrue(DescriptorMatcher.matchMethodDescriptors("(IJ)I", "(IJ)I"));
        assertTrue(DescriptorMatcher.matchMethodDescriptors("(*)?", "(IJ)I"));
        assertTrue(DescriptorMatcher.matchMethodDescriptors("(IJ)I", "(*)?"));

        assertTrue(DescriptorMatcher.matchMethodDescriptors("(Ltest/Test;)Ltest/Test;", "(Ltest/Test;)Ltest/Test;"));
        assertTrue(DescriptorMatcher.matchMethodDescriptors("(*)?", "(Ltest/Test;)Ltest/Test;"));
        assertTrue(DescriptorMatcher.matchMethodDescriptors("(Ltest/Test;)Ltest/Test;", "(*)?"));
        assertTrue(DescriptorMatcher.matchMethodDescriptors("([[Ltest/Test;[[Ltest/Test;)Ltest/Test;", "([[L*/Test;[[L*/Test;)L*/Test;"));
        assertTrue(DescriptorMatcher.matchMethodDescriptors("([[L*/Test;[[L*/Test;)L*/Test;", "([[Ltest/Test;[[Ltest/Test;)Ltest/Test;"));
    }

    @Test
    void testMatchMethodDescriptorsFurtherCases() {
        assertTrue(DescriptorMatcher.matchMethodDescriptors("(Ltest/Test;Ltest/Test;)Ltest/Test;", "(Ltest/Test;Ltest/Test;)Ltest/Test;"));
        assertTrue(DescriptorMatcher.matchMethodDescriptors("(*)?", "(Ltest/Test;Ltest/Test;)Ltest/Test;"));
        assertTrue(DescriptorMatcher.matchMethodDescriptors("(Ltest/Test;Ltest/Test;)Ltest/Test;", "(*)?"));

        assertTrue(DescriptorMatcher.matchMethodDescriptors("([[Ltest/Test;[[Ltest/Test;)Ltest/Test;", "([[Ltest/Test;[[Ltest/Test;)Ltest/Test;"));
        assertTrue(DescriptorMatcher.matchMethodDescriptors("(*)?", "([[Ltest/Test;[[Ltest/Test;)Ltest/Test;"));
        assertTrue(DescriptorMatcher.matchMethodDescriptors("([[Ltest/Test;[[Ltest/Test;)Ltest/Test;", "(*)?"));
        assertTrue(DescriptorMatcher.matchMethodDescriptors("([[L*/Test;[[L*/Test;)L*/Test;", "([[Ltest/Test;[[Ltest/Test;)Ltest/Test;"));
        assertTrue(DescriptorMatcher.matchMethodDescriptors("([[Ltest/Test;[[Ltest/Test;)Ltest/Test;", "([[L*/Test;[[L*/Test;)L*/Test;"));

        assertTrue(DescriptorMatcher.matchMethodDescriptors("(L*/Test;)L*/Test;", "(L*/Test;)L*/Test;"));
        assertTrue(DescriptorMatcher.matchMethodDescriptors("(*)?", "(L*/Test;)L*/Test;"));
        assertTrue(DescriptorMatcher.matchMethodDescriptors("(L*/Test;)L*/Test;", "(*)?"));

        assertTrue(DescriptorMatcher.matchMethodDescriptors("(L*/Test;L*/Test;)L*/Test;", "(L*/Test;L*/Test;)L*/Test;"));
        assertTrue(DescriptorMatcher.matchMethodDescriptors("(*)?", "(L*/Test;L*/Test;)L*/Test;"));
        assertTrue(DescriptorMatcher.matchMethodDescriptors("(Ltest/Test;Ltest/Test;)Ltest/Test;", "(*)?"));

        assertTrue(DescriptorMatcher.matchMethodDescriptors("([[L*/Test;[[L*/Test;)L*/Test;", "([[L*/Test;[[L*/Test;)L*/Test;"));
        assertTrue(DescriptorMatcher.matchMethodDescriptors("(*)?", "([[L*/Test;[[L*/Test;)L*/Test;"));
        assertTrue(DescriptorMatcher.matchMethodDescriptors("([[L*/Test;[[L*/Test;)L*/Test;", "(*)?"));
    }

    @Test
    void testHints() {
        assertTrue(DescriptorMatcher.hasHints("(~I?)V"));
        assertFalse(DescriptorMatcher.hasHints("(I?)V"));
        assertFalse(DescriptorMatcher.hasHints(null));

        // a hint is a required type when it is confirmed, an unknown one when it is dropped
        assertEquals("(ILjava/lang/String;?)?", DescriptorMatcher.confirmHints("(~I~Ljava/lang/String;?)?"));
        assertEquals("(???)?", DescriptorMatcher.dropHints("(~I~Ljava/lang/String;?)?"));
        assertEquals("(?I)V", DescriptorMatcher.dropHints("(~[[Ljava/lang/String;I)V"));
        assertEquals("(?)V", DescriptorMatcher.dropHints("(~Ljava/util/List<Ljava/lang/String;>;)V"));
        assertEquals("(??)V", DescriptorMatcher.dropHints("(~[I~[Ldemo/A;)V"));
        assertEquals("()V", DescriptorMatcher.dropHints("()V"));
        assertEquals("(?", DescriptorMatcher.dropHints("(~"), "a truncated descriptor does not fail");

        assertTrue(DescriptorMatcher.matchMethodDescriptors("(ILjava/lang/String;)V", DescriptorMatcher.confirmHints("(~I~Ljava/lang/String;)?")));
        assertFalse(DescriptorMatcher.matchMethodDescriptors("(JLjava/lang/String;)V", DescriptorMatcher.confirmHints("(~I~Ljava/lang/String;)?")));
        assertTrue(DescriptorMatcher.matchMethodDescriptors("(JLjava/lang/String;)V", DescriptorMatcher.dropHints("(~I~Ljava/lang/String;)?")));
        assertFalse(DescriptorMatcher.matchMethodDescriptors("(J)V", DescriptorMatcher.dropHints("(~I~Ljava/lang/String;)?")));
    }

    @Test
    void testFieldDescriptorsOfArraysAndClasses() {
        assertTrue(DescriptorMatcher.matchFieldDescriptors("[I", "[I"));
        assertFalse(DescriptorMatcher.matchFieldDescriptors("[I", "[J"));
        assertTrue(DescriptorMatcher.matchFieldDescriptors("[[Ljava/lang/String;", "[[Ljava/lang/String;"));
        assertFalse(DescriptorMatcher.matchFieldDescriptors("[[Ljava/lang/String;", "[Ljava/lang/String;"));
        assertFalse(DescriptorMatcher.matchFieldDescriptors("Lfoo/A;", "Lfoo/B;"));
        assertFalse(DescriptorMatcher.matchFieldDescriptors("Lfoo/A;", "Lfoo/AB;"));
        assertFalse(DescriptorMatcher.matchFieldDescriptors("Lfoo/A;", "I"));
        // an incomplete descriptor never matches
        assertFalse(DescriptorMatcher.matchFieldDescriptors("Lfoo/A", "Lfoo/A;"));
        assertFalse(DescriptorMatcher.matchFieldDescriptors("Lfoo/A;", "Lfoo/A"));
        assertFalse(DescriptorMatcher.matchFieldDescriptors("", ""));
        assertFalse(DescriptorMatcher.matchFieldDescriptors("L", "L"));
    }

    @Test
    void testFieldDescriptorsWithAnUnknownPackage() {
        // L*/Name; stands for a class of that name in any package, on either side
        assertTrue(DescriptorMatcher.matchFieldDescriptors("L*/A;", "Lfoo/A;"));
        assertTrue(DescriptorMatcher.matchFieldDescriptors("Lfoo/A;", "L*/A;"));
        assertTrue(DescriptorMatcher.matchFieldDescriptors("L*/A;", "L*/A;"));
        assertTrue(DescriptorMatcher.matchFieldDescriptors("L*/A;", "Lx/y/z/A;"));
        assertTrue(DescriptorMatcher.matchFieldDescriptors("L*/A;", "LA;"));
        assertFalse(DescriptorMatcher.matchFieldDescriptors("L*/A;", "Lfoo/B;"));
        assertFalse(DescriptorMatcher.matchFieldDescriptors("L*/A;", "Lfoo/AA;"));
        assertFalse(DescriptorMatcher.matchFieldDescriptors("L*/A", "Lfoo/A;"));
        assertFalse(DescriptorMatcher.matchFieldDescriptors("Lfoo/A;", "L*/A"));
        // the wildcard also covers an inner class name
        assertTrue(DescriptorMatcher.matchFieldDescriptors("L*/Inner;", "Ldemo/Over$Inner;"));
        assertTrue(DescriptorMatcher.matchFieldDescriptors("Ldemo/Over$Mid$Inner;", "L*/Inner;"));
        // ... but only at the wildcard: a top-level class of a package is not a nested class, and the other way round
        assertFalse(DescriptorMatcher.matchMethodDescriptors("(Lpkg/Outer/Inner;)V", "(L*/Outer$Inner;)V"));
        assertFalse(DescriptorMatcher.matchMethodDescriptors("(L*/Outer$Inner;)V", "(Lpkg/Outer/Inner;)V"));
        assertTrue(DescriptorMatcher.matchMethodDescriptors("(Lpkg/Outer$Inner;)V", "(L*/Outer$Inner;)V"));
        assertFalse(DescriptorMatcher.matchMethodDescriptors("(Lpkg/Outer$Inner;)V", "(L*/Other$Inner;)V"));
    }

    @Test
    void testFieldDescriptorsWithAnUnknownType() {
        assertTrue(DescriptorMatcher.matchFieldDescriptors("?", "[I"));
        assertTrue(DescriptorMatcher.matchFieldDescriptors("[[Ljava/lang/String;", "?"));
        assertTrue(DescriptorMatcher.matchFieldDescriptors("?", "Lfoo/A;"));
        assertTrue(DescriptorMatcher.matchFieldDescriptors("J", "?"));
        // there must be a whole type for it to stand for
        assertFalse(DescriptorMatcher.matchFieldDescriptors("?", ""));
        assertFalse(DescriptorMatcher.matchFieldDescriptors("", "?"));
        assertFalse(DescriptorMatcher.matchFieldDescriptors("?", "["));
        assertFalse(DescriptorMatcher.matchFieldDescriptors("[", "?"));
        assertFalse(DescriptorMatcher.matchFieldDescriptors("?", "Lfoo/A"));
        assertFalse(DescriptorMatcher.matchFieldDescriptors("[Lfoo/A", "?"));
    }

    @Test
    void testMethodDescriptorsParametersAndReturnType() {
        assertTrue(DescriptorMatcher.matchMethodDescriptors("()V", "()V"));
        assertTrue(DescriptorMatcher.matchMethodDescriptors("(I[Ljava/lang/String;)Z", "(I[Ljava/lang/String;)Z"));
        assertFalse(DescriptorMatcher.matchMethodDescriptors("(I)V", "(J)V"));
        assertFalse(DescriptorMatcher.matchMethodDescriptors("(II)V", "(I)V"));
        assertFalse(DescriptorMatcher.matchMethodDescriptors("(I)V", "(II)V"));
        assertFalse(DescriptorMatcher.matchMethodDescriptors("(I)V", "(I)I"));
        assertFalse(DescriptorMatcher.matchMethodDescriptors("(Lfoo/A;)V", "(Lfoo/B;)V"));
        assertFalse(DescriptorMatcher.matchMethodDescriptors("I", "()V"));
        assertFalse(DescriptorMatcher.matchMethodDescriptors("()V", "I"));
        assertFalse(DescriptorMatcher.matchMethodDescriptors("", ""));
    }

    @Test
    void testMethodDescriptorsWithUnknownParts() {
        // ? is an unknown parameter or return type; (*) is any parameters
        assertTrue(DescriptorMatcher.matchMethodDescriptors("(I)V", "(?)V"));
        assertTrue(DescriptorMatcher.matchMethodDescriptors("(?I)V", "(JI)?"));
        assertTrue(DescriptorMatcher.matchMethodDescriptors("(IJ)V", "(I)V".replace("(I)", "(I?)")));
        assertFalse(DescriptorMatcher.matchMethodDescriptors("(I)V", "(??)V"));
        assertTrue(DescriptorMatcher.matchMethodDescriptors("(*)V", "(IJ)Z"));
        assertTrue(DescriptorMatcher.matchMethodDescriptors("(IJ)Z", "(*)?"));
        assertTrue(DescriptorMatcher.matchMethodDescriptors("(L*/A;)V", "(Lfoo/A;)V"));
        assertFalse(DescriptorMatcher.matchMethodDescriptors("(L*/A;)V", "(Lfoo/B;)V"));
    }

    @Test
    void testIncompleteMethodDescriptorsEndWithoutLooping() {
        org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), () -> {
            assertFalse(DescriptorMatcher.matchMethodDescriptors("(I)V", "(I"));
            assertFalse(DescriptorMatcher.matchMethodDescriptors("(I", "(I)V"));
            assertFalse(DescriptorMatcher.matchMethodDescriptors("(I)V", "("));
            assertFalse(DescriptorMatcher.matchMethodDescriptors("(", "(I)V"));
            assertFalse(DescriptorMatcher.matchMethodDescriptors("(I)V", "(I)"));
        });
    }

    @Test
    void testNestedClassWildcardAgainstOtherSeparators() {
        // the '/' after the wildcard is the boundary of the nested class only: not a '.', not another character
        assertFalse(DescriptorMatcher.matchFieldDescriptors("L*/Inner;", "Ldemo/Over.Inner;"));
        assertFalse(DescriptorMatcher.matchFieldDescriptors("Ldemo/Over.Inner;", "L*/Inner;"));
        assertFalse(DescriptorMatcher.matchFieldDescriptors("L*/Inner;", "Ldemo/Over#Inner;"));
    }

    @Test
    void testCharBuffer() {
        DescriptorMatcher.CharBuffer buffer = new DescriptorMatcher.CharBuffer("(I)V");

        assertFalse(buffer.unread());
        assertEquals('(', buffer.get());
        assertEquals('(', buffer.read());
        assertEquals("I)V", buffer.toString());
        assertTrue(buffer.unread());
        assertEquals("(I)V", buffer.toString());
        buffer.read();
        buffer.read();
        buffer.read();
        buffer.read();
        // past the end: nothing is read, and the offset is where it was
        assertEquals((char) 0, buffer.read());
        assertEquals((char) 0, buffer.get());
        assertEquals("", buffer.toString());
    }

    @Test
    void testSkipType() {
        assertTrue(new DescriptorMatcher.CharBuffer("I").skipType());
        assertTrue(new DescriptorMatcher.CharBuffer("[[J").skipType());
        assertTrue(new DescriptorMatcher.CharBuffer("[Lfoo/A;I").skipType());
        assertFalse(new DescriptorMatcher.CharBuffer("Lfoo/A").skipType());
        assertFalse(new DescriptorMatcher.CharBuffer("[").skipType());
        assertFalse(new DescriptorMatcher.CharBuffer("").skipType());
        DescriptorMatcher.CharBuffer two = new DescriptorMatcher.CharBuffer("Lfoo/A;I");
        assertTrue(two.skipType());
        assertEquals("I", two.toString());
    }
}
