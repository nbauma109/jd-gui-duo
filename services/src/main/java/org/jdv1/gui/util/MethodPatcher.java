package org.jdv1.gui.util;

import org.apache.commons.lang3.Range;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ByteCodeWriter;
import org.jd.gui.api.model.Container;
import org.jd.gui.util.StringUtilities;
import org.jd.gui.util.parser.jdt.ASTParserFactory;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntUnaryOperator;

public class MethodPatcher {

	private MethodPatcher() {
		super();
	}

	public static String patchCode(String sourceCodeV1, String sourceCodeV0, Container.Entry entry) {
		Map<Integer, Range<Integer>> lineToPositionRanges = new HashMap<>();
		URI jarURI = entry.getContainer().getRoot().getParent().getUri();
		String unitName = entry.getPath();
		ASTParserFactory.getInstance().newASTParser(sourceCodeV1.toCharArray(), unitName, jarURI, new ASTVisitor() {
			
			private IntUnaryOperator positionToLineNumber;
	
			@Override
			public boolean visit(CompilationUnit node) {
				positionToLineNumber = node::getLineNumber;
				return super.visit(node);
			}
			
			@Override
			public boolean visit(MethodDeclaration node) {
				int methodStart = node.getBody().getStartPosition();
				int methodEnd = methodStart + node.getBody().getLength();
				String methodSource = sourceCodeV1.substring(methodStart, methodEnd);
				if (methodSource.contains(ByteCodeWriter.DECOMPILATION_FAILED_AT_LINE)) {
					int sourceLineMin = positionToLineNumber.applyAsInt(methodStart);
		            lineToPositionRanges.put(sourceLineMin, Range.between(methodStart, methodEnd));
				}
				return super.visit(node);
			}
		});
		Map<Range<Integer>, String> replacementMap = new HashMap<>();
		ASTParserFactory.getInstance().newASTParser(sourceCodeV0.toCharArray(), unitName, jarURI, new ASTVisitor() {
			
			private IntUnaryOperator positionToLineNumber;
	
			@Override
			public boolean visit(CompilationUnit node) {
				positionToLineNumber = node::getLineNumber;
				return super.visit(node);
			}
			
			@Override
			public boolean visit(MethodDeclaration node) {
		        int methodStart = node.getBody().getStartPosition();
		        int methodEnd = methodStart + node.getBody().getLength();
		        String methodV0 = sourceCodeV0.substring(methodStart, methodEnd);
		        int sourceLineMin = positionToLineNumber.applyAsInt(methodStart);
		        Range<Integer> rangeV1 = lineToPositionRanges.get(sourceLineMin);
		        if (rangeV1 != null) {
		        	String methodV1 = sourceCodeV1.substring(rangeV1.getMinimum(), rangeV1.getMaximum());
		        	int methodV0LineCount = (int) methodV0.lines().count();
		        	int methodV1LineCount = (int) methodV1.lines().count();
		        	StringBuilder newMethod = new StringBuilder(methodV0);
		        	for (int i = 0; i < methodV1LineCount - methodV0LineCount; i++) {
		        		newMethod.append(System.lineSeparator());
		        	}
		        	replacementMap.put(rangeV1, newMethod.toString());
		        }
				return super.visit(node);
			}
		});
		return StringUtilities.applyModifications(sourceCodeV1, replacementMap);
	}
	
	
}
