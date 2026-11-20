/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.util.debug;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The program debugged by the expression evaluator tests: it prepares locals of every kind, then stops in
 * {@link #breakHere()} where the test suspends it (the locals live in the frame below).
 */
@SuppressWarnings({"unused", "java:S1144", "java:S1068", "java:S100", "java:S1172"})
public class EvalTarget implements Comparable<EvalTarget> {

    public enum Color { RED, GREEN, BLUE }

    public static final int LIMIT = 10;
    public static final String GREETING = "hi";
    public static final long BIG = 1L << 40;
    public static int counter = 5;

    public final String name;
    public int count;
    public EvalTarget next;

    public EvalTarget(String name, int count) {
        this.name = name;
        this.count = count;
    }

    public String getName() {
        return name;
    }

    public int add(int a, int b) {
        return a + b;
    }

    public double add(double a, double b) {
        return a + b + 0.5;
    }

    public String join(String... parts) {
        return String.join("+", parts);
    }

    public static int twice(int value) {
        return value * 2;
    }

    public int fail() {
        throw new IllegalStateException("on purpose");
    }

    public EvalTarget self() {
        return this;
    }

    public String describe() {
        return name + ":" + count;
    }

    @Override
    public int compareTo(EvalTarget other) {
        return Integer.compare(count, other.count);
    }

    @Override
    public String toString() {
        return "Target(" + name + ")";
    }

    public static void main(String[] args) {
        int i = 7;
        long l = 123456789012L;
        double d = 2.5;
        float f = 1.5f;
        char c = 'x';
        boolean flag = true;
        byte b = 5;
        short sh = 300;
        String s = "hello";
        String nul = null;
        int[] arr = {1, 2, 3};
        int[][] grid = {{1, 2}, {3, 4}};
        String[] words = {"a", "b"};
        List<String> list = new ArrayList<>(List.of("a", "b"));
        Map<String, Integer> map = new HashMap<>();
        map.put("k", 1);
        Integer boxed = 42;
        Long boxedLong = 5L;
        Double boxedDouble = 1.5;
        Boolean boxedFlag = Boolean.TRUE;
        Character boxedChar = 'q';
        Object o = s;
        StringBuilder sb = new StringBuilder("sb");
        EvalTarget obj = new EvalTarget("n", 3);
        obj.next = new EvalTarget("m", 4);
        Color color = Color.GREEN;
        // the test suspends the program somewhere in this loop and reads the locals of this frame
        while (flag) {
            breakHere();
            java.util.concurrent.locks.LockSupport.parkNanos(10_000_000L);
        }
        System.out.println(i + l + d + f + c + b + sh + s + nul + arr.length + grid.length + words.length + list + map + boxed
                + boxedLong + boxedDouble + boxedFlag + boxedChar + o + sb + obj + color);
    }

    public static void breakHere() {
        counter++;
    }
}
