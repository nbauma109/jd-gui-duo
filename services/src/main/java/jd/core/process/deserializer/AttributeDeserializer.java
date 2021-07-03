/*******************************************************************************
 * Copyright (C) 2007-2019 Emmanuel Dupuy GPLv3
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package jd.core.process.deserializer;

import org.jd.core.v1.service.deserializer.classfile.attribute.InvalidAttributeLengthException;

import java.io.DataInput;
import java.io.IOException;

import jd.core.model.classfile.ConstantPool;
import jd.core.model.classfile.LocalVariable;
import jd.core.model.classfile.attribute.*;

public class AttributeDeserializer
{
    private AttributeDeserializer() {
        super();
    }

    public static Attribute[] deserialize(
            DataInput di, ConstantPool constants)
        throws IOException
    {
        int count = di.readUnsignedShort();
        if (count == 0)
            return null;

        Attribute[] attributes = new Attribute[count];

        for (int i=0; i<count; i++)
        {
            int attributeNameIndex = di.readUnsignedShort();
            int attributeLength = di.readInt();

            if (attributeNameIndex == constants.annotationDefaultAttributeNameIndex)
            {
                attributes[i] = new AttributeAnnotationDefault(
                        AttributeConstants.ATTR_ANNOTATION_DEFAULT,
                        attributeNameIndex,
                        AnnotationDeserializer.deserializeElementValue(di));
            }
            else if (attributeNameIndex == constants.codeAttributeNameIndex)
            {
                // skip max stack (1 short) and max locals (1 short) => skip 2 shorts = 4 bytes
                di.skipBytes(4);
                attributes[i] = new AttributeCode(
                        AttributeConstants.ATTR_CODE,
                        attributeNameIndex,
                        deserializeCode(di),
                        deserializeCodeExceptions(di),
                        deserialize(di, constants));
            }
            else if (attributeNameIndex == constants.constantValueAttributeNameIndex)
            {
                if (attributeLength != 2)
                    throw new InvalidAttributeLengthException();
                attributes[i] = new AttributeConstantValue(
                        AttributeConstants.ATTR_CONSTANT_VALUE,
                        attributeNameIndex,
                        di.readUnsignedShort());
            }
            else if (attributeNameIndex == constants.deprecatedAttributeNameIndex)
            {
                if (attributeLength != 0)
                    throw new InvalidAttributeLengthException();
                attributes[i] = new AttributeDeprecated(
                        AttributeConstants.ATTR_DEPRECATED,
                        attributeNameIndex);
            }
            else if (attributeNameIndex == constants.enclosingMethodAttributeNameIndex)
            {
                if (attributeLength != 4)
                    throw new InvalidAttributeLengthException();
                attributes[i] = new AttributeEnclosingMethod(
                        AttributeConstants.ATTR_ENCLOSING_METHOD,
                        attributeNameIndex,
                        di.readUnsignedShort(),
                        di.readUnsignedShort());
            }
            else if (attributeNameIndex == constants.exceptionsAttributeNameIndex)
            {
                attributes[i] = new AttributeExceptions(
                        AttributeConstants.ATTR_EXCEPTIONS,
                        attributeNameIndex,
                        deserializeExceptionIndexTable(di));
            }
            else if (attributeNameIndex == constants.innerClassesAttributeNameIndex)
            {
                attributes[i] = new AttributeInnerClasses(
                        AttributeConstants.ATTR_INNER_CLASSES,
                        attributeNameIndex,
                        deserializeInnerClasses(di));
            }
            else if (attributeNameIndex == constants.lineNumberTableAttributeNameIndex)
            {
                attributes[i] = new AttributeNumberTable(
                        AttributeConstants.ATTR_NUMBER_TABLE,
                        attributeNameIndex,
                        deserializeLineNumbers(di));
            }
            else if (attributeNameIndex == constants.localVariableTableAttributeNameIndex)
            {
                attributes[i] = new AttributeLocalVariableTable(
                        AttributeConstants.ATTR_LOCAL_VARIABLE_TABLE,
                        attributeNameIndex,
                        deserializeLocalVariable(di));
            }
            else if (attributeNameIndex == constants.localVariableTypeTableAttributeNameIndex)
            {
                attributes[i] = new AttributeLocalVariableTable(
                        AttributeConstants.ATTR_LOCAL_VARIABLE_TYPE_TABLE,
                        attributeNameIndex,
                        deserializeLocalVariable(di));
            }
            else if (attributeNameIndex == constants.runtimeInvisibleAnnotationsAttributeNameIndex)
            {
                attributes[i] = new AttributeRuntimeAnnotations(
                        AttributeConstants.ATTR_RUNTIME_INVISIBLE_ANNOTATIONS,
                        attributeNameIndex,
                        AnnotationDeserializer.deserialize(di));
            }
            else if (attributeNameIndex == constants.runtimeVisibleAnnotationsAttributeNameIndex)
            {
                attributes[i] = new AttributeRuntimeAnnotations(
                        AttributeConstants.ATTR_RUNTIME_VISIBLE_ANNOTATIONS,
                        attributeNameIndex,
                        AnnotationDeserializer.deserialize(di));
            }
            else if (attributeNameIndex == constants.runtimeInvisibleParameterAnnotationsAttributeNameIndex)
            {
                attributes[i] = new AttributeRuntimeParameterAnnotations(
                        AttributeConstants.ATTR_RUNTIME_INVISIBLE_PARAMETER_ANNOTATIONS,
                        attributeNameIndex,
                        deserializeParameterAnnotations(di));
            }
            else if (attributeNameIndex == constants.runtimeVisibleParameterAnnotationsAttributeNameIndex)
            {
                attributes[i] = new AttributeRuntimeParameterAnnotations(
                        AttributeConstants.ATTR_RUNTIME_VISIBLE_PARAMETER_ANNOTATIONS,
                        attributeNameIndex,
                        deserializeParameterAnnotations(di));
            }
            else if (attributeNameIndex == constants.signatureAttributeNameIndex)
            {
                if (attributeLength != 2)
                    throw new InvalidAttributeLengthException();
                attributes[i] = new AttributeSignature(
                        AttributeConstants.ATTR_SIGNATURE,
                        attributeNameIndex,
                        di.readUnsignedShort());
            }
            else if (attributeNameIndex == constants.sourceFileAttributeNameIndex)
            {
                if (attributeLength != 2)
                    throw new InvalidAttributeLengthException();
                attributes[i] = new AttributeSourceFile(
                        AttributeConstants.ATTR_SOURCE_FILE,
                        attributeNameIndex,
                        di.readUnsignedShort());
            }
            else if (attributeNameIndex == constants.syntheticAttributeNameIndex)
            {
                if (attributeLength != 0)
                    throw new InvalidAttributeLengthException();
                attributes[i] = new AttributeSynthetic(
                        AttributeConstants.ATTR_SYNTHETIC,
                        attributeNameIndex);
            }
            else
            {
                attributes[i] = new UnknowAttribute(
                        AttributeConstants.ATTR_UNKNOWN,
                        attributeNameIndex);
                for (int j=0; j<attributeLength; j++)
                    di.readByte();
            }
        }

        return attributes;
    }

    private static byte[] deserializeCode(DataInput di)
        throws IOException
    {
        int codeLength = di.readInt();
        if (codeLength == 0)
            return null;

        byte[] code = new byte[codeLength];
        di.readFully(code);

        return code;
    }

    private static CodeException[] deserializeCodeExceptions(DataInput di)
        throws IOException
    {
        int count = di.readUnsignedShort();
        if (count == 0)
            return null;

        CodeException[] codeExceptions = new CodeException[count];

        for (int i=0; i<count; i++)
            codeExceptions[i] = new CodeException(i,
                                                  di.readUnsignedShort(),
                                                  di.readUnsignedShort(),
                                                  di.readUnsignedShort(),
                                                  di.readUnsignedShort());
        return codeExceptions;
    }

    private static LineNumber[] deserializeLineNumbers(DataInput di)
        throws IOException
    {
        int count = di.readUnsignedShort();
        if (count == 0)
            return null;

        LineNumber[] lineNumbers = new LineNumber[count];

        for (int i=0; i<count; i++)
            lineNumbers[i] = new LineNumber(di.readUnsignedShort(),
                                            di.readUnsignedShort());
        return lineNumbers;
    }

    private static LocalVariable[] deserializeLocalVariable(DataInput di)
        throws IOException
    {
        int count = di.readUnsignedShort();
        if (count == 0)
            return null;

        LocalVariable[] localVariables = new LocalVariable[count];

        for (int i=0; i<count; i++)
            localVariables[i] = new LocalVariable(di.readUnsignedShort(),
                                                  di.readUnsignedShort(),
                                                  di.readUnsignedShort(),
                                                  di.readUnsignedShort(),
                                                  di.readUnsignedShort());

        return localVariables;
    }

    private static int[] deserializeExceptionIndexTable(DataInput di)
        throws IOException
    {
        int numberOfExceptions = di.readUnsignedShort();
        if (numberOfExceptions == 0)
            return null;

        int[] exceptionIndexTable = new int[numberOfExceptions];

        for(int i=0; i < numberOfExceptions; i++)
            exceptionIndexTable[i] = di.readUnsignedShort();

        return exceptionIndexTable;
    }

    private static InnerClass[] deserializeInnerClasses(DataInput di)
        throws IOException
    {
        int numberOfClasses = di.readUnsignedShort();
        if (numberOfClasses == 0)
            return null;

        InnerClass[] classes = new InnerClass[numberOfClasses];

        for(int i=0; i < numberOfClasses; i++)
            classes[i] = new InnerClass(di.readUnsignedShort(),
                                     di.readUnsignedShort(),
                                     di.readUnsignedShort(),
                                     di.readUnsignedShort());

        return classes;
    }

    private static ParameterAnnotations[] deserializeParameterAnnotations(
                                           DataInput di)
        throws IOException
    {
        int numParameters = di.readUnsignedByte();
        if (numParameters == 0)
            return null;

        ParameterAnnotations[] parameterAnnotations =
            new ParameterAnnotations[numParameters];

        for(int i=0; i < numParameters; i++)
            parameterAnnotations[i] = new ParameterAnnotations(
                    AnnotationDeserializer.deserialize(di));

        return parameterAnnotations;
    }
}
