/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.util.index;

import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.api.model.Container;
import org.jd.gui.api.model.Indexes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.zip.ZipEntry;

import static java.nio.charset.StandardCharsets.UTF_8;

public final class IndexesUtil {

    private IndexesUtil() {
    }

    public static boolean containsInternalTypeName(Collection<Future<Indexes>> collectionOfFutureIndexes, String internalTypeName) {
        return contains(collectionOfFutureIndexes, "typeDeclarations", internalTypeName);
    }

    public static List<Container.Entry> findInternalTypeName(Collection<Future<Indexes>> collectionOfFutureIndexes, String internalTypeName) {
        return find(collectionOfFutureIndexes, "typeDeclarations", internalTypeName);
    }

    @SuppressWarnings("rawtypes")
    public static boolean contains(Collection<Future<Indexes>> collectionOfFutureIndexes, String indexName, String key) {
        try {
            for (Future<Indexes> futureIndexes : collectionOfFutureIndexes) {
                if (futureIndexes.isDone()) {
                    Map<String, Collection> index = futureIndexes.get().getIndex(indexName);
                    if (index != null && index.get(key) != null) {
                        return true;
                    }
                }
            }
        } catch (InterruptedException e) {
            assert ExceptionUtil.printStackTrace(e);
            // Restore interrupted state...
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            assert ExceptionUtil.printStackTrace(e);
        }

        return false;
    }

    @SuppressWarnings({ "rawtypes" })
    public static List<Container.Entry> find(Collection<Future<Indexes>> collectionOfFutureIndexes, String indexName, String key) {
        List<Container.Entry> entries = new ArrayList<>();

        try {
            for (Future<Indexes> futureIndexes : collectionOfFutureIndexes) {
                if (futureIndexes.isDone()) {
                    Map<String, Collection> index = futureIndexes.get().getIndex(indexName);
                    if (index != null) {
                        @SuppressWarnings("unchecked")
                        Collection<Container.Entry> collection = index.get(key);
                        if (collection != null) {
                            entries.addAll(collection);
                        }
                    }
                }
            }
        } catch (InterruptedException e) {
            assert ExceptionUtil.printStackTrace(e);
            // Restore interrupted state...
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            assert ExceptionUtil.printStackTrace(e);
        }

        return entries;
    }

    /*
     * https://jakewharton.com/calculating-zip-file-entry-true-impact/
     */
    public static long entryImpactBytes(ZipEntry entry) {
        int nameSize = entry.getName().getBytes(UTF_8).length;
        int extraSize = entry.getExtra() != null
            ? entry.getExtra().length
            : 0;
        int commentSize = entry.getComment() != null
            ? entry.getComment().getBytes(UTF_8).length
            : 0;

        // Calculate the actual compressed size impact in the zip, not just compressed data size.
        // See https://en.wikipedia.org/wiki/Zip_(file_format)#File_headers for details.
        return entry.getCompressedSize()
            // Local file header. There is no way of knowing whether a trailing data descriptor
            // was present since the general flags field is not exposed, but it's unlikely.
            + 30 + nameSize + extraSize
            // Central directory file header.
            + 46 + nameSize + extraSize + commentSize;
      }
}
