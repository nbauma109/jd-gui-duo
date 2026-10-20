/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.indexer;

import org.jd.gui.api.model.Container;
import org.jd.gui.api.model.Indexes;
import org.jd.gui.spi.Indexer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ModuleVisitor;
import org.objectweb.asm.Opcodes;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IndexerConcurrencyTest {
    @TempDir
    Path tempDir;

    @Test
    void classFilesKeepDeclarationsReferencesAndVisitorsSeparate() throws Exception {
        assertIndependent(new ClassFileIndexerProvider(),
                classFile("First"), classFile("Second"),
                Map.of("typeDeclarations", Set.of("First"),
                        "constructorDeclarations", Set.of("First"),
                        "methodDeclarations", Set.of("methodFirst"),
                        "fieldDeclarations", Set.of("fieldFirst", "stringFirst"),
                        "typeReferences", Set.of("OwnerFirst", "First", "ReferenceFirst", "AnnotationFirst", "NestedFirst", "ExceptionFirst", "java/lang/Object", "java/lang/String"),
                        "strings", Set.of("valueFirst"),
                        "fieldReferences", Set.of("referencedFieldFirst"),
                        "methodReferences", Set.of("referencedMethodFirst"),
                        "constructorReferences", Set.of("OwnerFirst"),
                        "subTypeNames", Set.of("java/lang/Object")),
                Map.of("typeDeclarations", Set.of("Second"),
                        "constructorDeclarations", Set.of("Second"),
                        "methodDeclarations", Set.of("methodSecond"),
                        "fieldDeclarations", Set.of("fieldSecond", "stringSecond"),
                        "typeReferences", Set.of("OwnerSecond", "Second", "ReferenceSecond", "AnnotationSecond", "NestedSecond", "ExceptionSecond", "java/lang/Object", "java/lang/String"),
                        "strings", Set.of("valueSecond"),
                        "fieldReferences", Set.of("referencedFieldSecond"),
                        "methodReferences", Set.of("referencedMethodSecond"),
                        "constructorReferences", Set.of("OwnerSecond"),
                        "subTypeNames", Set.of("java/lang/Object")));
    }

    @Test
    void moduleDescriptorsKeepDeclarationsAndReferencesSeparate() throws Exception {
        assertIndependent(new JavaModuleInfoFileIndexerProvider(),
                moduleFile("first"), moduleFile("second"),
                Map.of("javaModuleDeclarations", Set.of("first"),
                        "javaModuleReferences", Set.of("required.first", "exported.first", "opened.first"),
                        "typeReferences", Set.of("first/Main", "first/Service", "first/Provider")),
                Map.of("javaModuleDeclarations", Set.of("second"),
                        "javaModuleReferences", Set.of("required.second", "exported.second", "opened.second"),
                        "typeReferences", Set.of("second/Main", "second/Service", "second/Provider")));
    }

    @Test
    void xmlFilesKeepReadersAndBeanReferencesSeparate() throws Exception {
        assertIndependent(new XmlFileIndexerProvider(),
                xml("<bean class=\"first.Bean\">first</bean>"),
                xml("<bean class=\"second.Bean\">second</bean>"),
                Map.of("typeReferences", Set.of("first/Bean"), "strings", Set.of("1.0", "UTF-8", "bean", "class", "first")),
                Map.of("typeReferences", Set.of("second/Bean"), "strings", Set.of("1.0", "UTF-8", "bean", "class", "second")));
    }

    @Test
    void xmlBasedFilesKeepReadersSeparate() throws Exception {
        assertIndependent(new XmlBasedFileIndexerProvider(), xml("<first>one</first>"), xml("<second>two</second>"),
                Map.of("strings", Set.of("1.0", "UTF-8", "first", "one")),
                Map.of("strings", Set.of("1.0", "UTF-8", "second", "two")));
    }

    private void assertIndependent(Indexer provider, byte[] first, byte[] second,
            Map<String, Set<String>> firstExpected, Map<String, Set<String>> secondExpected) throws Exception {
        // Both calls must finish parsing before either publishes its first index.
        // Reusing provider fields corrupts the first result; serializing calls times out.
        CyclicBarrier publishing = new CyclicBarrier(2);
        TestIndexes firstIndexes = new TestIndexes(publishing);
        TestIndexes secondIndexes = new TestIndexes(publishing);
        MemoryEntry firstEntry = entry("first", first);
        MemoryEntry secondEntry = entry("second", second);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var firstTask = executor.submit(() -> provider.index(null, firstEntry, firstIndexes, () -> 0, value -> { }, () -> false));
            var secondTask = executor.submit(() -> provider.index(null, secondEntry, secondIndexes, () -> 0, value -> { }, () -> false));
            firstTask.get(10, TimeUnit.SECONDS);
            secondTask.get(10, TimeUnit.SECONDS);
        }
        assertResult(firstIndexes, firstExpected, firstEntry);
        assertResult(secondIndexes, secondExpected, secondEntry);
    }

    private static void assertResult(TestIndexes indexes, Map<String, Set<String>> expected, MemoryEntry entry) {
        expected.forEach((name, keys) -> {
            var index = indexes.values.get(name);
            assertEquals(keys, index.keySet(), name);
            List<?> expectedValues = name.equals("subTypeNames")
                    ? List.of(entry.getPath().equals("first") ? "First" : "Second") : List.of(entry);
            index.values().forEach(entries -> assertEquals(expectedValues, entries, name));
        });
    }

    private MemoryEntry entry(String name, byte[] bytes) throws Exception {
        Path file = Files.write(tempDir.resolve(name), bytes);
        return new MemoryEntry(name, bytes, file.toUri());
    }

    private static byte[] xml(String body) {
        return ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + body).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] classFile(String name) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        var annotation = writer.visitAnnotation("LAnnotation" + name + ";", true);
        annotation.visitAnnotation("nested", "LNested" + name + ";").visitEnd();
        annotation.visitEnd();
        writer.visitField(Opcodes.ACC_PUBLIC, "field" + name, "LReference" + name + ";", null, null).visitEnd();
        writer.visitField(Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, "string" + name, "Ljava/lang/String;", null, "value" + name).visitEnd();
        writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null).visitEnd();
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC, "method" + name, "()V", null, new String[] { "Exception" + name });
        method.visitCode();
        method.visitTypeInsn(Opcodes.NEW, "Owner" + name);
        method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, "Owner" + name, "<init>", "()V", false);
        method.visitFieldInsn(Opcodes.GETFIELD, "Owner" + name, "referencedField" + name, "I");
        method.visitInsn(Opcodes.POP);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "Owner" + name, "referencedMethod" + name, "()V", false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(2, 1);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] moduleFile(String name) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_MODULE, "module-info", null, null, null);
        ModuleVisitor module = writer.visitModule(name, 0, null);
        module.visitMainClass(name + "/Main");
        module.visitRequire("required." + name, 0, null);
        module.visitExport(name, 0, "exported." + name);
        module.visitOpen(name, 0, "opened." + name);
        module.visitUse(name + "/Service");
        module.visitProvide(name + "/Service", name + "/Provider");
        module.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    @SuppressWarnings("rawtypes")
    private static class TestIndexes implements Indexes {
        private final Map<String, Map<String, Collection>> values = new HashMap<>();
        private final CyclicBarrier publishing;
        private boolean first = true;

        TestIndexes(CyclicBarrier publishing) {
            this.publishing = publishing;
        }

        @Override
        public Map<String, Collection> getIndex(String name) {
            if (first) {
                first = false;
                try {
                    publishing.await(5, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new AssertionError("Indexing calls must overlap", e);
                }
            }
            return values.computeIfAbsent(name, key -> new HashMap<>() {
                @Override
                public Collection get(Object key) {
                    return computeIfAbsent((String) key, ignored -> new ArrayList<>());
                }
            });
        }
    }

    private record MemoryEntry(String path, byte[] bytes, URI uri) implements Container.Entry {
        @Override
        public Container getContainer() {
            return new Container() {
                @Override
                public String getType() { return "test"; }
                @Override
                public Entry getRoot() { return MemoryEntry.this; }
            };
        }
        @Override
        public Container.Entry getParent() { return this; }
        @Override
        public URI getUri() { return uri; }
        @Override
        public long length() { return bytes.length; }
        @Override
        public long compressedLength() { return bytes.length; }
        @Override
        public InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
        @Override
        public Map<Container.EntryPath, Container.Entry> getChildren() { return Map.of(); }
        @Override
        public boolean isDirectory() { return false; }
        @Override
        public String getPath() { return path; }
    }
}
