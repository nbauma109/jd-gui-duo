package tim.jarcomp;

import java.util.Objects;

/**
 * Class to represent a size change for sorting and display
 */
class SizeChange implements Comparable<SizeChange> {
    /** Size difference, positive means the file has grown larger */
    private long sizeDiff;
    /** True if the files are in any way different, even if the size is the same */
    private boolean changed;

    /**
     * Update the difference object when the details are found
     */
    public void update(long inDiff, boolean inChanged) {
        sizeDiff = inDiff;
        changed = inChanged;
    }

    /**
     * compare two objects
     */
    @Override
    public int compareTo(SizeChange inOther) {
        if (inOther.changed != changed) {
            return changed ? 1 : -1;
        }
        if (sizeDiff > inOther.sizeDiff) {
            return 1;
        }
        return (sizeDiff == inOther.sizeDiff) ? 0 : -1;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        return compareTo((SizeChange) obj) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(sizeDiff, changed);
    }

    /**
     * @return value as a string for display
     */
    @Override
    public String toString() {
        if (!changed) {
            return "";
        }
        if (sizeDiff > 0) {
            return "+" + sizeDiff;
        }
        return "" + sizeDiff;
    }
}
