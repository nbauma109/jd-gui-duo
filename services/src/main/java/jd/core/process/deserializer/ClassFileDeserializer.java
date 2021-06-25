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

import org.jd.core.v1.api.loader.Loader;
import org.jd.core.v1.api.loader.LoaderException;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.core.v1.util.StringConstants;

import java.io.ByteArrayInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import jd.core.CoreConstants;
import jd.core.model.classfile.ClassFile;
import jd.core.model.classfile.ConstantPool;
import jd.core.model.classfile.Field;
import jd.core.model.classfile.Method;
import jd.core.model.classfile.attribute.Attribute;
import jd.core.model.classfile.attribute.AttributeInnerClasses;
import jd.core.model.classfile.attribute.InnerClass;
import jd.core.model.classfile.constant.*;

public class ClassFileDeserializer
{
    private ClassFileDeserializer() {
        super();
    }

    public static ClassFile Deserialize(Loader loader, String internalClassPath)
        throws LoaderException
    {
        ClassFile classFile = LoadSingleClass(loader, internalClassPath);
        if (classFile == null)
            return null;

        AttributeInnerClasses aics = classFile.getAttributeInnerClasses();
        if (aics == null)
            return classFile;

        String internalClassPathPrefix =
            internalClassPath.substring(
                0, internalClassPath.length() - StringConstants.CLASS_FILE_SUFFIX.length());
        String innerInternalClassNamePrefix =
            internalClassPathPrefix + StringConstants.INTERNAL_INNER_SEPARATOR;
        ConstantPool constants = classFile.getConstantPool();

        InnerClass[] cs = aics.classes;
        int length = cs.length;
        List<ClassFile> innerClassFiles = new ArrayList<>(length);

        for (int i=0; i<length; i++)
        {
            String innerInternalClassPath =
                constants.getConstantClassName(cs[i].inner_class_index);

            if (! innerInternalClassPath.startsWith(innerInternalClassNamePrefix))
                continue;
            int offsetInternalInnerSeparator = innerInternalClassPath.indexOf(
                StringConstants.INTERNAL_INNER_SEPARATOR,
                innerInternalClassNamePrefix.length());
            if (offsetInternalInnerSeparator != -1)
            {
                String tmpInnerInternalClassPath =
                    innerInternalClassPath.substring(0, offsetInternalInnerSeparator) +
                    StringConstants.CLASS_FILE_SUFFIX;
                if (loader.canLoad(tmpInnerInternalClassPath))
                    // 'innerInternalClassName' is not a direct inner classe.
                    continue;
            }

            try
            {
                ClassFile innerClassFile =
                    Deserialize(loader, innerInternalClassPath +
                    StringConstants.CLASS_FILE_SUFFIX);

                if (innerClassFile != null)
                {
                    // Alter inner class access flag
                    innerClassFile.setAccessFlags(cs[i].inner_access_flags);
                    // Setup outer class reference
                    innerClassFile.setOuterClass(classFile);
                    // Add inner classes
                    innerClassFiles.add(innerClassFile);
                }
            }
            catch (LoaderException e)
            {
                assert ExceptionUtil.printStackTrace(e);
            }
        }

        // Add inner classes
        classFile.setInnerClassFiles(innerClassFiles);

        return classFile;
    }

    private static ClassFile LoadSingleClass(
            Loader loader, String internalClassPath)
        throws LoaderException
    {
        ClassFile classFile = null;

        try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(loader.load(internalClassPath))))
        {
            classFile = Deserialize(dis);
        }
        catch (IOException e)
        {
            assert ExceptionUtil.printStackTrace(e);
        }
        return classFile;
    }

    private static ClassFile Deserialize(DataInput di)
        throws IOException
    {
        CheckMagic(di);

        int minor_version = di.readUnsignedShort();
        int major_version = di.readUnsignedShort();

        Constant[] constants = DeserializeConstants(di);
        ConstantPool constantPool = new ConstantPool(constants);

        int access_flags = di.readUnsignedShort();
        int this_class = di.readUnsignedShort();
        int super_class = di.readUnsignedShort();

        int[] interfaces = DeserializeInterfaces(di);
        Field[] fieldInfos = DeserializeFields(di, constantPool);
        Method[] methodInfos = DeserializeMethods(di, constantPool);

        Attribute[] attributeInfos =
            AttributeDeserializer.Deserialize(di, constantPool);

        return new ClassFile(
                minor_version, major_version,
                constantPool,
                access_flags, this_class, super_class,
                interfaces,
                fieldInfos,
                methodInfos,
                attributeInfos
        );
    }

    private static Constant[] DeserializeConstants(DataInput di)
        throws IOException
    {
        int count = di.readUnsignedShort();
        if (count == 0)
            return null;

        Constant[] constants = new Constant[count];

        for (int i=1; i<count; i++)
        {
            byte tag = di.readByte();

            switch (tag)
            {
            case ConstantConstant.CONSTANT_CLASS:
                constants[i] = new ConstantClass(tag, di.readUnsignedShort());
                break;
            case ConstantConstant.CONSTANT_FIELDREF:
                constants[i] = new ConstantFieldref(tag,
                                                    di.readUnsignedShort(),
                                                    di.readUnsignedShort());
                break;
            case ConstantConstant.CONSTANT_METHODREF:
                constants[i] = new ConstantMethodref(tag,
                                                     di.readUnsignedShort(),
                                                     di.readUnsignedShort());
                break;
            case ConstantConstant.CONSTANT_INTERFACEMETHODREF:
                constants[i] = new ConstantInterfaceMethodref(
                                                       tag,
                                                       di.readUnsignedShort(),
                                                       di.readUnsignedShort());
                break;
            case ConstantConstant.CONSTANT_STRING:
                constants[i] = new ConstantString(tag, di.readUnsignedShort());
                break;
            case ConstantConstant.CONSTANT_INTEGER:
                constants[i] = new ConstantInteger(tag, di.readInt());
                break;
            case ConstantConstant.CONSTANT_FLOAT:
                constants[i] = new ConstantFloat(tag, di.readFloat());
                break;
            case ConstantConstant.CONSTANT_LONG:
                constants[i++] = new ConstantLong(tag, di.readLong());
                break;
            case ConstantConstant.CONSTANT_DOUBLE:
                constants[i++] = new ConstantDouble(tag, di.readDouble());
                break;
            case ConstantConstant.CONSTANT_NAMEANDTYPE:
                constants[i] = new ConstantNameAndType(tag,
                                                       di.readUnsignedShort(),
                                                       di.readUnsignedShort());
                break;
            case ConstantConstant.CONSTANT_UTF8:
                constants[i] = new ConstantUtf8(tag, di.readUTF());
                break;
            default:
                throw new ClassFormatException("Invalid constant pool entry");
            }
        }

        return constants;
    }

    private static int[] DeserializeInterfaces(DataInput di)
        throws IOException
    {
        int count = di.readUnsignedShort();
        if (count == 0)
            return null;

        int[] interfaces = new int[count];

        for (int i=0; i<count; i++)
            interfaces[i] = di.readUnsignedShort();

        return interfaces;
    }

    private static Field[] DeserializeFields(
            DataInput di, ConstantPool constantPool)
        throws IOException
    {
        int count = di.readUnsignedShort();
        if (count == 0)
            return null;

        Field[] fieldInfos = new Field[count];

        for (int i=0; i<count; i++)
            fieldInfos[i] = new Field(
                        di.readUnsignedShort(),
                        di.readUnsignedShort(),
                        di.readUnsignedShort(),
                        AttributeDeserializer.Deserialize(di, constantPool));

        return fieldInfos;
    }

    private static Method[] DeserializeMethods(DataInput di,
            ConstantPool constants)
        throws IOException
    {
        int count = di.readUnsignedShort();
        if (count == 0)
            return null;

        Method[] methodInfos = new Method[count];

        for (int i=0; i<count; i++)
            methodInfos[i] = new Method(
                        di.readUnsignedShort(),
                        di.readUnsignedShort(),
                        di.readUnsignedShort(),
                        AttributeDeserializer.Deserialize(di, constants));

        return methodInfos;
    }

    private static void CheckMagic(DataInput di)
        throws IOException
    {
        int magic = di.readInt();

        if(magic != CoreConstants.JAVA_MAGIC_NUMBER)
          throw new ClassFormatException("Invalid Java .class file");
    }
}
