package org.jd.gui.util;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ByteCodeWriter;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.api.model.Container;
import org.jd.gui.util.parser.jdt.ASTParserFactory;
import org.jd.util.Range;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

public final class MethodPatcher {

    private MethodPatcher() {
    }

    public static String patchCode(String sourceCodeV1, String sourceCodeV0, Container.Entry entry) {
        Map<String, Range> methodKeyPositionRanges = new HashMap<>();
        URI jarURI = entry.getContainer().getRoot().getParent().getUri();
        String unitName = entry.getPath();
        ASTParserFactory astParserFactory = ASTParserFactory.getInstanceWithBindings();
        CompilationUnit compilationUnit = (CompilationUnit) astParserFactory.newASTParser(sourceCodeV1.toCharArray(), unitName, jarURI).createAST(null);
        ASTRewrite rewriter = ASTRewrite.create(compilationUnit.getAST());
        Document document = new Document(sourceCodeV1);
        TextEdit textEdit = rewriter.rewriteAST(document, null);
        compilationUnit.accept(new ASTVisitor() {

            @Override
            public boolean visit(MethodDeclaration node) {
                if (node.getBody() != null) {
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
                }
                return super.visit(node);
            }
        });
        astParserFactory.newASTParser(sourceCodeV0.toCharArray(), unitName, jarURI).createAST(null).accept(new ASTVisitor() {

            @Override
            public boolean visit(MethodDeclaration node) {
                if (node.getBody() != null) {
                    int methodStart = node.getBody().getStartPosition();
                    int methodEnd = methodStart + node.getBody().getLength();
                    String methodV0 = sourceCodeV0.substring(methodStart, methodEnd);
                    IMethodBinding methodBinding = node.resolveBinding();
                    if (methodBinding != null) {
                        String methodKey = methodBinding.getKey();
                        Range rangeV1 = methodKeyPositionRanges.get(methodKey);
                        if (rangeV1 == null) {
                            methodKey = methodKey.replaceAll("L(\\w+/)*+", "L");
                            rangeV1 = methodKeyPositionRanges.get(methodKey);
                        }
                        if (rangeV1 != null) {
                            String methodV1 = sourceCodeV1.substring(rangeV1.minimum(), rangeV1.maximum());
                            int methodV0LineCount = (int) methodV0.lines().count();
                            int methodV1LineCount = (int) methodV1.lines().count();
                            StringBuilder newMethod = new StringBuilder(methodV0);
                            for (int i = 0; i < methodV1LineCount - methodV0LineCount; i++) {
                                newMethod.append(System.lineSeparator());
                            }
                            textEdit.addChild(new ReplaceEdit(rangeV1.minimum(), rangeV1.length(), newMethod.toString()));
                            textEdit.addChild(new InsertEdit(rangeV1.minimum(), "/* Patched from JD-Core V0 */"));
                        }
                    }
                }
                return super.visit(node);
            }
        });
        try {
            textEdit.apply(document);
        } catch (MalformedTreeException | BadLocationException e) {
            assert ExceptionUtil.printStackTrace(e);
        }
        return document.get();
    }
}
