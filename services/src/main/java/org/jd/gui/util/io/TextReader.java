/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.util.io;

import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class TextReader {

    private TextReader() {
    }

    public static String getText(File file) {
        try (FileInputStream is = new FileInputStream(file)) {
            return getText(is);
        } catch (IOException e) {
            assert ExceptionUtil.printStackTrace(e);
            return "";
        }
    }

    public static String getText(InputStream is) {
        StringBuilder sb = new StringBuilder();
        char[] charBuffer = new char[8192];
        int nbCharRead;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            while ((nbCharRead = reader.read(charBuffer)) != -1) {
                // appends buffer
                sb.append(charBuffer, 0, nbCharRead);
            }
        } catch (IOException e) {
            assert ExceptionUtil.printStackTrace(e);
        }

        return sb.toString();
    }
}
