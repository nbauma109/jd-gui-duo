package tim.jarcomp;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Class to do the actual comparison of jar files, populating a list of
 * EntryDetails objects
 */
public final class Comparer {

    private Comparer() {
    }

    /**
     * Compare the two given files and return the results
     *
     * @param inFile1 first file
     * @param inFile2 second file
     * @return results of comparison
     */
    public static CompareResults compare(File inFile1, File inFile2) {
        // Make results object and compare file sizes
        CompareResults results = new CompareResults();
        results.setSize(0, inFile1.length());
        results.setSize(1, inFile2.length());
        // Make empty list
        ArrayList<EntryDetails> entryList = new ArrayList<>();
        try {
            ArchiveSupport.ArchiveSnapshot snapshot1 = ArchiveSupport.readSnapshot(inFile1);
            ArchiveSupport.ArchiveSnapshot snapshot2 = ArchiveSupport.readSnapshot(inFile2);
            results.setNumFiles(0, snapshot1.entryCount());
            results.setNumFiles(1, snapshot2.entryCount());
            makeEntries(entryList, snapshot1.entries(), 0);
            makeEntries(entryList, snapshot2.entries(), 1);
        } catch (IOException ioe) {
            System.err.println(ioe);
        }
        results.setEntryList(entryList);
        return results;
    }

    // TODO: Maybe we need to add an option to ignore path, just look at filenames?

    /**
     * Make entrydetails objects for each entry in the given file and put in list
     *
     * @param inList  list of entries so far
     * @param entries archive entries to search through
     * @param inIndex 0 for first file, 1 for second
     */
    private static void makeEntries(ArrayList<EntryDetails> inList, java.util.Map<String, ArchiveSupport.ArchiveEntryData> entries, int inIndex) {
        boolean checkList = (!inList.isEmpty());
        for (java.util.Map.Entry<String, ArchiveSupport.ArchiveEntryData> archiveEntry : entries.entrySet()) {
            String name = archiveEntry.getKey();
            EntryDetails details = null;
            if (checkList) {
                details = getEntryFromList(inList, name);
            }
            if (details == null) {
                details = new EntryDetails();
                details.setName(name);
                inList.add(details);
            }
            details.setSize(inIndex, archiveEntry.getValue().size());
            details.setCRCChecksum(inIndex, archiveEntry.getValue().checksum());
        }
    }

    /**
     * Look up the given name in the list
     *
     * @param inList list of EntryDetails objects
     * @param inName name to look up
     */
    private static EntryDetails getEntryFromList(ArrayList<EntryDetails> inList, String inName) {
        EntryDetails details = null;
        for (EntryDetails element : inList) {
            details = element;
            if (details.getName() != null && details.getName().equals(inName)) {
                return details;
            }
        }
        return null;
    }
}
