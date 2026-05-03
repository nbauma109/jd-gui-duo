/*
 * © 2008-2019 Emmanuel Dupuy
 * © 2022-2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.util.decompiler;

public final class GuiPreferences {

    public static final String MAXIMUM_DEPTH_KEY                 = "DirectoryIndexerPreferences.maximumDepth";
    public static final String FONT_SIZE_KEY                     = "ViewerPreferences.fontSize";
    public static final String TREE_NODE_FONT_SIZE_KEY           = "ViewerPreferences.treeNodeFontSize";
    public static final String SELECTED_WORD_HIGHLIGHT_ENABLED   = "ViewerPreferences.selectedWordHighlight.enabled";
    public static final String SELECTED_WORD_HIGHLIGHT_COLOR     = "ViewerPreferences.selectedWordHighlight.color";
    public static final String SEARCH_HIGHLIGHT_COLOR            = "ViewerPreferences.searchHighlight.color";
    public static final String SELECTION_HIGHLIGHT_COLOR         = "ViewerPreferences.selectionHighlight.color";
    public static final String DEFAULT_SELECTED_WORD_HIGHLIGHT_ENABLED = Boolean.TRUE.toString();
    public static final String DEFAULT_SELECTED_WORD_HIGHLIGHT_COLOR   = "0x66FF66";
    public static final String DEFAULT_SEARCH_HIGHLIGHT_COLOR          = "0xFFFF66";
    public static final String DEFAULT_SELECTION_HIGHLIGHT_COLOR       = "0xF49810";
    public static final String ERROR_BACKGROUND_COLOR            = "JdGuiPreferences.errorBackgroundColor";
    public static final String DECOMPILE_ENGINE                  = "ClassFileDecompilerPreferences.decompileEngine";
    public static final String SHOW_COMPILER_ERRORS              = "ClassFileDecompilerPreferences.showCompilerErrors";
    public static final String SHOW_COMPILER_WARNINGS            = "ClassFileDecompilerPreferences.showCompilerWarnings";
    public static final String SHOW_COMPILER_INFO                = "ClassFileDecompilerPreferences.showCompilerInfo";
    public static final String ADVANCED_CLASS_LOOKUP             = "ClassFileDecompilerPreferences.advancedClassLookup";
    public static final String REMOVE_UNNECESSARY_CASTS          = "ClassFileDecompilerPreferences.removeUnnecessaryCasts";
    public static final String INCLUDE_RUNNING_VM_BOOT_CLASSPATH = "ClassFileDecompilerPreferences.includeRunningVMBootClasspath";
    public static final String JRE_SYSTEM_LIBRARY_PATH           = "ClassFileDecompilerPreferences.jreSystemLibraryPath";

    private GuiPreferences() {
    }
}
