/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.util.io;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class NewlineOutputStream extends FilterOutputStream {
    private static final byte[] lineSeparator = System.lineSeparator().getBytes(StandardCharsets.UTF_8);

    public NewlineOutputStream(OutputStream os) {
        super(os);
    }

    @Override
    public void write(int b) throws IOException {
        if (b == '\n') {
            out.write(lineSeparator);
        } else {
            out.write(b);
        }
    }

    @Override
    public void write(byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        int i;

        for (i=off; i<len; i++) {
            if (b[i] == '\n') {
                out.write(b, off, i-off);
                out.write(lineSeparator);
                off = i+1;
            }
        }

        out.write(b, off, i-off);
    }
}
