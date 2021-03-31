/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jdv1.gui.view.component;

import static org.jd.gui.util.Key.key;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;

import org.fife.ui.rsyntaxtextarea.DocumentRange;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.jd.core.v1.ClassFileToJavaSourceDecompiler;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.api.API;
import org.jd.gui.api.model.Container;
import org.jd.gui.util.decompiler.ClassFileSourcePrinter;
import org.jd.gui.util.decompiler.ContainerLoader;
import org.jd.gui.util.decompiler.GuiPreferences;
import org.jd.gui.util.decompiler.postprocess.impl.ByteCodeReplacer;
import org.jd.gui.util.io.NewlineOutputStream;
import org.jd.gui.util.parser.jdt.core.DeclarationData;
import org.jd.gui.util.parser.jdt.core.HyperlinkReferenceData;
import org.jd.gui.util.parser.jdt.core.StringData;
import org.jdv1.gui.util.decompiler.LineNumberStringBuilderPrinter;
import org.jdv1.gui.util.decompiler.StringBuilderPrinter;

import jd.core.Decompiler;
import jd.core.loader.Loader;
import jd.core.loader.LoaderException;
import jd.core.process.DecompilerImpl;

public class ClassFilePage extends TypePage {

	private static final long serialVersionUID = 1L;

	protected static final String ESCAPE_UNICODE_CHARACTERS = "ClassFileDecompilerPreferences.escapeUnicodeCharacters";
	protected static final String REALIGN_LINE_NUMBERS = "ClassFileDecompilerPreferences.realignLineNumbers";
	protected static final String WRITE_LINE_NUMBERS = "ClassFileSaverPreferences.writeLineNumbers";
	protected static final String WRITE_METADATA = "ClassFileSaverPreferences.writeMetadata";
	protected static final String OMIT_THIS_PREFIX = "ClassFileViewerPreferences.omitThisPrefix";
	protected static final String DISPLAY_DEFAULT_CONSTRUCTOR = "ClassFileViewerPreferences.displayDefaultConstructor";

	protected static final String JD_CORE_VERSION = "JdGuiPreferences.jdCoreVersion";

	protected static final ClassFileToJavaSourceDecompiler DECOMPILER = new ClassFileToJavaSourceDecompiler();
	protected static final Decompiler DECOMPILERV0 = new DecompilerImpl();

	protected int maximumLineNumber = -1;

	public ClassFilePage(API api, Container.Entry entry) {
		super(api, entry);
		Map<String, String> preferences = api.getPreferences();
		// Init view
		setErrorForeground(Color.decode(preferences.get("JdGuiPreferences.errorBackgroundColor")));
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
			boolean realignmentLineNumbers = getPreferenceValue(preferences, REALIGN_LINE_NUMBERS, false);
			boolean unicodeEscape = getPreferenceValue(preferences, ESCAPE_UNICODE_CHARACTERS, false);

			Map<String, Object> configuration = new HashMap<>();
			configuration.put("realignLineNumbers", realignmentLineNumbers);

			setShowMisalignment(realignmentLineNumbers);

			// Init printer
			ClassFilePrinter printer = new ClassFilePrinter();
			printer.setRealignmentLineNumber(realignmentLineNumbers);
			printer.setUnicodeEscape(unicodeEscape);

			// Format internal name
			String entryPath = entry.getPath();
			assert entryPath.endsWith(".class");
			String entryInternalName = entryPath.substring(0, entryPath.length() - 6); // 6 = ".class".length()

			// Decompile class file
			DECOMPILER.decompile(loader, printer, entryInternalName, configuration);

		} catch (Throwable t) {
			assert ExceptionUtil.printStackTrace(t);

			// Init preferences v0
			GuiPreferences p = new GuiPreferences();
			p.setUnicodeEscape(getPreferenceValue(preferences, ESCAPE_UNICODE_CHARACTERS, false));
			p.setShowPrefixThis(!getPreferenceValue(preferences, OMIT_THIS_PREFIX, false));
			p.setShowDefaultConstructor(getPreferenceValue(preferences, DISPLAY_DEFAULT_CONSTRUCTOR, false));
			p.setRealignmentLineNumber(getPreferenceValue(preferences, REALIGN_LINE_NUMBERS, false));

			// Init printer v0
			Printer printer = new Printer(p);

			// Decompile class file
			try {
				Loader proxyLoader = (Loader) Proxy.newProxyInstance(getClass().getClassLoader(),
						new Class<?>[] { Loader.class }, loader);
				DECOMPILERV0.decompile(p, proxyLoader, printer, entry.getPath());
				ByteCodeReplacer byteCodeReplacer = new ByteCodeReplacer(entry, false);
				setText(byteCodeReplacer.process(printer.toString()));
			} catch (LoaderException e) {
				assert ExceptionUtil.printStackTrace(t);
				setText("// INTERNAL ERROR //");
			}
		}

		maximumLineNumber = getMaximumLineNumber();
	}

	protected static boolean getPreferenceValue(Map<String, String> preferences, String key, boolean defaultValue) {
		String v = preferences.get(key);
		return (v == null) ? defaultValue : Boolean.valueOf(v);
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
		try {
			// Init preferences
			Map<String, String> preferences = api.getPreferences();
			boolean realignmentLineNumbers = getPreferenceValue(preferences, REALIGN_LINE_NUMBERS, false);
			boolean unicodeEscape = getPreferenceValue(preferences, ESCAPE_UNICODE_CHARACTERS, false);
			boolean showLineNumbers = getPreferenceValue(preferences, WRITE_LINE_NUMBERS, true);

			Map<String, Object> configuration = new HashMap<>();
			configuration.put("realignLineNumbers", realignmentLineNumbers);

			// Init loader
			ContainerLoader loader = new ContainerLoader(entry);

			// Init printer
			LineNumberStringBuilderPrinter printer = new LineNumberStringBuilderPrinter();
			printer.setRealignmentLineNumber(realignmentLineNumbers);
			printer.setUnicodeEscape(unicodeEscape);
			printer.setShowLineNumbers(showLineNumbers);

			// Format internal name
			String entryPath = entry.getPath();
			assert entryPath.endsWith(".class");
			String entryInternalName = entryPath.substring(0, entryPath.length() - 6); // 6 = ".class".length()

			// Decompile class file
			DECOMPILER.decompile(loader, printer, entryInternalName, configuration);

			StringBuilder stringBuffer = printer.getStringBuffer();

			// Metadata
			if (getPreferenceValue(preferences, WRITE_METADATA, true)) {
				// Add location
				String location = new File(entry.getUri()).getPath()
						// Escape "\ u" sequence to prevent "Invalid unicode" errors
						.replaceAll("(^|[^\\\\])\\\\u", "\\\\\\\\u");
				stringBuffer.append("\n\n/* Location:              ");
				stringBuffer.append(location);
				// Add Java compiler version
				int majorVersion = printer.getMajorVersion();

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
					stringBuffer.append(printer.getMinorVersion());
					stringBuffer.append(')');
				}
				// Add JD-Core version
				stringBuffer.append("\n * JD-Core Version:       ");
				stringBuffer.append(preferences.get(JD_CORE_VERSION));
				stringBuffer.append("\n */");
			}

			try (PrintStream ps = new PrintStream(new NewlineOutputStream(os), true, StandardCharsets.UTF_8.name())) {
				ps.print(stringBuffer.toString());
			} catch (IOException e) {
				assert ExceptionUtil.printStackTrace(e);
			}
		} catch (Throwable t) {
			assert ExceptionUtil.printStackTrace(t);
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
		protected StringBuilder sb = new StringBuilder();
		protected boolean realignmentLineNumber;
		protected boolean showPrefixThis;
		protected boolean unicodeEscape;

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
		int textAreaLineNumber = 1;

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
					listener.newReferenceData(internalName, "<init>", descriptor, scopeInternalName)));
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
			listener.addDeclaration(key(internalName, "<init>", descriptor),
					new DeclarationData(sb.length(), name.length(), internalName, "<init>", descriptor));
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
		int textAreaLineNumber = 1;

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
			String currentSourceCode = stringBuffer.toString();
			if (currentSourceCode.contains(ByteCodeReplacer.BYTE_CODE)) {
				listener.clearData();
				ByteCodeReplacer byteCodeReplacer = new ByteCodeReplacer(entry, false);
				String newSourceCode = byteCodeReplacer.process(currentSourceCode);
				parseAndSetText(newSourceCode);
			} else {
				setText(currentSourceCode);
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
				listener.addDeclaration(key(internalTypeName, "<init>", descriptor), new DeclarationData(stringBuffer.length(),
						name.length(), internalTypeName, "<init>", descriptor));
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
						listener.newReferenceData(internalTypeName, "<init>", descriptor, ownerInternalName)));
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
