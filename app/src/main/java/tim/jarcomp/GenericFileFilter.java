package tim.jarcomp;

import java.io.File;
import javax.swing.filechooser.FileFilter;

/**
 * Class to act as a generic file filter based on file extension
 */
public class GenericFileFilter extends FileFilter {
    /** Filter description for display */
    private String filterDesc;
    /** Array of allowed three-character suffixes */
    private String[] threeCharSuffixes;
    /** Array of allowed four-character suffixes */
    private String[] fourCharSuffixes;

    /**
     * Constructor
     *
     * @param inDescription filter description
     * @param inSuffixes    array of allowed 3- and 4-character file suffixes
     */
    public GenericFileFilter(String inDescription, String[] inSuffixes) {
        filterDesc = inDescription;
        if (inSuffixes != null && inSuffixes.length > 0) {
            threeCharSuffixes = new String[inSuffixes.length];
            fourCharSuffixes = new String[inSuffixes.length];
            int threeIndex = 0;
            int fourIndex = 0;
            for (String element : inSuffixes) {
                String suffix = element;
                if (suffix != null) {
                    suffix = suffix.trim().toLowerCase();
                    if (suffix.length() == 3) {
                        threeCharSuffixes[threeIndex++] = suffix;
                    } else if (suffix.length() == 4) {
                        fourCharSuffixes[fourIndex++] = suffix;
                    }
                }
            }
        }
    }

    /**
     * Check whether to accept the specified file or not
     *
     * @see javax.swing.filechooser.FileFilter#accept(java.io.File)
     */
    @Override
    public boolean accept(File inFile) {
        return inFile.isDirectory() || acceptFilename(inFile.getName());
    }

    /**
     * Check whether to accept the given filename
     *
     * @param inName name of file
     * @return true if accepted, false otherwise
     */
    public boolean acceptFilename(String inName) {
        if (inName != null) {
            int nameLen = inName.length();
            if (nameLen > 4) {
                // Check for three character suffixes
                char currChar = inName.charAt(nameLen - 4);
                if (currChar == '.') {
                    return acceptFilename(inName.substring(nameLen - 3).toLowerCase(), threeCharSuffixes);
                }
                // check for four character suffixes
                currChar = inName.charAt(nameLen - 5);
                if (currChar == '.') {
                    return acceptFilename(inName.substring(nameLen - 4).toLowerCase(), fourCharSuffixes);
                }
            }
        }
        // Not matched so don't accept
        return false;
    }

    /**
     * Check whether to accept the given filename
     *
     * @param inSuffixToCheck   suffix to check
     * @param inAllowedSuffixes array of allowed suffixes
     * @return true if accepted, false otherwise
     */
    public boolean acceptFilename(String inSuffixToCheck, String[] inAllowedSuffixes) {
        if (inSuffixToCheck != null && inAllowedSuffixes != null) {
            // Loop over allowed suffixes
            for (String element : inAllowedSuffixes) {
                if (element != null && inSuffixToCheck.equals(element)) {
                    return true;
                }
            }
        }
        // Fallen through so not allowed
        return false;
    }

    /**
     * Get description
     *
     * @see javax.swing.filechooser.FileFilter#getDescription()
     */
    @Override
    public String getDescription() {
        return filterDesc;
    }

}
