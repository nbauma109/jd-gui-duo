package org.jd.gui.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.strobel.decompiler.ast.Range;

public class StringUtilities {

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

	public static String applyModifications(final String text, final Map<Range, String> modificationMap) {
		List<Integer> indexes = collectIndexes(modificationMap.keySet());
		indexes.add(text.length());
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < indexes.size() - 1; i++) {
			Integer fromIdx = indexes.get(i);
			Integer toIdx = indexes.get(i + 1);
			Range range = new Range(fromIdx, toIdx);
			String modification = modificationMap.get(range);
			if (modification == null) {
				sb.append(text.substring(fromIdx, toIdx));
			} else {
				sb.append(modification);
			}
		}
		return sb.toString();
	}

	private static List<Integer> collectIndexes(final Set<Range> rangeSet) {
		Set<Integer> indexes = new TreeSet<>();
		indexes.add(0);
		for (Range range : rangeSet) {
			indexes.add(range.getStart());
			indexes.add(range.getEnd());
		}
		return new ArrayList<>(indexes);
	}
}