/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.util.decompiler;

public final class GuiPreferences {

    public static final String MAXIMUM_DEPTH_KEY        = "DirectoryIndexerPreferences.maximumDepth";
    public static final String FONT_SIZE_KEY            = "ViewerPreferences.fontSize";
    public static final String ERROR_BACKGROUND_COLOR   = "JdGuiPreferences.errorBackgroundColor";
    public static final String DECOMPILE_ENGINE         = "ClassFileDecompilerPreferences.decompileEngine";
    public static final String SHOW_COMPILER_ERRORS     = "ClassFileDecompilerPreferences.showCompilerErrors";
    public static final String SHOW_COMPILER_WARNINGS   = "ClassFileDecompilerPreferences.showCompilerWarnings";
    public static final String SHOW_COMPILER_INFO       = "ClassFileDecompilerPreferences.showCompilerInfo";
    public static final String ADVANCED_CLASS_LOOKUP    = "ClassFileDecompilerPreferences.advancedClassLookup";
    public static final String REMOVE_UNNECESSARY_CASTS = "ClassFileDecompilerPreferences.removeUnnecessaryCasts";

    private GuiPreferences() {
    }
}
