/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.view.component;

import org.fife.ui.rsyntaxtextarea.DocumentRange;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.jd.core.v1.ClassFileToJavaSourceDecompiler;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ByteCodeWriter;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.api.API;
import org.jd.gui.api.model.Container;
import org.jd.gui.util.MethodPatcher;
import org.jd.gui.util.decompiler.ContainerLoader;
import org.jd.gui.util.decompiler.GuiPreferences;

import com.heliosdecompiler.transformerapi.StandardTransformers;
import com.heliosdecompiler.transformerapi.common.Loader;

import java.awt.Color;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.swing.text.BadLocationException;

import static com.heliosdecompiler.transformerapi.StandardTransformers.Decompilers.ENGINE_JD_CORE_V0;
import static com.heliosdecompiler.transformerapi.StandardTransformers.Decompilers.ENGINE_JD_CORE_V1;
import static jd.core.preferences.Preferences.REALIGN_LINE_NUMBERS;
import static org.jd.gui.util.decompiler.GuiPreferences.DECOMPILE_ENGINE;

import jd.core.ClassUtil;
import jd.core.Decompiler;
import jd.core.process.DecompilerImpl;

public class ClassFilePage extends TypePage {

    private static final String INTERNAL_ERROR = "// INTERNAL ERROR //";

    private static final long serialVersionUID = 1L;

    protected static final ClassFileToJavaSourceDecompiler DECOMPILER = new ClassFileToJavaSourceDecompiler();
    protected static final Decompiler DECOMPILERV0 = new DecompilerImpl();

    private int maximumLineNumber = -1;

    public ClassFilePage(API api, Container.Entry entry) {
        super(api, entry);
        Map<String, String> preferences = api.getPreferences();
        // Init view
        setErrorForeground(Color.decode(preferences.get(GuiPreferences.ERROR_BACKGROUND_COLOR)));
        // Display source
        decompile(preferences);
    }

    public void decompile(Map<String, String> preferences) {
        
        boolean realignmentLineNumbers = Boolean.parseBoolean(preferences.getOrDefault(REALIGN_LINE_NUMBERS, Boolean.FALSE.toString()));

        setShowMisalignment(realignmentLineNumbers);
        
        // Init loader
        ContainerLoader loader = new ContainerLoader(entry);
        try {
            // Clear ...
            clearLineNumbers();
            listener.clearData();

            // Format internal name
            String entryInternalName = ClassUtil.getInternalName(entry.getPath());
            
            String engineName = preferences.getOrDefault(DECOMPILE_ENGINE, ENGINE_JD_CORE_V1);
            Loader apiLoader = new Loader(loader::canLoad, loader::load);
            String decompilationResult = StandardTransformers.decompile(apiLoader, entryInternalName, preferences, engineName);
            if (decompilationResult.contains(ByteCodeWriter.DECOMPILATION_FAILED_AT_LINE)) {
                String sourceCodeV0 = StandardTransformers.decompile(apiLoader, entryInternalName, preferences, ENGINE_JD_CORE_V0);
                String patchedCode = MethodPatcher.patchCode(decompilationResult, sourceCodeV0, entry);
                parseAndSetText(patchedCode);
            } else {
                parseAndSetText(decompilationResult);
            }
        } catch (Exception t) {
            assert ExceptionUtil.printStackTrace(t);
            setText(INTERNAL_ERROR);
        } finally {
            maximumLineNumber = getMaximumSourceLineNumber();
        }
    }

    @Override
    public String getSyntaxStyle() {
        return SyntaxConstants.SYNTAX_STYLE_JAVA;
    }

    // --- ContentSavable --- //
    @Override
    public String getFileName() {
        String path = entry.getPath();
        int index = path.lastIndexOf('.');
        return path.substring(0, index) + ".java";
    }

    @Override
    public void save(API api, OutputStream os) {

        String decompiledOutput = "";
        
        // Init loader
        ContainerLoader loader = new ContainerLoader(entry);
        try {
            // Init preferences
            Map<String, String> preferences = api.getPreferences();

            // Format internal name
            String entryInternalName = ClassUtil.getInternalName(entry.getPath());

            String decompileEngine = preferences.getOrDefault(DECOMPILE_ENGINE, ENGINE_JD_CORE_V1);
            Loader apiLoader = new Loader(loader::canLoad, loader::load);
            decompiledOutput = StandardTransformers.decompile(apiLoader, entryInternalName, preferences, decompileEngine);
            if (decompiledOutput.contains(ByteCodeWriter.DECOMPILATION_FAILED_AT_LINE)) {
                String sourceCodeV0 = StandardTransformers.decompile(apiLoader, entryInternalName, preferences, ENGINE_JD_CORE_V0);
                decompiledOutput = MethodPatcher.patchCode(decompiledOutput, sourceCodeV0, entry);
            }
        } catch (Exception t) {
            assert ExceptionUtil.printStackTrace(t);
            decompiledOutput = INTERNAL_ERROR;
        }
        try (PrintStream ps = new PrintStream(os, true, StandardCharsets.UTF_8.name())) {
            ps.print(decompiledOutput);
        } catch (IOException e) {
            assert ExceptionUtil.printStackTrace(e);
        }
    }

    // --- LineNumberNavigable --- //
    @Override
    public int getMaximumLineNumber() {
        return maximumLineNumber;
    }

    @Override
    public void goToLineNumber(int lineNumber) {
        int textAreaLineNumber = getTextAreaLineNumber(lineNumber);
        if (textAreaLineNumber > 0) {
            try {
                int start = textArea.getLineStartOffset(textAreaLineNumber - 1);
                int end = textArea.getLineEndOffset(textAreaLineNumber - 1);
                setCaretPositionAndCenter(new DocumentRange(start, end));
            } catch (BadLocationException e) {
                assert ExceptionUtil.printStackTrace(e);
            }
        }
    }

    @Override
    public boolean checkLineNumber(int lineNumber) {
        return lineNumber <= maximumLineNumber;
    }

    // --- PreferencesChangeListener --- //
    @Override
    public void preferencesChanged(Map<String, String> preferences) {
        DynamicPage dynamicPage = (DynamicPage) getParent();
        ComponentRemovable tabbedPanel = (ComponentRemovable) dynamicPage.getParent().getParent();
        tabbedPanel.removeComponent(dynamicPage);
        api.openURI(getUri());
    }
}
