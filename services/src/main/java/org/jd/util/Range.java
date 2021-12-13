package org.jd.util;

import java.util.Objects;

public record Range(int minimum, int maximum) {

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

	@Override
	public String toString() {
		return "Range [" + minimum + ".." + maximum + "]";
	}
}
