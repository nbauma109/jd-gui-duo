package org.jd.gui.view.component;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.jd.gui.test.MemoryContainer;
import org.junit.jupiter.api.Test;

import jd.core.links.HyperlinkData;

import java.util.NavigableMap;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReferenceListenerTest {

    @Test
    void classInstanceCreation_keepsLinkOnInnerTypeSegment() {
        MemoryContainer container = new MemoryContainer();
        container.addFile("p/Test.java");
        container.addFile("p/Evaluator.java");

        String source = """
                package p;

                class Test {
                    void m() {
                        new Evaluator.Tag("x");
                    }
                }
                """;

        ReferenceListener listener = new ReferenceListener(container.getEntry("p/Test.java"));
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(source.toCharArray());

        CompilationUnit cu = (CompilationUnit) parser.createAST(null);
        cu.accept(listener);

        int tagOffset = source.indexOf("Tag");
        NavigableMap<Integer, HyperlinkData> hyperlinks = new TreeMap<>(listener.getHyperlinks());
        var hyperlinkEntry = hyperlinks.floorEntry(tagOffset);

        assertNotNull(hyperlinkEntry);
        assertTrue(tagOffset >= hyperlinkEntry.getValue().getStartPosition());
        assertTrue(tagOffset < hyperlinkEntry.getValue().getEndPosition());
    }
}
