package tim.jarcomp;

import java.io.File;
import javax.swing.filechooser.FileFilter;

/**
 * Class to act as a generic file filter based on file extension
 */
public class GenericFileFilter extends FileFilter {
    /** Filter description for display */
    private String filterDesc;
    /** Array of allowed suffixes of any length */
    private String[] allowedSuffixes;

    /**
     * Constructor
     *
     * @param inDescription filter description
     * @param inSuffixes    array of allowed file suffixes (any length, e.g., "jar", "tar.gz", "7z")
     */
    public GenericFileFilter(String inDescription, String[] inSuffixes) {
        filterDesc = inDescription;
        if (inSuffixes != null && inSuffixes.length > 0) {
            allowedSuffixes = new String[inSuffixes.length];
            for (int i = 0; i < inSuffixes.length; i++) {
                String suffix = inSuffixes[i];
                if (suffix != null) {
                    allowedSuffixes[i] = suffix.trim().toLowerCase();
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
        if (inName == null || allowedSuffixes == null) {
            return false;
        }

        String lowerName = inName.toLowerCase();

        // Check each allowed suffix
        for (String suffix : allowedSuffixes) {
            if (suffix != null && lowerName.endsWith("." + suffix)) {
                return true;
            }
        }

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
