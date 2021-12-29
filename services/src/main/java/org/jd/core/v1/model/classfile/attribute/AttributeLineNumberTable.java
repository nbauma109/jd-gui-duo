/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.core.v1.model.classfile.attribute;

import org.apache.bcel.classfile.LineNumber;

public class AttributeLineNumberTable implements Attribute {
    private final LineNumber[] lineNumberTable;

    public AttributeLineNumberTable(LineNumber[] lineNumberTable) {
        this.lineNumberTable = lineNumberTable;
    }

    public LineNumber[] getLineNumberTable() {
        return lineNumberTable;
    }

    public LineNumber getLineNumberTable(int i) {
    	return lineNumberTable[i];
    }
}
