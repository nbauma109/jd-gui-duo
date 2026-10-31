/*
 * © 2008-2019 Emmanuel Dupuy
 * © 2022-2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.view.component;

import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.DocumentRange;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ByteCodeWriter;
import org.jd.core.v1.parser.ParseException;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.core.v1.util.ParserRealigner;
import org.jd.gui.api.API;
import org.jd.gui.api.feature.BreakpointCapablePage;
import org.jd.gui.api.model.Container;
import org.jd.gui.util.MethodPatcher;
import org.jd.gui.util.decompiler.ContainerLoader;
import org.jd.gui.util.decompiler.GuiPreferences;
import org.jd.gui.util.loader.LoaderUtils;
import org.jd.gui.util.parser.jdt.core.manipulation.RemoveUnnecessaryCasts;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import com.heliosdecompiler.transformerapi.StandardTransformers;
import com.heliosdecompiler.transformerapi.common.Loader;
import com.heliosdecompiler.transformerapi.decompilers.Decompiler;
import com.heliosdecompiler.transformerapi.decompilers.cfr.CFRSettings;
import com.heliosdecompiler.transformerapi.decompilers.fernflower.FernflowerSettings;
import com.heliosdecompiler.transformerapi.decompilers.jadx.MapJadxArgs;
import com.heliosdecompiler.transformerapi.decompilers.jd.JDSettings;
import com.heliosdecompiler.transformerapi.decompilers.procyon.MapDecompilerSettings;
import com.heliosdecompiler.transformerapi.decompilers.vineflower.VineflowerSettings;

import java.awt.Color;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;

import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;

import org.eclipse.jdt.core.JavaCore;

import static com.heliosdecompiler.transformerapi.StandardTransformers.Decompilers.ENGINE_CFR;
import static com.heliosdecompiler.transformerapi.StandardTransformers.Decompilers.ENGINE_FERNFLOWER;
import static com.heliosdecompiler.transformerapi.StandardTransformers.Decompilers.ENGINE_JADX;
import static com.heliosdecompiler.transformerapi.StandardTransformers.Decompilers.ENGINE_JD_CORE;
import static com.heliosdecompiler.transformerapi.StandardTransformers.Decompilers.ENGINE_JD_CORE_V0;
import static com.heliosdecompiler.transformerapi.StandardTransformers.Decompilers.ENGINE_JD_CORE_V1;
import static com.heliosdecompiler.transformerapi.StandardTransformers.Decompilers.ENGINE_PROCYON;
import static com.heliosdecompiler.transformerapi.StandardTransformers.Decompilers.ENGINE_VINEFLOWER;
import static jd.core.preferences.Preferences.DISPLAY_DEFAULT_CONSTRUCTOR;
import static jd.core.preferences.Preferences.ESCAPE_UNICODE_CHARACTERS;
import static jd.core.preferences.Preferences.OMIT_THIS_PREFIX;
import static jd.core.preferences.Preferences.REALIGN_LINE_NUMBERS;
import static jd.core.preferences.Preferences.WRITE_LINE_NUMBERS;
import static jd.core.preferences.Preferences.WRITE_METADATA;
import static org.jd.gui.util.decompiler.GuiPreferences.DECOMPILE_ENGINE;
import static org.jd.gui.util.decompiler.GuiPreferences.ERROR_BACKGROUND_COLOR;
import static org.jd.gui.util.decompiler.GuiPreferences.ADVANCED_CLASS_LOOKUP;
import static org.jd.gui.util.decompiler.GuiPreferences.INCLUDE_RUNNING_VM_BOOT_CLASSPATH;
import static org.jd.gui.util.decompiler.GuiPreferences.JRE_SYSTEM_LIBRARY_PATH;
import static org.jd.gui.util.decompiler.GuiPreferences.REMOVE_UNNECESSARY_CASTS;
import static org.jd.gui.util.decompiler.GuiPreferences.SHOW_COMPILER_ERRORS;
import static org.jd.gui.util.decompiler.GuiPreferences.SHOW_COMPILER_INFO;
import static org.jd.gui.util.decompiler.GuiPreferences.SHOW_COMPILER_WARNINGS;

import jd.core.ClassUtil;
import jd.core.DecompilationResult;

public class ClassFilePage extends TypePage implements BreakpointCapablePage {

    private static final String INTERNAL_ERROR = "// INTERNAL ERROR //";

    private static final long serialVersionUID = 1L;
    private static final List<String> DECOMPILE_RELEVANT_PREFERENCES = List.of(
        DECOMPILE_ENGINE,
        REMOVE_UNNECESSARY_CASTS,
        WRITE_LINE_NUMBERS,
        WRITE_METADATA,
        ESCAPE_UNICODE_CHARACTERS,
        REALIGN_LINE_NUMBERS,
        OMIT_THIS_PREFIX,
        DISPLAY_DEFAULT_CONSTRUCTOR,
        ADVANCED_CLASS_LOOKUP,
        "StretchLines"
    );
    private static final List<String> PARSER_RELEVANT_PREFERENCES = List.of(
        SHOW_COMPILER_ERRORS,
        SHOW_COMPILER_WARNINGS,
        SHOW_COMPILER_INFO,
        INCLUDE_RUNNING_VM_BOOT_CLASSPATH,
        JRE_SYSTEM_LIBRARY_PATH,
        JavaCore.COMPILER_SOURCE,
        JavaCore.COMPILER_COMPLIANCE
    );

    private int maximumLineNumber = -1;
    private int lastDecompilePreferencesHash;
    private int lastParserPreferencesHash;
    private transient BiPredicate<String, Integer> breakpointToggleHandler;

    public ClassFilePage(API api, Container.Entry entry) {
        super(api, entry);
        Map<String, String> preferences = api.getPreferences();
        // Init view
        setErrorForeground(Color.decode(preferences.get(GuiPreferences.ERROR_BACKGROUND_COLOR)));
        // Display source
        decompile(preferences);
        lastDecompilePreferencesHash = computePreferencesHash(preferences, DECOMPILE_RELEVANT_PREFERENCES);
        lastParserPreferencesHash = computePreferencesHash(preferences, PARSER_RELEVANT_PREFERENCES);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        javax.swing.SwingUtilities.invokeLater(() -> api.syncBreakpointPage(this));
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
            boolean parserRealignmentRequired = isParserRealignmentRequired(engineName, realignmentLineNumbers);
            DecompilationResult decompilationResult = decompileForDisplay(apiLoader, entryInternalName, preferences, engineName, realignmentLineNumbers);
            if (decompilationResult.getDecompiledOutput().contains(ByteCodeWriter.DECOMPILATION_FAILED_AT_LINE)) {
                /*
                 * Sometimes JD-Core v0 decompiles with success where JD-Core v1 fails.
                 * In this case, patch JD-Core v0 method into JD-Core v1 method.
                 * It will appear with comment 'Patched from JD-Core V0'
                 */
                DecompilationResult sourceCodeV0 = decompileForDisplay(apiLoader, entryInternalName, preferences, ENGINE_JD_CORE_V0, realignmentLineNumbers);
                String patchedCode = MethodPatcher.patchCode(api, decompilationResult.getDecompiledOutput(), sourceCodeV0.getDecompiledOutput(), entry);
                if (removeUnnecessaryCasts) {
                    patchedCode = new RemoveUnnecessaryCasts(api, entry).process(patchedCode);
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
                if (!parserRealignmentRequired && decompilationResult.getMaxLineNumber() != 0) {
                    setMaxLineNumber(decompilationResult.getMaxLineNumber());
                }
                if (!parserRealignmentRequired) {
                    for (Map.Entry<Integer, Integer> entry : decompilationResult.getLineNumbers().entrySet()) {
                        Integer textAreaLineNumber = entry.getKey();
                        Integer sourceLineNumber = entry.getValue();
                        setLineNumber(textAreaLineNumber, sourceLineNumber);
                    }
                }
                if (parserRealignmentRequired || hyperlinks.isEmpty() || removeUnnecessaryCasts) {
                    /*
                     * if hyperlinks are empty, it means the links are not supported by the decompiler, so the JAVA parser is called to
                     * enable the links. Same thing in case 'Remove casts' options is activated, as it ruins the hyperlinks.
                     * Avoid shifting positions for all of them by re-parsing the source code.
                     */
                    if (removeUnnecessaryCasts) {
                        decompilationResult.setDecompiledOutput(new RemoveUnnecessaryCasts(api, entry).process(decompilationResult.getDecompiledOutput()));
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

    private static boolean isParserRealignmentRequired(String engineName, boolean realignmentLineNumbers) {
        Decompiler<?> decompiler = StandardTransformers.Decompilers.valueOf(engineName);
        return realignmentLineNumbers && !decompiler.supportsRealignment();
    }

    private static DecompilationResult decompileForDisplay(Loader apiLoader, String entryInternalName, Map<String, String> preferences,
            String engineName, boolean realignmentLineNumbers) throws IOException, IllegalAccessException, InvocationTargetException, ParseException {
        boolean parserRealignmentRequired = isParserRealignmentRequired(engineName, realignmentLineNumbers);
        Map<String, String> decompilePreferences = parserRealignmentRequired ? withLineNumberSettings(preferences, engineName) : preferences;
        DecompilationResult result = StandardTransformers.decompile(apiLoader, entryInternalName, decompilePreferences, engineName);

        if (parserRealignmentRequired && !result.getDecompiledOutput().contains(ByteCodeWriter.DECOMPILATION_FAILED_AT_LINE)) {
            result.setDecompiledOutput(new ParserRealigner().realign(result.getDecompiledOutput()));
        }

        return result;
    }

    private static Map<String, String> withLineNumberSettings(Map<String, String> preferences, String engineName) {
        Map<String, String> result = new HashMap<>(preferences);
        result.putAll(lineNumbersFor(engineName));
        return result;
    }

    private static Map<String, String> lineNumbersFor(String engineName) {
        return switch (engineName) {
            case ENGINE_JD_CORE_V0, ENGINE_JD_CORE_V1, ENGINE_JD_CORE -> JDSettings.lineNumbers();
            case ENGINE_CFR -> CFRSettings.lineNumbers();
            case ENGINE_PROCYON -> MapDecompilerSettings.lineNumbers();
            case ENGINE_FERNFLOWER -> FernflowerSettings.lineNumbers();
            case ENGINE_VINEFLOWER -> VineflowerSettings.lineNumbers();
            case ENGINE_JADX -> MapJadxArgs.lineNumbers();
            default -> Map.of();
        };
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
                decompilationResult.setDecompiledOutput(MethodPatcher.patchCode(api, decompilationResult.getDecompiledOutput(), sourceCodeV0.getDecompiledOutput(), entry));
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

    // --- BreakpointCapablePage --- //
    @Override
    public String getDebugTypeName() {
        return ClassUtil.getInternalName(entry.getPath()).replace('/', '.');
    }

    @Override
    public String getBreakpointMethodDescription(int lineNumber) {
        List<String[]> methods = new java.util.ArrayList<>(); // name, descriptor, synthetic
        String[] holder = new String[2];
        try (java.io.InputStream inputStream = entry.getInputStream()) {
            new ClassReader(inputStream).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    methods.add(new String[] { name, descriptor, Boolean.toString((access & Opcodes.ACC_SYNTHETIC) != 0) });
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitLineNumber(int line, org.objectweb.asm.Label start) {
                            if (line == lineNumber && holder[0] == null) {
                                holder[0] = name;
                                holder[1] = descriptor;
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_FRAMES);
        } catch (IOException | RuntimeException e) {
            assert ExceptionUtil.printStackTrace(e);
            return null;
        }
        if (holder[0] == null) {
            return null;
        }
        String[] source = enclosingSourceMethod(holder[0], holder[1], methods);
        return describeMethod(source[0], source[1]);
    }

    private static final java.util.regex.Pattern LAMBDA_NAME = java.util.regex.Pattern.compile("lambda\\$(.+)\\$\\d+");

    /** Lambda bodies are compiled to lambda$name$N: name the enclosing source method like Eclipse does. */
    private static String[] enclosingSourceMethod(String name, String descriptor, List<String[]> methods) {
        java.util.regex.Matcher lambda = LAMBDA_NAME.matcher(name);
        if (lambda.matches()) {
            for (String[] method : methods) {
                if (method[0].equals(lambda.group(1)) && "false".equals(method[2])) {
                    return new String[] { method[0], method[1] };
                }
            }
        }
        return new String[] { name, descriptor };
    }

    private String describeMethod(String name, String descriptor) {
        if ("<clinit>".equals(name)) {
            return "static {...}";
        }
        String simpleClassName = getDebugTypeName().substring(getDebugTypeName().lastIndexOf('.') + 1);
        StringBuilder description = new StringBuilder("<init>".equals(name) ? simpleClassName : name).append('(');
        Type[] arguments = Type.getArgumentTypes(descriptor);
        for (int i = 0; i < arguments.length; i++) {
            String argument = arguments[i].getClassName();
            description.append(i > 0 ? ", " : "").append(argument.substring(argument.lastIndexOf('.') + 1));
        }
        return description.append(')').toString();
    }

    @Override
    public void setBreakpointToggleHandler(BiPredicate<String, Integer> handler) {
        this.breakpointToggleHandler = handler;
    }

    @Override
    public void setBreakpointLine(int lineNumber, boolean enabled) {
        setBreakpointLineNumber(lineNumber, enabled);
    }

    @Override
    protected void toggleBreakpointLineNumber(int sourceLineNumber) {
        boolean nowEnabled = !isBreakpointLineNumber(sourceLineNumber);
        setBreakpointLineNumber(sourceLineNumber, nowEnabled);
        if (breakpointToggleHandler != null) {
            boolean vmEnabled = breakpointToggleHandler.test(getDebugTypeName(), sourceLineNumber);
            if (vmEnabled != nowEnabled) {
                setBreakpointLineNumber(sourceLineNumber, vmEnabled);
            }
        }
    }

    // --- PreferencesChangeListener --- //
    @Override
    public void preferencesChanged(Map<String, String> preferences) {
        setErrorForeground(Color.decode(preferences.get(ERROR_BACKGROUND_COLOR)));

        int decompilePreferencesHash = computePreferencesHash(preferences, DECOMPILE_RELEVANT_PREFERENCES);
        int parserPreferencesHash = computePreferencesHash(preferences, PARSER_RELEVANT_PREFERENCES);
        boolean decompileRequired = decompilePreferencesHash != lastDecompilePreferencesHash;
        boolean parserRefreshRequired = parserPreferencesHash != lastParserPreferencesHash;

        if (decompileRequired) {
            DefaultCaret caret = (DefaultCaret) textArea.getCaret();
            int updatePolicy = caret.getUpdatePolicy();

            caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
            decompile(preferences);
            caret.setUpdatePolicy(updatePolicy);

            lastDecompilePreferencesHash = decompilePreferencesHash;
            lastParserPreferencesHash = parserPreferencesHash;
        } else if (parserRefreshRequired) {
            textArea.forceReparsing(astParser);
            lastParserPreferencesHash = parserPreferencesHash;
        }

        super.preferencesChanged(preferences);
        if (decompileRequired) {
            indexesChanged(collectionOfFutureIndexes);
        }
    }

    static int computePreferencesHash(Map<String, String> preferences, List<String> keys) {
        int hash = 1;

        for (String key : keys) {
            hash = 31 * hash + Objects.toString(preferences.get(key), "").hashCode();
        }

        return hash;
    }
}
