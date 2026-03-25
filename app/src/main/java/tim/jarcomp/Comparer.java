package tim.jarcomp;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import tim.jarcomp.ArchiveReader.ArchiveEntryInfo;

/**
 * Class to do the actual comparison of archive files, populating a list of
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
        // load first file, make entrydetails object for each one
        final int numFiles1 = makeEntries(entryList, inFile1, 0);
        results.setNumFiles(0, numFiles1);
        // load second file, try to find entrydetails for each file or make new one
        final int numFiles2 = makeEntries(entryList, inFile2, 1);
        results.setNumFiles(1, numFiles2);
        results.setEntryList(entryList);

        // Check CRC checksums as it's necessary
        collectCRCChecksums(results, inFile1, 0);
        collectCRCChecksums(results, inFile2, 1);
        return results;
    }

    // TODO: Maybe we need to add an option to ignore path, just look at filenames?

    /**
     * Make entrydetails objects for each entry in the given file and put in list
     *
     * @param inList  list of entries so far
     * @param inFile  archive file to search through
     * @param inIndex 0 for first file, 1 for second
     * @return number of files found
     */
    private static int makeEntries(ArrayList<EntryDetails> inList, File inFile, int inIndex) {
        boolean checkList = (!inList.isEmpty());
        int numFiles = 0;
        try (ArchiveReader reader = new ArchiveReader(inFile)) {
            List<ArchiveEntryInfo> entries = reader.getEntries();
            for (ArchiveEntryInfo archiveEntry : entries) {
                numFiles++;
                String name = archiveEntry.getName();
                EntryDetails details = null;
                if (checkList) {
                    details = getEntryFromList(inList, name);
                }
                // Construct new details object if necessary
                if (details == null) {
                    details = new EntryDetails();
                    details.setName(name);
                    if (!archiveEntry.isDirectory() && !name.contains("$")) {
                        inList.add(details);
                    }
                }
                // set size
                details.setSize(inIndex, archiveEntry.getSize());
            }
        } catch (IOException ioe) {
            System.err.println(ioe);
        }
        return numFiles;
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

    /**
     * Collect the CRC checksums of all relevant entries
     *
     * @param inResults results from preliminary check
     * @param inFile    file to read
     * @param inIndex   0 or 1
     */
    private static void collectCRCChecksums(CompareResults inResults, File inFile, int inIndex) {
        List<EntryDetails> list = inResults.getEntryList();
        try (ArchiveReader reader = new ArchiveReader(inFile)) {
            List<ArchiveEntryInfo> entries = reader.getEntries();
            // Build a map for O(1) lookups instead of O(n) linear search
            Map<String, ArchiveEntryInfo> entryMap = new HashMap<>();
            for (ArchiveEntryInfo entry : entries) {
                entryMap.put(entry.getName(), entry);
            }

            for (EntryDetails entry : list) {
                if (entry.getStatus() == EntryDetails.EntryStatus.SAME_SIZE) {
                    // Must be present in both archives if size is the same
                    ArchiveEntryInfo archiveEntry = entryMap.get(entry.getName());
                    if (archiveEntry == null) {
                        System.err.println("archiveEntry for " + entry.getName() + " shouldn't be null!");
                    } else {
                        entry.setCRCChecksum(inIndex, archiveEntry.getCrc());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println(e);
        }
    }

    /**
     * Find an entry by name in the list
     *
     * @param entries list of archive entries
     * @param name    name to find
     * @return entry info or null if not found
     */
    private static ArchiveEntryInfo findEntry(List<ArchiveEntryInfo> entries, String name) {
        for (ArchiveEntryInfo entry : entries) {
            if (entry.getName().equals(name)) {
                return entry;
            }
        }
        return null;
    }
}
