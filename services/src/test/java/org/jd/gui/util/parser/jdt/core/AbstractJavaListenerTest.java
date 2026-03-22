package org.jd.gui.util.parser.jdt.core;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.NameQualifiedType;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.eclipse.jdt.core.dom.SimpleType;
import org.jd.gui.api.model.Container;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbstractJavaListenerTest {

    @Test
    void resolveInternalTypeName_convertsInnerTypesToJvmNames() {
        TestContainer container = new TestContainer();
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
        }
    }

    private static final class TestContainer implements Container {
        private final TestEntry root = new TestEntry(this, null, "", true);
        private final Map<String, TestEntry> entriesByPath = new LinkedHashMap<>();

        private TestContainer() {
            entriesByPath.put("", root);
        }

        private void addFile(String path) {
            String[] segments = path.split("/");
            TestEntry parent = root;
            StringBuilder currentPath = new StringBuilder();

            for (int i = 0; i < segments.length; i++) {
                if (i > 0) {
                    currentPath.append('/');
                }
                currentPath.append(segments[i]);

                String entryPath = currentPath.toString();
                boolean directory = i < segments.length - 1;
                TestEntry entry = entriesByPath.get(entryPath);

                if (entry == null) {
                    entry = new TestEntry(this, parent, entryPath, directory);
                    parent.children.put(new TestEntryPath(entryPath, directory), entry);
                    entriesByPath.put(entryPath, entry);
                }

                parent = entry;
            }
        }

        private TestEntry getEntry(String path) {
            return entriesByPath.get(path);
        }

        @Override
        public String getType() {
            return "test";
        }

        @Override
        public Container.Entry getRoot() {
            return root;
        }
    }

    private static final class TestEntry implements Container.Entry {
        private final TestContainer container;
        private final TestEntry parent;
        private final String path;
        private final boolean directory;
        private final Map<Container.EntryPath, Container.Entry> children = new LinkedHashMap<>();

        private TestEntry(TestContainer container, TestEntry parent, String path, boolean directory) {
            this.container = container;
            this.parent = parent;
            this.path = path;
            this.directory = directory;
        }

        @Override
        public Container getContainer() {
            return container;
        }

        @Override
        public TestEntry getParent() {
            return parent;
        }

        @Override
        public URI getUri() {
            return URI.create("memory:/" + path);
        }

        @Override
        public long length() {
            return 0;
        }

        @Override
        public long compressedLength() {
            return 0;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public Map<Container.EntryPath, Container.Entry> getChildren() {
            return children;
        }

        @Override
        public boolean isDirectory() {
            return directory;
        }

        @Override
        public String getPath() {
            return path;
        }
    }

    private record TestEntryPath(String path, boolean directory) implements Container.EntryPath {
        @Override
        public boolean isDirectory() {
            return directory;
        }

        @Override
        public String getPath() {
            return path;
        }
    }
}
