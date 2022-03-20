package tim.jarcomp;

/**
 * Class to represent a single entry in the jar file for displaying in the
 * comparison table
 */
public class EntryDetails {
    /** Name of entry, including full path */
    private String name;
    /** Flag to show if it's present or not (might be zero length) */
    private boolean[] present = new boolean[2];
    /** Sizes of this file, in bytes, in archives */
    private long[] sizes = new long[2];
    /** CRC checksums in both archives */
    private long[] crcCheckSums = new long[2];
    /** SizeChange */
    private SizeChange sizeChange = new SizeChange();

    /** Constants for entry status */
    public enum EntryStatus {
        /** File not in first but in second */
        ADDED,
        /** File found in first, not in second */
        REMOVED,
        /** File size different in two files */
        CHANGED_SIZE,
        /** File size same (CRC not checked) */
        SAME_SIZE,
        /** File checksum different */
        CHANGED_SUM,
        /** Files really equal */
        EQUAL
    }
    // TODO: Each of these status flags needs an icon

    /**
     * @return name of entry
     */
    public String getName() {
        return name;
    }

    /**
     * @param inName name to set
     */
    public void setName(String inName) {
        name = inName;
    }

    /**
     * @param inIndex index, either 0 or 1
     * @return size of this file in corresponding archive
     */
    public long getSize(int inIndex) {
        if (inIndex < 0 || inIndex > 1) {
            return 0L;
        }
        return sizes[inIndex];
    }

    /**
     * @param inIndex index, either 0 or 1
     * @param inSize  size of file in bytes
     */
    public void setSize(int inIndex, long inSize) {
        if (inIndex == 0 || inIndex == 1) {
            sizes[inIndex] = inSize;
            present[inIndex] = true;
            sizeChange.update(sizes[1] - sizes[0], isChanged());
        }
    }

    /**
     * @param inIndex index, either 0 or 1
     * @return CRC checksum of this file in corresponding archive
     */
    public long getCRCChecksum(int inIndex) {
        if (inIndex < 0 || inIndex > 1) {
            return 0;
        }
        return crcCheckSums[inIndex];
    }

    /**
     * @param inIndex  index, either 0 or 1
     * @param inCRCChecksum CRC checksum of this file
     */
    public void setCRCChecksum(int inIndex, long inCRCChecksum) {
        if (inIndex == 0 || inIndex == 1) {
            crcCheckSums[inIndex] = inCRCChecksum;
            sizeChange.update(sizes[1] - sizes[0], isChanged());
        }
    }

    /**
     * @return true if CRC checksums have been generated for this entry
     */
    public boolean isCRCChecked() {
        return (crcCheckSums[0] != 0 && crcCheckSums[1] != 0);
    }

    /**
     * @return status of entry
     */
    public EntryStatus getStatus() {
        if (!present[0] && present[1]) {
            return EntryStatus.ADDED;
        }
        if (present[0] && !present[1]) {
            return EntryStatus.REMOVED;
        }
        if (sizes[0] != sizes[1]) {
            return EntryStatus.CHANGED_SIZE;
        }
        if (!isCRCChecked()) {
            return EntryStatus.SAME_SIZE;
        }
        // CRC checksums have been checked
        if (crcCheckSums[0] != crcCheckSums[1]) {
            return EntryStatus.CHANGED_SUM;
        }
        return EntryStatus.EQUAL;
    }

    /**
     * @return difference in file sizes (bytes)
     */
    public long getSizeDifference() {
        return sizes[1] - sizes[0];
    }

    /**
     * @return size change object
     */
    public SizeChange getSizeChange() {
        return sizeChange;
    }

    /**
     * @return true if the row represents a change
     */
    public boolean isChanged() {
        EntryStatus status = getStatus();
        return status != EntryStatus.SAME_SIZE && status != EntryStatus.EQUAL;
    }
}
