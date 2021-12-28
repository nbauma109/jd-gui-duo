/*
 * Copyright (c) 2008, 2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.core.v1.model.classfile.attribute;

import java.util.List;
import java.util.Map.Entry;

public class Annotation {
    private String descriptor;
    private List<Entry<String, AttributeElementValue>> elementValuePairs;

    public Annotation(String descriptor, List<Entry<String,AttributeElementValue>> elementValuePairs) {
        this.descriptor = descriptor;
        this.elementValuePairs = elementValuePairs;
    }

    public String getDescriptor() {
        return descriptor;
    }

    public List<Entry<String, AttributeElementValue>> getElementValuePairs() {
        return elementValuePairs;
    }
}
