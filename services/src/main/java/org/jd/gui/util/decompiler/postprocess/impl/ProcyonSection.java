package org.jd.gui.util.decompiler.postprocess.impl;

import java.io.StringWriter;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.api.model.Container;
import org.jd.gui.api.model.Container.Entry;
import org.jd.gui.util.parser.jdt.ASTParserFactory;

import com.strobel.assembler.InputTypeLoader;
import com.strobel.decompiler.DecompilationOptions;
import com.strobel.decompiler.DecompilerSettings;
import com.strobel.decompiler.ITextOutput;
import com.strobel.decompiler.PlainTextOutput;
import com.strobel.decompiler.languages.java.JavaFormattingOptions;

public class ProcyonSection {

	private static final Pattern LINE_NUMBER_PATTERN = Pattern.compile("/\\*([ES])L:(\\d+)\\*/");

	private static final Map<String, String> regexReplaceMap = new HashMap<String, String>();
	static {
		regexReplaceMap.put("(if|for|while) \\((.*)\\) \\{(\\s*[^;]*;)\\s*\\}", "$1 ($2) $3");
		regexReplaceMap.put("\\}\\s*(else|while|catch|finally)", "} $1");
	}

	private Entry containerEntry;
	private Set<Integer> sourceStartLines;
	
	public ProcyonSection(Set<Integer> sourceStartLines, Container.Entry containerEntry) {
		this.containerEntry = containerEntry;
		this.sourceStartLines = sourceStartLines;
	}

	public Map<Integer, String> decompile(boolean showDebugLineNumbers) {
		try {
			JarEntryDecompiler decompiler = new JarEntryDecompiler();
			DecompilationOptions decompilationOptions = new DecompilationOptions();
			DecompilerSettings settings = new DecompilerSettings();
			settings.setJavaFormattingOptions(JavaFormattingOptions.createDefault());
			settings.setTypeLoader(new InputTypeLoader());
			settings.setShowDebugLineNumbers(true);
			decompilationOptions.setSettings(settings);
			URI jarURI = containerEntry.getContainer().getRoot().getParent().getUri();
			ITextOutput output = new PlainTextOutput(new StringWriter());
			decompiler.decompileJarEntry(jarURI, containerEntry.getPath(), decompilationOptions, output);
			return parse(output.toString(), showDebugLineNumbers);
		} catch (Exception e) {
			assert ExceptionUtil.printStackTrace(e);
			return null;
		}
	}

	public Map<Integer, String> parse(String sourceCode, boolean showDebugLineNumbers) {
		Map<Integer, String> methodsByStartLine = new HashMap<>();
		URI jarURI = containerEntry.getContainer().getRoot().getParent().getUri();
		String unitName = containerEntry.getPath();
		ASTParserFactory.getInstance().newASTParser(sourceCode.toCharArray(), unitName, jarURI, new ASTVisitor() {

			@Override
			public boolean visit(MethodDeclaration node) {
				int methodStart = node.getStartPosition();
				int methodEnd = methodStart + node.getLength();
				String methodSource = applyRegexReplacements(sourceCode.substring(methodStart, methodEnd));
				int sourceLineMin = Integer.MAX_VALUE;
				Matcher lineNumberMatcher = LINE_NUMBER_PATTERN.matcher(methodSource);
				while (lineNumberMatcher.find()) {
					int lineNumber = Integer.parseInt(lineNumberMatcher.group(2));
					sourceLineMin = Math.min(sourceLineMin, lineNumber);
				}
				if (sourceStartLines.contains(sourceLineMin)) {
					if (showDebugLineNumbers) {
						methodsByStartLine.put(sourceLineMin, methodSource);
					} else {
						methodsByStartLine.put(sourceLineMin, lineNumberMatcher.replaceAll(""));
					}
				}
				return true;
			}
		});
		return methodsByStartLine;

	}

	protected static String applyRegexReplacements(String s) {
		for (java.util.Map.Entry<String, String> entry : regexReplaceMap.entrySet()) {
			s = s.replaceAll(entry.getKey(), entry.getValue());
		}
		return s;
	}

}
