/*
 * © 2008-2019 Emmanuel Dupuy
 * © 2022-2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */
package org.jd.gui.util.index;

import org.jd.gui.api.model.Container;
import org.jd.gui.api.model.Indexes;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.zip.ZipEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndexesUtilTest {

    // -------------------------------------------------------------------------
    // entryImpactBytes
    // -------------------------------------------------------------------------

    @Test
    void entryImpactBytes_basicCalculation() {
        ZipEntry entry = new ZipEntry("com/example/Foo.class");
        entry.setCompressedSize(1000);

        long nameBytes = "com/example/Foo.class".getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        // Local header: 30 + name + extra(0)
        // Central directory: 46 + name + extra(0) + comment(0)
        long expected = 1000 + 30 + nameBytes + 46 + nameBytes;
        assertEquals(expected, IndexesUtil.entryImpactBytes(entry));
    }

    @Test
    void entryImpactBytes_withNoExtraOrComment() {
        ZipEntry entry = new ZipEntry("a");
        entry.setCompressedSize(500);

        long nameBytes = 1; // "a"
        long expected = 500 + 30 + nameBytes + 46 + nameBytes;
        assertEquals(expected, IndexesUtil.entryImpactBytes(entry));
    }

    // -------------------------------------------------------------------------
    // Helper: build a ready Future<Indexes> holding a named index
    // -------------------------------------------------------------------------

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Future<Indexes> futureIndexWith(String indexName, String key, Container.Entry entry) {
        Map<String, Collection> index = new HashMap<>();
        List<Container.Entry> entries = new ArrayList<>();
        if (entry != null) {
            entries.add(entry);
        }
        index.put(key, entries);

        Indexes indexes = indexName2 -> indexName2.equals(indexName) ? index : null;
        return CompletableFuture.completedFuture(indexes);
    }

    // -------------------------------------------------------------------------
    // contains
    // -------------------------------------------------------------------------

    @Test
    void contains_returnsTrueWhenKeyFoundInDoneIndex() {
        Future<Indexes> fi = futureIndexWith("typeDeclarations", "org/example/Foo", null);
        assertTrue(IndexesUtil.contains(List.of(fi), "typeDeclarations", "org/example/Foo"));
    }

    @Test
    void contains_returnsFalseWhenKeyNotPresent() {
        Future<Indexes> fi = futureIndexWith("typeDeclarations", "org/example/Foo", null);
        assertFalse(IndexesUtil.contains(List.of(fi), "typeDeclarations", "org/example/Bar"));
    }

    @Test
    void contains_returnsFalseForEmptyCollection() {
        assertFalse(IndexesUtil.contains(Collections.emptyList(), "typeDeclarations", "org/example/Foo"));
    }

    @Test
    void contains_returnsFalseWhenIndexNameDoesNotMatch() {
        Future<Indexes> fi = futureIndexWith("strings", "some-key", null);
        assertFalse(IndexesUtil.contains(List.of(fi), "typeDeclarations", "some-key"));
    }

    // -------------------------------------------------------------------------
    // containsInternalTypeName
    // -------------------------------------------------------------------------

    @Test
    void containsInternalTypeName_delegatesToTypeDeclarationsIndex() {
        Future<Indexes> fi = futureIndexWith("typeDeclarations", "org/example/MyClass", null);
        assertTrue(IndexesUtil.containsInternalTypeName(List.of(fi), "org/example/MyClass"));
    }

    // -------------------------------------------------------------------------
    // find
    // -------------------------------------------------------------------------

    @Test
    void find_returnsEntriesForMatchingKey() {
        // Use a minimal anonymous Container.Entry implementation
        Container.Entry mockEntry = new StubEntry("org/example/Foo.class");
        Future<Indexes> fi = futureIndexWith("typeDeclarations", "org/example/Foo", mockEntry);

        List<Container.Entry> result = IndexesUtil.find(List.of(fi), "typeDeclarations", "org/example/Foo");

        assertEquals(1, result.size());
        assertEquals(mockEntry, result.get(0));
    }

    @Test
    void find_returnsEmptyListWhenKeyNotFound() {
        Future<Indexes> fi = futureIndexWith("typeDeclarations", "org/example/Foo", null);
        // null entry was added so we have a collection with one null element
        // Let's use a key that doesn't exist in this index
        List<Container.Entry> result = IndexesUtil.find(List.of(fi), "typeDeclarations", "org/example/Bar");
        assertTrue(result.isEmpty());
    }

    @Test
    void findInternalTypeName_delegatesToTypeDeclarationsIndex() {
        Container.Entry mockEntry = new StubEntry("org/example/Foo.class");
        Future<Indexes> fi = futureIndexWith("typeDeclarations", "org/example/Foo", mockEntry);

        List<Container.Entry> result = IndexesUtil.findInternalTypeName(List.of(fi), "org/example/Foo");

        assertEquals(1, result.size());
    }

    // -------------------------------------------------------------------------
    // Minimal Container.Entry stub
    // -------------------------------------------------------------------------

    private static class StubEntry implements Container.Entry {
        private final String path;

        StubEntry(String path) {
            this.path = path;
        }

        @Override
        public Container getContainer() { return null; }

        @Override
        public Container.Entry getParent() { return null; }

        @Override
        public java.net.URI getUri() { return java.net.URI.create("file:///" + path); }

        @Override
        public long length() { return 0; }

        @Override
        public long compressedLength() { return 0; }

        @Override
        public java.io.InputStream getInputStream() { return java.io.InputStream.nullInputStream(); }

        @Override
        public Map<Container.EntryPath, Container.Entry> getChildren() { return Collections.emptyMap(); }

        @Override
        public boolean isDirectory() { return false; }

        @Override
        public String getPath() { return path; }
    }
}
