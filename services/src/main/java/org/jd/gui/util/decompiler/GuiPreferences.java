/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.util.decompiler;

import java.util.Map;

import jd.core.preferences.Preferences;

public class GuiPreferences extends Preferences {

    public static final String MAXIMUM_DEPTH_KEY           = "DirectoryIndexerPreferences.maximumDepth";
    public static final String FONT_SIZE_KEY               = "ViewerPreferences.fontSize";
    public static final String ERROR_BACKGROUND_COLOR      = "JdGuiPreferences.errorBackgroundColor";
    public static final String JD_CORE_VERSION             = "JdGuiPreferences.jdCoreVersion";
    public static final String DISPLAY_DEFAULT_CONSTRUCTOR = "ClassFileViewerPreferences.displayDefaultConstructor";
    public static final String WRITE_LINE_NUMBERS          = "ClassFileSaverPreferences.writeLineNumbers";
    public static final String WRITE_METADATA              = "ClassFileSaverPreferences.writeMetadata";
    public static final String ESCAPE_UNICODE_CHARACTERS   = "ClassFileSaverPreferences.escapeUnicodeCharacters";
    public static final String OMIT_THIS_PREFIX            = "ClassFileSaverPreferences.omitThisPrefix";
    public static final String WRITE_DEFAULT_CONSTRUCTOR   = "ClassFileSaverPreferences.writeDefaultConstructor";
    public static final String REALIGN_LINE_NUMBERS        = "ClassFileSaverPreferences.realignLineNumbers";
    public static final String USE_JD_CORE_V0              = "ClassFileSaverPreferences.useJDCoreV0";

    private boolean showPrefixThis;
    private boolean unicodeEscape;
    private boolean showLineNumbers;

    public GuiPreferences()
    {
        this.showPrefixThis = true;
        this.unicodeEscape = false;
        this.showLineNumbers = true;
    }

    public GuiPreferences(Map<String, String> preferences)
    {
        setUnicodeEscape(Boolean.parseBoolean(preferences.getOrDefault(ESCAPE_UNICODE_CHARACTERS, Boolean.FALSE.toString())));
        setShowPrefixThis(!Boolean.parseBoolean(preferences.getOrDefault(OMIT_THIS_PREFIX, Boolean.FALSE.toString())));
        setShowDefaultConstructor(Boolean.parseBoolean(preferences.getOrDefault(DISPLAY_DEFAULT_CONSTRUCTOR, Boolean.FALSE.toString())));
        setRealignmentLineNumber(Boolean.parseBoolean(preferences.getOrDefault(REALIGN_LINE_NUMBERS, Boolean.FALSE.toString())));
    }

    public void setShowDefaultConstructor(boolean b) { showDefaultConstructor = b; }
    public void setRealignmentLineNumber(boolean b) { realignmentLineNumber=b; }
    public void setShowPrefixThis(boolean b) { showPrefixThis = b; }
    public void setUnicodeEscape(boolean b) { unicodeEscape=b; }
    public void setShowLineNumbers(boolean b) { showLineNumbers=b; }

    public boolean isShowPrefixThis() { return showPrefixThis; }
    public boolean isUnicodeEscape() { return unicodeEscape; }
    public boolean isShowLineNumbers() { return showLineNumbers; }
}
