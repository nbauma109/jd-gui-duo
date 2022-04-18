package org.jd.gui.util.parser.jdt.core.manipulation;

import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.DeleteEdit;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.api.model.Container;
import org.jd.gui.util.parser.jdt.ASTParserFactory;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;

public final class RemoveUnnecessaryCasts {

    private Container.Entry entry;

    public RemoveUnnecessaryCasts(Container.Entry entry) {
        this.entry = entry;
    }

    public String process(String source) {
        String unitName = entry.getPath();
        URI jarURI = entry.getContainer().getRoot().getParent().getUri();
        Document document = new Document(source);
        ASTParser astParser = ASTParserFactory.getInstanceWithBindings().newASTParser(source.toCharArray(), unitName, jarURI);
        CompilationUnit cu = (CompilationUnit) astParser.createAST(null);
        ASTRewrite astRewrite = ASTRewrite.create(cu.getAST());
        TextEdit textEdit = astRewrite.rewriteAST(document, null);
        Set<Integer> unnecessaryCasts = new HashSet<>();
        IProblem[] problems = cu.getProblems();
        for (IProblem pb : problems) {
            if (pb.getID() == IProblem.UnnecessaryCast) {
                unnecessaryCasts.add(pb.getSourceStart());
            }
        }
        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(CastExpression node) {
                int start = node.getStartPosition();
                if (unnecessaryCasts.contains(start)) {
                    int length = node.getExpression().getStartPosition() - start;
                    textEdit.addChild(new DeleteEdit(start, length));
                }
                return true;
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
