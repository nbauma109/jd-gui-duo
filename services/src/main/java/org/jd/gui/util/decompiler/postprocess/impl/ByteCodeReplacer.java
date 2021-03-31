package org.jd.gui.util.decompiler.postprocess.impl;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.jd.gui.api.model.Container;
import org.jd.gui.api.model.Container.Entry;
import org.jd.gui.util.StringUtilities;
import org.jd.gui.util.decompiler.postprocess.PostProcess;
import org.jd.gui.util.parser.jdt.ASTParserFactory;

import com.strobel.decompiler.ast.Range;

public class ByteCodeReplacer implements PostProcess {

	public static final String BYTE_CODE = "// Byte code:";

	private Container.Entry entry;

	private boolean showDebugLineNumbers;

	public ByteCodeReplacer(Entry entry, boolean showDebugLineNumbers) {
		this.entry = entry;
		this.showDebugLineNumbers = showDebugLineNumbers;
	}

	@Override
	public String process(String input) {
		Map<Integer, Range> byteCodeRangesByStartLine = parse(input);
		if (byteCodeRangesByStartLine.isEmpty()) {
			return input;
		}
		ProcyonSection procyonSection = new ProcyonSection(byteCodeRangesByStartLine.keySet(), entry);
		Map<Integer, String> codeSnippets = procyonSection.decompile(showDebugLineNumbers);
		Map<Range, String> patchMap = new HashMap<>();
		for (Map.Entry<Integer, Range> entry : byteCodeRangesByStartLine.entrySet()) {
			int sourceLineMin = entry.getKey();
			Range byteCodeRange = entry.getValue();
			String patch = codeSnippets.get(sourceLineMin);
			patchMap.put(byteCodeRange, patch);
		}
		return StringUtilities.applyModifications(input, patchMap);
	}

	public Map<Integer, Range> parse(String sourceCode) {
		Map<Integer, Range> byteCodeRangesByStartLine = new HashMap<>();
		URI jarURI = entry.getContainer().getRoot().getParent().getUri();
		String unitName = entry.getPath();
		ASTParserFactory.getInstance().newASTParser(sourceCode.toCharArray(), unitName, jarURI, new ASTVisitor() {

			@Override
			public boolean visit(MethodDeclaration node) {
				int methodStart = node.getStartPosition();
				int methodEnd = methodStart + node.getLength();
				String methodSource = sourceCode.substring(methodStart, methodEnd);
				if (methodSource.contains(BYTE_CODE)) {
					try (Scanner sc = new Scanner(methodSource)) {
						int sourceLineMin = Integer.MAX_VALUE;
						while (sc.hasNextLine()) {
							String line = sc.nextLine();
							int idx1 = line.indexOf('#');
							int idx2 = line.indexOf("->");
							if (idx1 != -1 && idx2 != -1) {
								int sourceLineNumber = Integer.parseInt(line.substring(idx1 + 1, idx2).trim());
								sourceLineMin = Math.min(sourceLineMin, sourceLineNumber);
							}
						}
						byteCodeRangesByStartLine.put(sourceLineMin, new Range(methodStart, methodEnd));
					}
				}

				return true;
			}
		});
		return byteCodeRangesByStartLine;
	}
}
