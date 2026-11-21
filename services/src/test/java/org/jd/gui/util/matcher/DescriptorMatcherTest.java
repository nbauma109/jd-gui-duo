package org.jd.gui.util.matcher;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DescriptorMatcherTest {

    @Test
    public void testMatchFieldDescriptors() {
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
    public void testMatchMethodDescriptors() {
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
    public void testHints() {
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
}
