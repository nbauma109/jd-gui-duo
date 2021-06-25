/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jdv1.gui.util.decompiler;

import org.jd.core.v1.api.Decompiler;
import org.jd.core.v1.api.loader.LoaderException;

import org.jd.core.v1.util.StringConstants;
import org.jd.gui.api.model.Container;
import org.jd.gui.util.decompiler.ContainerLoader;

import java.io.File;
import java.io.UTFDataFormatException;
import java.util.HashMap;
import java.util.Map;

import static org.jd.gui.util.decompiler.GuiPreferences.*;

public class LineNumberStringBuilderPrinter extends StringBuilderPrinter {
    protected boolean showLineNumbers = false;

    protected int maxLineNumber = 0;
    protected int digitCount = 0;

    protected String lineNumberBeginPrefix;
    protected String lineNumberEndPrefix;
    protected String unknownLineNumberPrefix;

    public void setShowLineNumbers(boolean showLineNumbers) { this.showLineNumbers = showLineNumbers; }

    protected int printDigit(int dcv, int lineNumber, int divisor, int left) {
        if (digitCount >= dcv) {
            if (lineNumber < divisor) {
                stringBuffer.append(' ');
            } else {
                int e = (lineNumber-left) / divisor;
                stringBuffer.append((char)('0' + e));
                left += e*divisor;
            }
        }

        return left;
    }

    // --- Printer --- //
    @Override
    public void start(int maxLineNumber, int majorVersion, int minorVersion) {
        super.start(maxLineNumber, majorVersion, minorVersion);

        if (showLineNumbers) {
            this.maxLineNumber = maxLineNumber;

            if (maxLineNumber > 0) {
                digitCount = 1;
                unknownLineNumberPrefix = " ";
                int maximum = 9;

                while (maximum < maxLineNumber) {
                    digitCount++;
                    unknownLineNumberPrefix += ' ';
                    maximum = maximum*10 + 9;
                }

                lineNumberBeginPrefix = "/* ";
                lineNumberEndPrefix = " */ ";
            } else {
                unknownLineNumberPrefix = "";
                lineNumberBeginPrefix = "";
                lineNumberEndPrefix = "";
            }
        } else {
            this.maxLineNumber = 0;
            unknownLineNumberPrefix = "";
            lineNumberBeginPrefix = "";
            lineNumberEndPrefix = "";
        }
    }

    @Override
    public void startLine(int lineNumber) {
        if (maxLineNumber > 0) {
            stringBuffer.append(lineNumberBeginPrefix);

            if (lineNumber == UNKNOWN_LINE_NUMBER) {
                stringBuffer.append(unknownLineNumberPrefix);
            } else {
                int left = 0;

                left = printDigit(5, lineNumber, 10000, left);
                left = printDigit(4, lineNumber,  1000, left);
                left = printDigit(3, lineNumber,   100, left);
                left = printDigit(2, lineNumber,    10, left);
                stringBuffer.append((char)('0' + (lineNumber-left)));
            }

            stringBuffer.append(lineNumberEndPrefix);
        }

        for (int i=0; i<indentationCount; i++) {
            stringBuffer.append(TAB);
        }
    }
    @Override
    public void extraLine(int count) {
        if (realignmentLineNumber) {
            while (count-- > 0) {
                if (maxLineNumber > 0) {
                    stringBuffer.append(lineNumberBeginPrefix);
                    stringBuffer.append(unknownLineNumberPrefix);
                    stringBuffer.append(lineNumberEndPrefix);
                }

                stringBuffer.append(NEWLINE);
            }
        }
    }

    public String buildDecompiledOutput(Map<String, String> preferences, ContainerLoader loader, Container.Entry entry, Decompiler decompiler) throws UTFDataFormatException, LoaderException {
        // Init preferences
        boolean realignmentLineNumbers = Boolean.parseBoolean(preferences.getOrDefault(REALIGN_LINE_NUMBERS, Boolean.FALSE.toString()));
        boolean unicodeEscape = Boolean.parseBoolean(preferences.getOrDefault(ESCAPE_UNICODE_CHARACTERS, Boolean.FALSE.toString()));
        boolean showLineNumbers = Boolean.parseBoolean(preferences.getOrDefault(WRITE_LINE_NUMBERS, Boolean.TRUE.toString()));

        Map<String, Object> configuration = new HashMap<>();
        configuration.put("realignLineNumbers", realignmentLineNumbers);

        setRealignmentLineNumber(realignmentLineNumbers);
        setUnicodeEscape(unicodeEscape);
        setShowLineNumbers(showLineNumbers);

        // Format internal name
        String entryPath = entry.getPath();
        assert entryPath.endsWith(StringConstants.CLASS_FILE_SUFFIX);
        String entryInternalName = entryPath.substring(0, entryPath.length() - 6); // 6 = ".class".length()

        // Decompile class file
        decompiler.decompile(loader, this, entryInternalName, configuration);

        StringBuilder stringBuffer = getStringBuffer();

        // Metadata
        if (Boolean.parseBoolean(preferences.getOrDefault(WRITE_METADATA, Boolean.TRUE.toString()))) {
            // Add location
            String location = new File(entry.getUri()).getPath()
                    // Escape "\ u" sequence to prevent "Invalid unicode" errors
                    .replaceAll("(^|[^\\\\])\\\\u", "\\\\\\\\u");
            stringBuffer.append("\n\n/* Location:              ");
            stringBuffer.append(location);
            // Add Java compiler version
            int majorVersion = getMajorVersion();

            if (majorVersion >= 45) {
                stringBuffer.append("\n * Java compiler version: ");

                if (majorVersion >= 49) {
                    stringBuffer.append(majorVersion - (49 - 5));
                } else {
                    stringBuffer.append(majorVersion - (45 - 1));
                }

                stringBuffer.append(" (");
                stringBuffer.append(majorVersion);
                stringBuffer.append('.');
                stringBuffer.append(getMinorVersion());
                stringBuffer.append(')');
            }
            // Add JD-Core version
            stringBuffer.append("\n * JD-Core Version:       ");
            stringBuffer.append(preferences.get(JD_CORE_VERSION));
            stringBuffer.append("\n */");
        }
        return stringBuffer.toString();
    }
}
