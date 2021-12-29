/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jdv1.gui.view.component;

import org.fife.ui.rsyntaxtextarea.DocumentRange;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.jd.core.v1.ClassFileToJavaSourceDecompiler;
import org.jd.core.v1.api.loader.LoaderException;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ByteCodeWriter;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.core.v1.util.StringConstants;
import org.jd.gui.api.API;
import org.jd.gui.api.model.Container;
import org.jd.gui.util.decompiler.ClassFileSourcePrinter;
import org.jd.gui.util.decompiler.ContainerLoader;
import org.jd.gui.util.decompiler.GuiPreferences;
import org.jd.gui.util.io.NewlineOutputStream;
import org.jd.gui.util.parser.jdt.core.DeclarationData;
import org.jd.gui.util.parser.jdt.core.HyperlinkReferenceData;
import org.jd.gui.util.parser.jdt.core.StringData;
import org.jdv1.gui.util.MethodPatcher;
import org.jdv1.gui.util.decompiler.LineNumberStringBuilderPrinter;
import org.jdv1.gui.util.decompiler.StringBuilderPrinter;

import java.awt.Color;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;

import static org.jd.gui.util.Key.key;
import static org.jd.gui.util.decompiler.GuiPreferences.ESCAPE_UNICODE_CHARACTERS;
import static org.jd.gui.util.decompiler.GuiPreferences.REALIGN_LINE_NUMBERS;
import static org.jd.gui.util.decompiler.GuiPreferences.USE_JD_CORE_V0;

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

        // Init loader
        ContainerLoader loader = new ContainerLoader(entry);
        try {
            // Clear ...
            clearLineNumbers();
            listener.clearData();

            // Init preferences
            boolean realignmentLineNumbers = Boolean.parseBoolean(preferences.getOrDefault(REALIGN_LINE_NUMBERS, Boolean.FALSE.toString()));
            boolean unicodeEscape = Boolean.parseBoolean(preferences.getOrDefault(ESCAPE_UNICODE_CHARACTERS, Boolean.FALSE.toString()));

            if (Boolean.parseBoolean(preferences.getOrDefault(USE_JD_CORE_V0, Boolean.FALSE.toString()))) {
                decompileV0(preferences, loader);
                return;
            }

            Map<String, Object> configuration = new HashMap<>();
            configuration.put("realignLineNumbers", realignmentLineNumbers);

            setShowMisalignment(realignmentLineNumbers);

            // Init printer
            ClassFilePrinter printer = new ClassFilePrinter();
            printer.setRealignmentLineNumber(realignmentLineNumbers);
            printer.setUnicodeEscape(unicodeEscape);

            // Format internal name
            String entryPath = entry.getPath();
            assert entryPath.endsWith(StringConstants.CLASS_FILE_SUFFIX);
            String entryInternalName = entryPath.substring(0, entryPath.length() - 6); // 6 = ".class".length()

            // Decompile class file
            DECOMPILER.decompile(loader, printer, entryInternalName, configuration);

        } catch (Exception t) {
            assert ExceptionUtil.printStackTrace(t);
            if (Boolean.parseBoolean(preferences.getOrDefault(USE_JD_CORE_V0, Boolean.FALSE.toString()))) {
                setText(INTERNAL_ERROR);
            } else {
                decompileV0(preferences, loader);
            }
        } finally {
        	maximumLineNumber = getMaximumSourceLineNumber();
        }
    }

    protected void decompileV0(Map<String, String> preferences, ContainerLoader loader) {
        try {
            setText(decompileV0Internal(preferences, loader));
        } catch (LoaderException e) {
            assert ExceptionUtil.printStackTrace(e);
            setText(INTERNAL_ERROR);
        }
    }

    protected String decompileV0Internal(Map<String, String> preferences, ContainerLoader loader)
            throws LoaderException {
        // Init v0 preferences
        GuiPreferences prefs = new GuiPreferences(preferences);

        // Init printer v0
        Printer printer = new Printer(prefs);

        // Decompile class file
        DECOMPILERV0.decompile(prefs, loader, printer, entry.getPath());

        return printer.toString();
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

            if (Boolean.parseBoolean(preferences.getOrDefault(USE_JD_CORE_V0, "false"))) {
                decompiledOutput = decompileV0Internal(preferences, loader);
            } else {
                // Init printer
                LineNumberStringBuilderPrinter printer = new LineNumberStringBuilderPrinter();

                decompiledOutput = printer.buildDecompiledOutput(preferences, loader, entry, DECOMPILER);
            }

        } catch (Exception t) {
            assert ExceptionUtil.printStackTrace(t);
            if (!Boolean.parseBoolean(api.getPreferences().getOrDefault(USE_JD_CORE_V0, "false"))) {
                try {
                    decompiledOutput = decompileV0Internal(api.getPreferences(), loader);
                } catch (LoaderException e) {
                    assert ExceptionUtil.printStackTrace(e);
                }
            }
        }
        try (PrintStream ps = new PrintStream(new NewlineOutputStream(os), true, StandardCharsets.UTF_8.name())) {
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
        DefaultCaret caret = (DefaultCaret) textArea.getCaret();
        int updatePolicy = caret.getUpdatePolicy();

        caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
        decompile(preferences);
        caret.setUpdatePolicy(updatePolicy);

        super.preferencesChanged(preferences);
    }

    public class Printer extends ClassFileSourcePrinter {
        private final StringBuilder sb = new StringBuilder();
        private final boolean realignmentLineNumber;
        private final boolean showPrefixThis;
        private final boolean unicodeEscape;

        public Printer(GuiPreferences preferences) {
            this.realignmentLineNumber = preferences.getRealignmentLineNumber();
            this.showPrefixThis = preferences.isShowPrefixThis();
            this.unicodeEscape = preferences.isUnicodeEscape();
        }

        @Override
        public boolean getRealignmentLineNumber() {
            return realignmentLineNumber;
        }

        @Override
        public boolean isShowPrefixThis() {
            return showPrefixThis;
        }

        @Override
        public boolean isUnicodeEscape() {
            return unicodeEscape;
        }

        @Override
        public void append(char c) {
            sb.append(c);
        }

        @Override
        public void append(String s) {
            sb.append(s);
        }

        // Manage line number and misalignment
        private int textAreaLineNumber = 1;

        @Override
        public void start(int maxLineNumber, int majorVersion, int minorVersion) {
            super.start(maxLineNumber, majorVersion, minorVersion);

            if (maxLineNumber == 0) {
                scrollPane.setLineNumbersEnabled(false);
            } else {
                setMaxLineNumber(maxLineNumber);
            }
        }

        @Override
        public void startOfLine(int sourceLineNumber) {
            super.startOfLine(sourceLineNumber);
            setLineNumber(textAreaLineNumber, sourceLineNumber);
        }

        @Override
        public void endOfLine() {
            super.endOfLine();
            textAreaLineNumber++;
        }

        @Override
        public void extraLine(int count) {
            super.extraLine(count);
            if (realignmentLineNumber) {
                textAreaLineNumber += count;
            }
        }

        // --- Add strings --- //
        @Override
        public void printString(String s, String scopeInternalName) {
            listener.addStringData(new StringData(sb.length(), s, scopeInternalName));
            super.printString(s, scopeInternalName);
        }

        // --- Add references --- //
        @Override
        public void printTypeImport(String internalName, String name) {
            listener.addHyperlink(new HyperlinkReferenceData(sb.length(), name.length(),
                    listener.newReferenceData(internalName, null, null, null)));
            super.printTypeImport(internalName, name);
        }

        @Override
        public void printType(String internalName, String name, String scopeInternalName) {
            listener.addHyperlink(new HyperlinkReferenceData(sb.length(), name.length(),
                    listener.newReferenceData(internalName, null, null, scopeInternalName)));
            super.printType(internalName, name, scopeInternalName);
        }

        @Override
        public void printField(String internalName, String name, String descriptor, String scopeInternalName) {
            listener.addHyperlink(new HyperlinkReferenceData(sb.length(), name.length(),
                    listener.newReferenceData(internalName, name, descriptor, scopeInternalName)));
            super.printField(internalName, name, descriptor, scopeInternalName);
        }

        @Override
        public void printStaticField(String internalName, String name, String descriptor, String scopeInternalName) {
            listener.addHyperlink(new HyperlinkReferenceData(sb.length(), name.length(),
                    listener.newReferenceData(internalName, name, descriptor, scopeInternalName)));
            super.printStaticField(internalName, name, descriptor, scopeInternalName);
        }

        @Override
        public void printConstructor(String internalName, String name, String descriptor, String scopeInternalName) {
            listener.addHyperlink(new HyperlinkReferenceData(sb.length(), name.length(),
                    listener.newReferenceData(internalName, StringConstants.INSTANCE_CONSTRUCTOR, descriptor, scopeInternalName)));
            super.printConstructor(internalName, name, descriptor, scopeInternalName);
        }

        @Override
        public void printMethod(String internalName, String name, String descriptor, String scopeInternalName) {
            listener.addHyperlink(new HyperlinkReferenceData(sb.length(), name.length(),
                    listener.newReferenceData(internalName, name, descriptor, scopeInternalName)));
            super.printMethod(internalName, name, descriptor, scopeInternalName);
        }

        @Override
        public void printStaticMethod(String internalName, String name, String descriptor, String scopeInternalName) {
            listener.addHyperlink(new HyperlinkReferenceData(sb.length(), name.length(),
                    listener.newReferenceData(internalName, name, descriptor, scopeInternalName)));
            super.printStaticMethod(internalName, name, descriptor, scopeInternalName);
        }

        // --- Add declarations --- //
        @Override
        public void printTypeDeclaration(String internalName, String name) {
            DeclarationData data = new DeclarationData(sb.length(), name.length(), internalName, null, null);
            listener.addTypeDeclaration(sb.length(), internalName, data);
            super.printTypeDeclaration(internalName, name);
        }

        @Override
        public void printFieldDeclaration(String internalName, String name, String descriptor) {
            String key = key(internalName, name, descriptor);
            listener.addDeclaration(key, new DeclarationData(sb.length(), name.length(), internalName, name, descriptor));
            super.printFieldDeclaration(internalName, name, descriptor);
        }

        @Override
        public void printStaticFieldDeclaration(String internalName, String name, String descriptor) {
            String key = key(internalName, name, descriptor);
            listener.addDeclaration(key, new DeclarationData(sb.length(), name.length(), internalName, name, descriptor));
            super.printStaticFieldDeclaration(internalName, name, descriptor);
        }

        @Override
        public void printConstructorDeclaration(String internalName, String name, String descriptor) {
            listener.addDeclaration(key(internalName, StringConstants.INSTANCE_CONSTRUCTOR, descriptor),
                    new DeclarationData(sb.length(), name.length(), internalName, StringConstants.INSTANCE_CONSTRUCTOR, descriptor));
            super.printConstructorDeclaration(internalName, name, descriptor);
        }

        @Override
        public void printStaticConstructorDeclaration(String internalName, String name) {
            listener.addDeclaration(key(internalName, "<clinit>", "()V"),
                    new DeclarationData(sb.length(), name.length(), internalName, "<clinit>", "()V"));
            super.printStaticConstructorDeclaration(internalName, name);
        }

        @Override
        public void printMethodDeclaration(String internalName, String name, String descriptor) {
            listener.addDeclaration(key(internalName, name, descriptor),
                    new DeclarationData(sb.length(), name.length(), internalName, name, descriptor));
            super.printMethodDeclaration(internalName, name, descriptor);
        }

        @Override
        public void printStaticMethodDeclaration(String internalName, String name, String descriptor) {
            listener.addDeclaration(key(internalName, name, descriptor),
                    new DeclarationData(sb.length(), name.length(), internalName, name, descriptor));
            super.printStaticMethodDeclaration(internalName, name, descriptor);
        }

        @Override
        public String toString() {
            return sb.toString();
        }
    }

    public class ClassFilePrinter extends StringBuilderPrinter {

        // Manage line number and misalignment
        private int textAreaLineNumber = 1;

        @Override
        public void start(int maxLineNumber, int majorVersion, int minorVersion) {
            super.start(maxLineNumber, majorVersion, minorVersion);

            if (maxLineNumber == 0) {
                scrollPane.setLineNumbersEnabled(false);
            } else {
                setMaxLineNumber(maxLineNumber);
            }
        }

        @Override
        public void end() {
            String sourceCodeV1 = stringBuffer.toString();
			if (sourceCodeV1.contains(ByteCodeWriter.DECOMPILATION_FAILED_AT_LINE)) {
				listener.clearData();
				try {
					String sourceCodeV0 = decompileV0Internal(api.getPreferences(), new ContainerLoader(entry));
					String patchedCode = MethodPatcher.patchCode(sourceCodeV1, sourceCodeV0, entry);
					parseAndSetText(patchedCode);
		        } catch (LoaderException e) {
		            assert ExceptionUtil.printStackTrace(e);
		            setText(INTERNAL_ERROR);
		        }
			} else {
                setText(sourceCodeV1);
			}
        }

		// --- Add strings --- //
        @Override
        public void printStringConstant(String constant, String ownerInternalName) {
            if (constant == null) {
                constant = "null";
            }
            if (ownerInternalName == null) {
                ownerInternalName = "null";
            }

            listener.addStringData(new StringData(stringBuffer.length(), constant, ownerInternalName));
            super.printStringConstant(constant, ownerInternalName);
        }

        @Override
        public void printDeclaration(int type, String internalTypeName, String name, String descriptor) {
            if (internalTypeName == null) {
                internalTypeName = "null";
            }
            if (name == null) {
                name = "null";
            }
            if (descriptor == null) {
                descriptor = "null";
            }

            switch (type) {
            case TYPE:
                DeclarationData data = new DeclarationData(stringBuffer.length(), name.length(), internalTypeName, null,
                        null);
                listener.addTypeDeclaration(stringBuffer.length(), internalTypeName, data);
                break;
            case CONSTRUCTOR:
                listener.addDeclaration(key(internalTypeName, StringConstants.INSTANCE_CONSTRUCTOR, descriptor), new DeclarationData(stringBuffer.length(),
                        name.length(), internalTypeName, StringConstants.INSTANCE_CONSTRUCTOR, descriptor));
                break;
            default:
                listener.addDeclaration(key(internalTypeName, name, descriptor),
                        new DeclarationData(stringBuffer.length(), name.length(), internalTypeName, name, descriptor));
                break;
            }
            super.printDeclaration(type, internalTypeName, name, descriptor);
        }

        @Override
        public void printReference(int type, String internalTypeName, String name, String descriptor,
                String ownerInternalName) {
            if (internalTypeName == null) {
                internalTypeName = "null";
            }
            if (name == null) {
                name = "null";
            }
            if (descriptor == null) {
                descriptor = "null";
            }

            switch (type) {
            case TYPE:
                listener.addHyperlink(new HyperlinkReferenceData(stringBuffer.length(), name.length(),
                        listener.newReferenceData(internalTypeName, null, null, ownerInternalName)));
                break;
            case CONSTRUCTOR:
                listener.addHyperlink(new HyperlinkReferenceData(stringBuffer.length(), name.length(),
                        listener.newReferenceData(internalTypeName, StringConstants.INSTANCE_CONSTRUCTOR, descriptor, ownerInternalName)));
                break;
            default:
                listener.addHyperlink(new HyperlinkReferenceData(stringBuffer.length(), name.length(),
                        listener.newReferenceData(internalTypeName, name, descriptor, ownerInternalName)));
                break;
            }
            super.printReference(type, internalTypeName, name, descriptor, ownerInternalName);
        }

        @Override
        public void startLine(int lineNumber) {
            super.startLine(lineNumber);
            setLineNumber(textAreaLineNumber, lineNumber);
        }

        @Override
        public void endLine() {
            super.endLine();
            textAreaLineNumber++;
        }

        @Override
        public void extraLine(int count) {
            super.extraLine(count);
            if (realignmentLineNumber) {
                textAreaLineNumber += count;
            }
        }
    }
}
