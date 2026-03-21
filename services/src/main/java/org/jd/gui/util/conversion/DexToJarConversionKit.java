/*
 * Copyright (c) 2008-2026 Emmanuel Dupuy and other contributors.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.util.conversion;

import com.googlecode.d2j.dex.Dex2jar;
import com.googlecode.d2j.reader.BaseDexFileReader;
import com.googlecode.d2j.reader.MultiDexFileReader;
import com.googlecode.dex2jar.tools.BaksmaliBaseDexExceptionHandler;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DexToJarConversionKit {

    private static final Map<ConversionKey, File> CACHE = new ConcurrentHashMap<>();

    private DexToJarConversionKit() {
    }

    public static File getOrCreate(File sourceFile) throws IOException {
        ConversionKey key = ConversionKey.of(sourceFile);
        File cached = CACHE.get(key);

        if (cached != null && cached.isFile()) {
            return cached;
        }

        File converted = convert(sourceFile);
        File previous = CACHE.putIfAbsent(key, converted);
        return previous != null && previous.isFile() ? previous : converted;
    }

    private static File convert(File sourceFile) throws IOException {
        Path temporaryJar = Files.createTempFile("jd-gui-dex2jar.", ".jar");
        temporaryJar.toFile().deleteOnExit();

        byte[] bytes = Files.readAllBytes(sourceFile.toPath());
        BaseDexFileReader reader = MultiDexFileReader.open(bytes);
        BaksmaliBaseDexExceptionHandler exceptionHandler = new BaksmaliBaseDexExceptionHandler();

        Dex2jar.from(reader)
            .withExceptionHandler(exceptionHandler)
            .skipDebug(false)
            .reUseReg(false)
            .topoLogicalSort(true)
            .to(temporaryJar);

        if (!Files.isRegularFile(temporaryJar) || Files.size(temporaryJar) == 0L) {
            throw new IOException("Dex to jar conversion produced no output for " + sourceFile.getAbsolutePath());
        }

        return temporaryJar.toFile();
    }

    private record ConversionKey(String path, long lastModified, long length) {
        private static ConversionKey of(File file) throws IOException {
            return new ConversionKey(file.getCanonicalPath(), file.lastModified(), file.length());
        }
    }
}
