package org.jd.gui.view.component;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.jd.gui.api.model.Container;
import org.junit.jupiter.api.Test;

import jd.core.links.HyperlinkData;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReferenceListenerTest {

    @Test
    void classInstanceCreation_keepsLinkOnInnerTypeSegment() {
        TestContainer container = new TestContainer();
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
