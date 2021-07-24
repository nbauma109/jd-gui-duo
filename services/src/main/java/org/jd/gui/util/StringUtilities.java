package org.jd.gui.util;

import org.jd.util.Range;

import java.util.*;

public class StringUtilities {

    private StringUtilities() {
        super();
    }

    public static String replaceAll(final String text, final String regex, final String replacement, final int fromIdx,
            final int toIdx) {
        StringBuilder sb = new StringBuilder();
        sb.append(text.substring(0, fromIdx));
        sb.append(text.substring(fromIdx, toIdx).replaceAll(regex, replacement));
        sb.append(text.substring(toIdx));
        return sb.toString();
    }

    public static String replace(final String text, final String searchString, final String replacement,
            final int fromIdx, final int toIdx) {
        StringBuilder sb = new StringBuilder();
        sb.append(text.substring(0, fromIdx));
        sb.append(text.substring(fromIdx, toIdx).replace(searchString, replacement));
        sb.append(text.substring(toIdx));
        return sb.toString();
    }

    public static String patch(final String text, final String patch, final int fromIdx, final int toIdx) {
        StringBuilder sb = new StringBuilder();
        sb.append(text.substring(0, fromIdx));
        sb.append(patch);
        sb.append(text.substring(toIdx));
        return sb.toString();
    }

    public static String applyModifications(final String text, final Map<Range, String> replacementMap) {
        List<Integer> indexes = collectIndexes(replacementMap.keySet(), text.length());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < indexes.size() - 1; i++) {
            Integer fromIdx = indexes.get(i);
            Integer toIdx = indexes.get(i + 1);
            Range range = Range.between(fromIdx, toIdx);
            String modification = replacementMap.get(range);
            if (modification == null) {
                sb.append(text.substring(fromIdx, toIdx));
            } else {
                sb.append(modification);
            }
        }
        return sb.toString();
    }

    private static List<Integer> collectIndexes(final Set<Range> ranges, final int length) {
        Set<Integer> indexes = new TreeSet<>();
        indexes.add(0);
        for (Range range : ranges) {
            indexes.add(range.getMinimum());
            indexes.add(range.getMaximum());
        }
        indexes.add(length);
        return new ArrayList<>(indexes);
    }
}