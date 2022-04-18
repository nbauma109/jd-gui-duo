/*
 * Copyright (c) 2008, 2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.util.loader;

import org.jd.core.v1.api.loader.Loader;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ZipLoader implements Loader {

    private static final Pattern CLASS_SUFFIX_PATTERN = Pattern.compile("\\.class$");

    private HashMap<String, byte[]> map = new HashMap<>();

    public  ZipLoader(File zip) throws IOException {
        byte[] buffer = new byte[1024 * 2];

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zip))) {
            ZipEntry ze = zis.getNextEntry();

            while (ze != null) {
                if (!ze.isDirectory()) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    int read = zis.read(buffer);

                    while (read > 0) {
                        out.write(buffer, 0, read);
                        read = zis.read(buffer);
                    }

                    map.put(removeClassSuffix(ze.getName()), out.toByteArray());
                }

                ze = zis.getNextEntry();
            }

            zis.closeEntry();
        }
    }

    private static String removeClassSuffix(String entryName) {
        return CLASS_SUFFIX_PATTERN.matcher(entryName).replaceFirst("");
    }

    @Override
    public byte[] load(String internalName) throws IOException {
        return map.get(removeClassSuffix(internalName));
    }

    @Override
    public boolean canLoad(String internalName) {
        return map.containsKey(removeClassSuffix(internalName));
    }
}
