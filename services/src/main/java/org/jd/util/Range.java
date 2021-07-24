package org.jd.util;

import java.util.Objects;

public class Range {
	
	private final int minimum;
	private final int maximum;

	private Range(int minimum, int maximum) {
		this.minimum = minimum;
		this.maximum = maximum;
	}

	public static Range between(int minimum, int maximum) {
		return new Range(minimum, maximum);
	}

	@Override
	public int hashCode() {
		return Objects.hash(minimum, maximum);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		Range other = (Range) obj;
		return maximum == other.maximum && minimum == other.minimum;
	}

	public int getMinimum() {
		return minimum;
	}

	public int getMaximum() {
		return maximum;
	}

	@Override
	public String toString() {
		return "Range [" + minimum + ".." + maximum + "]";
	}
}
