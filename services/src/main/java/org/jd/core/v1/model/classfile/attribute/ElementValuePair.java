package org.jd.core.v1.model.classfile.attribute;

public record ElementValuePair<T extends AttributeElementValue>(String elementName, T elementValue) {
}
