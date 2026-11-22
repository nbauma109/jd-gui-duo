/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.util.conversion;

import com.googlecode.d2j.Field;
import com.googlecode.d2j.dex.writer.DexFileWriter;
import com.googlecode.d2j.visitors.DexClassVisitor;

/** A dex file of two classes (demo.Tiny and demo.Tiny$Inner), written with the writer of dex2jar. */
public final class TinyDex {

    private TinyDex() {
    }

    public static byte[] bytes() {
        DexFileWriter writer = new DexFileWriter();
        writer.visitDexFileVersion(35);
        DexClassVisitor tiny = writer.visit(1, "Ldemo/Tiny;", "Ljava/lang/Object;", null);
        tiny.visitSource("Tiny.java");
        tiny.visitField(1, new Field("Ldemo/Tiny;", "count", "I"), null).visitEnd();
        tiny.visitEnd();
        writer.visit(1, "Ldemo/Tiny$Inner;", "Ljava/lang/Object;", null).visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }
}
