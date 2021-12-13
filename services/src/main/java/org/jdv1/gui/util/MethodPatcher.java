package org.jdv1.gui.util;

import org.eclipse.jdt.core.dom.*;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ByteCodeWriter;
import org.jd.gui.api.model.Container;
import org.jd.gui.util.StringUtilities;
import org.jd.gui.util.parser.jdt.ASTParserFactory;
import org.jd.util.Range;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

public class MethodPatcher {

	private MethodPatcher() {
		super();
	}

	public static String patchCode(String sourceCodeV1, String sourceCodeV0, Container.Entry entry) {
		Map<String, Range> methodKeyPositionRanges = new HashMap<>();
		URI jarURI = entry.getContainer().getRoot().getParent().getUri();
		String unitName = entry.getPath();
		ASTParserFactory.getInstanceWithBindings().newASTParser(sourceCodeV1.toCharArray(), unitName, jarURI, new ASTVisitor() {

			@Override
			public boolean visit(MethodDeclaration node) {
				int methodStart = node.getBody().getStartPosition();
				int methodEnd = methodStart + node.getBody().getLength();
				String methodSource = sourceCodeV1.substring(methodStart, methodEnd);
				if (methodSource.contains(ByteCodeWriter.DECOMPILATION_FAILED_AT_LINE)) {
		            IMethodBinding methodBinding = node.resolveBinding();
			        if (methodBinding != null) {
						String methodKey = methodBinding.getKey();
						methodKeyPositionRanges.put(methodKey, Range.between(methodStart, methodEnd));
			        }
				}
				return super.visit(node);
			}
		});
		Map<Range, String> replacementMap = new HashMap<>();
		ASTParserFactory.getInstanceWithBindings().newASTParser(sourceCodeV0.toCharArray(), unitName, jarURI, new ASTVisitor() {

			@Override
			public boolean visit(MethodDeclaration node) {
		        int methodStart = node.getBody().getStartPosition();
		        int methodEnd = methodStart + node.getBody().getLength();
		        String methodV0 = sourceCodeV0.substring(methodStart, methodEnd);
		        IMethodBinding methodBinding = node.resolveBinding();
		        if (methodBinding != null) {
					String methodKey = methodBinding.getKey();
					Range rangeV1 = methodKeyPositionRanges.get(methodKey);
			        if (rangeV1 != null) {
			        	String methodV1 = sourceCodeV1.substring(rangeV1.minimum(), rangeV1.maximum());
			        	int methodV0LineCount = (int) methodV0.lines().count();
			        	int methodV1LineCount = (int) methodV1.lines().count();
			        	StringBuilder newMethod = new StringBuilder(methodV0);
			        	for (int i = 0; i < methodV1LineCount - methodV0LineCount; i++) {
			        		newMethod.append(System.lineSeparator());
			        	}
			        	replacementMap.put(rangeV1, newMethod.toString());
			        }
		        }
				return super.visit(node);
			}
		});
		return StringUtilities.applyModifications(sourceCodeV1, replacementMap);
	}


}
