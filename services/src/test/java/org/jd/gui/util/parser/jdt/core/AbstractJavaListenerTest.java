package org.jd.gui.util.parser.jdt.core;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.NameQualifiedType;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.eclipse.jdt.core.dom.SimpleType;
import org.jd.gui.test.MemoryContainer;
import org.jd.gui.api.model.Container;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbstractJavaListenerTest {

    @Test
    void resolveInternalTypeName_convertsInnerTypesToJvmNames() {
        MemoryContainer container = new MemoryContainer();
        container.addFile("p/Test.java");
        container.addFile("p/Outer.java");
        container.addFile("a/b/Outer.java");

        ResolvingListener listener = new ResolvingListener(container.getEntry("p/Test.java"));
        String source = """
                package p;
                import a.b.Outer.Inner;

                class Test {
                    Inner imported;
                    Outer.Inner samePackage;
                    a.b.Outer.Inner fullyQualified;
                }
                """;

        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(source.toCharArray());

        CompilationUnit cu = (CompilationUnit) parser.createAST(null);
        cu.accept(listener);

        assertEquals("a/b/Outer$Inner", listener.importedTypeNames.get("Inner"));
        assertTrue(listener.resolvedTypeNames.getOrDefault("Inner", List.of()).contains("a/b/Outer$Inner"));
        assertTrue(listener.resolvedTypeNames.getOrDefault("Outer.Inner", List.of()).contains("p/Outer$Inner"));
        assertTrue(listener.resolvedTypeNames.getOrDefault("a.b.Outer.Inner", List.of()).contains("a/b/Outer$Inner"));
    }

    private static final class ResolvingListener extends AbstractJavaListener {
        private final Map<String, String> importedTypeNames = new LinkedHashMap<>();
        private final Map<String, List<String>> resolvedTypeNames = new LinkedHashMap<>();

        private ResolvingListener(Container.Entry entry) {
            super(entry);
        }

        @Override
        public boolean visit(ImportDeclaration node) {
            boolean visited = super.visit(node);
            if (!node.isOnDemand()) {
                String simpleName = node.getName().getFullyQualifiedName()
                        .substring(node.getName().getFullyQualifiedName().lastIndexOf('.') + 1);
                importedTypeNames.put(simpleName,
                        nameToInternalTypeName.get(node.getName().getFullyQualifiedName()
                                .substring(node.getName().getFullyQualifiedName().lastIndexOf('.') + 1)));
            }
            return visited;
        }

        @Override
        public boolean visit(SimpleType node) {
            resolvedTypeNames.computeIfAbsent(node.toString(), key -> new ArrayList<>())
                    .add(resolveInternalTypeName(node));
            return true;
        }

        @Override
        public boolean visit(QualifiedType node) {
            resolvedTypeNames.computeIfAbsent(node.toString(), key -> new ArrayList<>())
                    .add(resolveInternalTypeName(node));
            return true;
        }

        @Override
        public boolean visit(NameQualifiedType node) {
            resolvedTypeNames.computeIfAbsent(node.toString(), key -> new ArrayList<>())
                    .add(resolveInternalTypeName(node));
            return true;
        }

        @Override
        protected boolean enterTypeDeclaration(AbstractTypeDeclaration node, int flag) {
            return true;
        }

        @Override
        protected void exitTypeDeclaration() {
            // nothing to do
        }
    }
}
