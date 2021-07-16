/*
 * Copyright (c) 2008, 2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */
package org.jd.core.v1.service.converter.classfiletojavasyntax.util;

import org.apache.bcel.classfile.*;
import org.jd.core.v1.model.classfile.attribute.*;
import org.jd.core.v1.model.classfile.attribute.Annotations;
import org.jd.core.v1.model.javasyntax.expression.*;
import org.jd.core.v1.model.javasyntax.reference.*;
import org.jd.core.v1.model.javasyntax.reference.AnnotationElementValue;
import org.jd.core.v1.model.javasyntax.reference.ElementValuePair;
import org.jd.core.v1.model.javasyntax.type.ObjectType;
import org.jd.core.v1.model.javasyntax.type.PrimitiveType;

import java.util.List;
import java.util.Map.Entry;

public class AnnotationConverter implements ElementValueVisitor {
    protected TypeMaker typeMaker;
    protected BaseElementValue elementValue;

    public AnnotationConverter(TypeMaker typeMaker) {
        this.typeMaker = typeMaker;
    }

    public BaseAnnotationReference convert(Annotations visibles, Annotations invisibles) {
        if (visibles == null) {
            if (invisibles == null) {
                return null;
            }
            return convert(invisibles);
        }
        if (invisibles == null) {
            return convert(visibles);
        }
        AnnotationReferences<AnnotationReference> aral = new AnnotationReferences<>();

        for (Annotation a : visibles.getAnnotations()) {
            aral.add(convert(a));
        }
        for (Annotation a : invisibles.getAnnotations()) {
            aral.add(convert(a));
        }

        return aral;
    }

    protected BaseAnnotationReference convert(Annotations annotations) {
        Annotation[] as = annotations.getAnnotations();

        if (as.length == 1) {
            return convert(as[0]);
        }
        AnnotationReferences<AnnotationReference> aral = new AnnotationReferences<>(as.length);

        for (Annotation a : as) {
            aral.add(convert(a));
        }

        return aral;
    }

    protected AnnotationReference convert(Annotation annotation) {
        String descriptor = annotation.getDescriptor();

        assert descriptor != null && descriptor.length() > 2 && descriptor.charAt(0) == 'L' && descriptor.charAt(descriptor.length()-1) == ';';

        ObjectType ot = typeMaker.makeFromDescriptor(descriptor);
        List<Entry<String, AttributeElementValue>> elementValuePairs = annotation.getElementValuePairs();

        if (elementValuePairs == null) {
            return new AnnotationReference(ot);
        }
		if (elementValuePairs.size() == 1) {
			Entry<String, AttributeElementValue> elementValuePair = elementValuePairs.get(0);
            String elementName = elementValuePair.getKey();
            AttributeElementValue elementValue = elementValuePair.getValue();

            if ("value".equals(elementName)) {
                return new AnnotationReference(ot, convert(elementValue));
            }
            return new AnnotationReference(
                    ot,
                    new ElementValuePair(elementName, convert(elementValue)));
        }
		ElementValuePairs list = new ElementValuePairs(elementValuePairs.size());
		String elementName;
		AttributeElementValue elementValue;
		for (Entry<String, AttributeElementValue> elementValuePair : elementValuePairs) {
		    elementName = elementValuePair.getKey();
		    elementValue = elementValuePair.getValue();
		    list.add(new ElementValuePair(elementName, convert(elementValue)));
		}
		return new AnnotationReference(ot, list);
    }

    public BaseElementValue convert(AttributeElementValue ev) {
        ev.accept(this);
        return elementValue;
    }

    /** --- ElementValueVisitor --- */
    @Override
    public void visit(ElementValuePrimitiveType elementValuePrimitiveType) {
        switch (elementValuePrimitiveType.getType()) {
            case 'B':
                elementValue = new ExpressionElementValue(new IntegerConstantExpression(PrimitiveType.TYPE_BYTE, elementValuePrimitiveType.<ConstantInteger>getConstValue().getBytes()));
                break;
            case 'D':
                elementValue = new ExpressionElementValue(new DoubleConstantExpression(elementValuePrimitiveType.<ConstantDouble>getConstValue().getBytes()));
                break;
            case 'F':
                elementValue = new ExpressionElementValue(new FloatConstantExpression(elementValuePrimitiveType.<ConstantFloat>getConstValue().getBytes()));
                break;
            case 'I':
                elementValue = new ExpressionElementValue(new IntegerConstantExpression(PrimitiveType.TYPE_INT, elementValuePrimitiveType.<ConstantInteger>getConstValue().getBytes()));
                break;
            case 'J':
                elementValue = new ExpressionElementValue(new LongConstantExpression(elementValuePrimitiveType.<ConstantLong>getConstValue().getBytes()));
                break;
            case 'S':
                elementValue = new ExpressionElementValue(new IntegerConstantExpression(PrimitiveType.TYPE_SHORT, elementValuePrimitiveType.<ConstantInteger>getConstValue().getBytes()));
                break;
            case 'Z':
                elementValue = new ExpressionElementValue(new IntegerConstantExpression(PrimitiveType.TYPE_BOOLEAN, elementValuePrimitiveType.<ConstantInteger>getConstValue().getBytes()));
                break;
            case 'C':
                elementValue = new ExpressionElementValue(new IntegerConstantExpression(PrimitiveType.TYPE_CHAR, elementValuePrimitiveType.<ConstantInteger>getConstValue().getBytes()));
                break;
            case 's':
                elementValue = new ExpressionElementValue(new StringConstantExpression(elementValuePrimitiveType.<ConstantUtf8>getConstValue().getBytes()));
                break;
        }
    }

    @Override
    public void visit(ElementValueClassInfo elementValueClassInfo) {
        String classInfo = elementValueClassInfo.getClassInfo();
        ObjectType ot = typeMaker.makeFromDescriptor(classInfo);
        elementValue = new ExpressionElementValue(new TypeReferenceDotClassExpression(ot));
    }

    @Override
    public void visit(ElementValueAnnotationValue elementValueAnnotationValue) {
        Annotation annotationValue = elementValueAnnotationValue.getAnnotationValue();
        AnnotationReference annotationReference = convert(annotationValue);
        elementValue = new AnnotationElementValue(annotationReference);
    }

    @Override
    public void visit(ElementValueEnumConstValue elementValueEnumConstValue) {
        String descriptor = elementValueEnumConstValue.getDescriptor();

        if (descriptor == null || descriptor.length() <= 2 || descriptor.charAt(0) != 'L' || descriptor.charAt(descriptor.length()-1) != ';') {
            throw new IllegalArgumentException("AnnotationConverter.visit(elementValueEnumConstValue)");
        }

        ObjectType ot = typeMaker.makeFromDescriptor(descriptor);
        String constName = elementValueEnumConstValue.getConstName();
        String internalTypeName = descriptor.substring(1, descriptor.length()-1);
        elementValue = new ExpressionElementValue(new FieldReferenceExpression(ot, new ObjectTypeReferenceExpression(ot), internalTypeName, constName, descriptor));
    }

    @Override
    public void visit(ElementValueArrayValue elementValueArrayValue) {
        AttributeElementValue[] values = elementValueArrayValue.getValues();

        if (values == null) {
            elementValue = new ElementValueArrayInitializerElementValue();
        } else if (values.length == 1) {
            values[0].accept(this);
            elementValue = new ElementValueArrayInitializerElementValue(elementValue);
        } else {
            ElementValues list = new ElementValues(values.length);

            for (AttributeElementValue value : values) {
                value.accept(this);
                list.add(elementValue);
            }

            elementValue = new ElementValueArrayInitializerElementValue(list);
        }
    }
}
