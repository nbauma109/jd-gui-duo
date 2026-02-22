/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.view.component;

import org.fife.ui.rsyntaxtextarea.DocumentRange;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ByteCodeWriter;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.api.API;
import org.jd.gui.api.model.Container;
import org.jd.gui.util.MethodPatcher;
import org.jd.gui.util.decompiler.ContainerLoader;
import org.jd.gui.util.decompiler.GuiPreferences;
import org.jd.gui.util.loader.LoaderUtils;
import org.jd.gui.util.parser.jdt.core.manipulation.RemoveUnnecessaryCasts;

import com.heliosdecompiler.transformerapi.StandardTransformers;
import com.heliosdecompiler.transformerapi.common.Loader;

import java.awt.Color;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;

import static com.heliosdecompiler.transformerapi.StandardTransformers.Decompilers.ENGINE_JD_CORE_V0;
import static com.heliosdecompiler.transformerapi.StandardTransformers.Decompilers.ENGINE_JD_CORE_V1;
import static jd.core.preferences.Preferences.REALIGN_LINE_NUMBERS;
import static org.jd.gui.util.decompiler.GuiPreferences.DECOMPILE_ENGINE;
import static org.jd.gui.util.decompiler.GuiPreferences.REMOVE_UNNECESSARY_CASTS;

import jd.core.ClassUtil;
import jd.core.DecompilationResult;

public class ClassFilePage extends TypePage {

    private static final String INTERNAL_ERROR = "// INTERNAL ERROR //";

    private static final long serialVersionUID = 1L;

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

        boolean realignmentLineNumbers = "true".equals(preferences.get(REALIGN_LINE_NUMBERS)) || "true".equals(preferences.get("StretchLines"));
        boolean removeUnnecessaryCasts = "true".equals(preferences.get(REMOVE_UNNECESSARY_CASTS));

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
            Loader apiLoader = LoaderUtils.createLoader(preferences, loader, entry);
            DecompilationResult decompilationResult = StandardTransformers.decompile(apiLoader, entryInternalName, preferences, engineName);
            if (decompilationResult.getDecompiledOutput().contains(ByteCodeWriter.DECOMPILATION_FAILED_AT_LINE)) {
                /*
                 * Sometimes JD-Core v0 decompiles with success where JD-Core v1 fails.
                 * In this case, patch JD-Core v0 method into JD-Core v1 method.
                 * It will appear with comment 'Patched from JD-Core V0'
                 */
                DecompilationResult sourceCodeV0 = StandardTransformers.decompile(apiLoader, entryInternalName, preferences, ENGINE_JD_CORE_V0);
                String patchedCode = MethodPatcher.patchCode(decompilationResult.getDecompiledOutput(), sourceCodeV0.getDecompiledOutput(), entry);
                if (removeUnnecessaryCasts) {
                    patchedCode = new RemoveUnnecessaryCasts(entry).process(patchedCode);
                }
                parseAndSetText(patchedCode);
            } else {
                /*
                 * Use the hyperlinks of the decompiler as preferred way providing links as it often provides more of them
                 */
                listener.getStrings().addAll(decompilationResult.getStrings());
                listener.getTypeDeclarations().putAll(decompilationResult.getTypeDeclarations());
                listener.getDeclarations().putAll(decompilationResult.getDeclarations());
                listener.getReferences().addAll(decompilationResult.getReferences());
                hyperlinks.putAll(decompilationResult.getHyperlinks());
                if (decompilationResult.getMaxLineNumber() != 0) {
                    setMaxLineNumber(decompilationResult.getMaxLineNumber());
                }
                for (Map.Entry<Integer, Integer> entry : decompilationResult.getLineNumbers().entrySet()) {
                    Integer textAreaLineNumber = entry.getKey();
                    Integer sourceLineNumber = entry.getValue();
                    setLineNumber(textAreaLineNumber, sourceLineNumber);
                }
                if (hyperlinks.isEmpty() || removeUnnecessaryCasts) {
                    /*
                     * if hyperlinks are empty, it means the links are not supported by the decompiler, so the JAVA parser is called to
                     * enable the links. Same thing in case 'Remove casts' options is activated, as it ruins the hyperlinks.
                     * Avoid shifting positions for all of them by re-parsing the source code.
                     */
                    if (removeUnnecessaryCasts) {
                        decompilationResult.setDecompiledOutput(new RemoveUnnecessaryCasts(entry).process(decompilationResult.getDecompiledOutput()));
                    }
                    parseAndSetText(decompilationResult.getDecompiledOutput());
                } else {
                    setText(decompilationResult.getDecompiledOutput());
                }
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

        DecompilationResult decompilationResult = new DecompilationResult();

        // Init loader
        ContainerLoader loader = new ContainerLoader(entry);
        try {
            // Init preferences
            Map<String, String> preferences = api.getPreferences();

            // Format internal name
            String entryInternalName = ClassUtil.getInternalName(entry.getPath());

            String decompileEngine = preferences.getOrDefault(DECOMPILE_ENGINE, ENGINE_JD_CORE_V1);
            Loader apiLoader = LoaderUtils.createLoader(preferences, loader, entry);
            decompilationResult = StandardTransformers.decompile(apiLoader, entryInternalName, preferences, decompileEngine);
            if (decompilationResult.getDecompiledOutput().contains(ByteCodeWriter.DECOMPILATION_FAILED_AT_LINE)) {
                DecompilationResult sourceCodeV0 = StandardTransformers.decompile(apiLoader, entryInternalName, preferences, ENGINE_JD_CORE_V0);
                decompilationResult.setDecompiledOutput(MethodPatcher.patchCode(decompilationResult.getDecompiledOutput(), sourceCodeV0.getDecompiledOutput(), entry));
            }
        } catch (Exception t) {
            assert ExceptionUtil.printStackTrace(t);
            decompilationResult.setDecompiledOutput(INTERNAL_ERROR);
        }
        try (PrintStream ps = new PrintStream(os, true, StandardCharsets.UTF_8.name())) {
            ps.print(decompilationResult.getDecompiledOutput());
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
        DefaultCaret caret = (DefaultCaret) textArea.getCaret();
        int updatePolicy = caret.getUpdatePolicy();

        caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
        decompile(preferences);
        caret.setUpdatePolicy(updatePolicy);

        super.preferencesChanged(preferences);
        indexesChanged(collectionOfFutureIndexes);
    }
}
