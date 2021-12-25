package org.jd.gui.util;

import org.jd.util.Range;

import java.util.*;

public class StringUtilities {

    private StringUtilities() {
        super();
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
            indexes.add(range.minimum());
            indexes.add(range.maximum());
        }
        indexes.add(length);
        return new ArrayList<>(indexes);
    }
}