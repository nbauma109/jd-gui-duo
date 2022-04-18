package org.jd.gui.util.parser.jdt;

import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.fife.ui.rsyntaxtextarea.RSyntaxDocument;
import org.fife.ui.rsyntaxtextarea.parser.AbstractParser;
import org.fife.ui.rsyntaxtextarea.parser.DefaultParseResult;
import org.fife.ui.rsyntaxtextarea.parser.DefaultParserNotice;
import org.fife.ui.rsyntaxtextarea.parser.ParseResult;
import org.fife.ui.rsyntaxtextarea.parser.ParserNotice.Level;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.api.API;
import org.jd.gui.api.model.Container;
import org.jd.gui.api.model.Container.Entry;
import org.jd.gui.util.decompiler.GuiPreferences;

import java.net.URI;

import javax.swing.text.BadLocationException;

public class RSyntaxASTParser extends AbstractParser {

    protected final API api;
    protected final Container.Entry entry;

    public RSyntaxASTParser(Entry entry, API api) {
        this.api = api;
        this.entry = entry;
    }

    @Override
    public ParseResult parse(RSyntaxDocument doc, String style) {
        boolean showErrors = "true".equals(api.getPreferences().get(GuiPreferences.SHOW_COMPILER_ERRORS));
        boolean showWarnings = "true".equals(api.getPreferences().get(GuiPreferences.SHOW_COMPILER_WARNINGS));
        boolean showInfo = "true".equals(api.getPreferences().get(GuiPreferences.SHOW_COMPILER_INFO));
        DefaultParseResult result = new DefaultParseResult(this);
        try {
            String text = doc.getText(0, doc.getLength());
            String unitName = entry.getPath();
            URI jarURI = entry.getContainer().getRoot().getParent().getUri();
            ASTNode ast = ASTParserFactory.getInstanceWithBindings().newASTParser(text.toCharArray(), unitName, jarURI).createAST(null);
            if (ast instanceof CompilationUnit) {
                CompilationUnit cu = (CompilationUnit) ast;
                IProblem[] problems = cu.getProblems();
                for (IProblem pb : problems) {
                    int sourceStart = pb.getSourceStart();
                    int length = pb.getSourceEnd() - sourceStart + 1;
                    String message = pb.getMessage();
                    int lineNo = pb.getSourceLineNumber();
                    DefaultParserNotice notice = new DefaultParserNotice(this, message, lineNo, sourceStart, length);
                    if (pb.isError()) {
                        notice.setLevel(Level.ERROR);
                    }
                    if (pb.isWarning()) {
                        notice.setLevel(Level.WARNING);
                    }
                    if (pb.isInfo()) {
                        notice.setLevel(Level.INFO);
                    }
                    if ((pb.isError() && showErrors) || (pb.isWarning() && showWarnings) || (pb.isInfo() && showInfo)) {
                        result.addNotice(notice);
                    }
                }
            }
        } catch (BadLocationException e) {
            assert ExceptionUtil.printStackTrace(e);
        }
        return result;
    }

}
