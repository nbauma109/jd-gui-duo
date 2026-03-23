package tim.jarcomp;

import java.io.File;
import java.util.Arrays;
import java.util.Locale;
import javax.swing.filechooser.FileFilter;

/**
 * Class to act as a generic file filter based on file extension
 */
public class GenericFileFilter extends FileFilter {
    /** Filter description for display */
    private String filterDesc;
    /** Array of allowed suffixes */
    private String[] suffixes;

    /**
     * Constructor
     *
     * @param inDescription filter description
     * @param inSuffixes    array of allowed 3- and 4-character file suffixes
     */
    public GenericFileFilter(String inDescription, String[] inSuffixes) {
        filterDesc = inDescription;
        if (inSuffixes != null && inSuffixes.length > 0) {
            suffixes = Arrays.stream(inSuffixes)
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .map(suffix -> suffix.toLowerCase(Locale.ROOT))
                .toArray(String[]::new);
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
        if (inName != null && suffixes != null) {
            String lowerCaseName = inName.toLowerCase(Locale.ROOT);
            for (String suffix : suffixes) {
                if (lowerCaseName.endsWith("." + suffix)) {
                    return true;
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
