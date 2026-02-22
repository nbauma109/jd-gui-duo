package tim.jarcomp;

import java.util.List;

/**
 * Class to hold the results of a compare operation
 */
public class CompareResults extends EntryDetails {
    /** list of entries */
    private List<EntryDetails> entries;
    /** Number of files in each archive */
    private int[] numFiles = new int[2];

    /**
     * @param inList list of EntryDetails objects
     */
    public void setEntryList(List<EntryDetails> inList) {
        entries = inList;
    }

    /**
     * @return entry list
     */
    public List<EntryDetails> getEntryList() {
        return entries;
    }

    /**
     * @param inIndex index, either 0 or 1
     * @return number of files in specified archive
     */
    public int getNumFiles(int inIndex) {
        if (inIndex < 0 || inIndex > 1) {
            return 0;
        }
        return numFiles[inIndex];
    }

    /**
     * @param inIndex    index, either 0 or 1
     * @param inNumFiles number of files
     */
    public void setNumFiles(int inIndex, int inNumFiles) {
        if (inIndex == 0 || inIndex == 1) {
            numFiles[inIndex] = inNumFiles;
        }
    }

    /**
     * @return true if the entries are in any way different
     */
    public boolean getEntriesDifferent() {
        // Loop over all entries
        for (EntryDetails entry : entries) {
            EntryDetails.EntryStatus status = entry.getStatus();
            if (status != EntryDetails.EntryStatus.EQUAL && status != EntryDetails.EntryStatus.SAME_SIZE) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return true if the CRC checksums of all (necessary) entries have been checked
     */
    public boolean isEntriesCRCChecked() {
        // Loop over all entries
        for (EntryDetails entry : entries) {
            if (entry.getStatus() == EntryDetails.EntryStatus.SAME_SIZE) {
                return false;
            }
        }
        return true;
    }
}
