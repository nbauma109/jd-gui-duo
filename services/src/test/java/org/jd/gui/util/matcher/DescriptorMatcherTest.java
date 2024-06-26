package org.jd.gui.util.matcher;

import org.junit.jupiter.api.Test;

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
}
