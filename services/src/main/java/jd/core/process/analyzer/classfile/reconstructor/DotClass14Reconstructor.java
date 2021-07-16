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
package jd.core.process.analyzer.classfile.reconstructor;

import org.apache.bcel.Const;
import org.apache.bcel.classfile.Constant;
import org.apache.bcel.classfile.ConstantFieldref;
import org.apache.bcel.classfile.ConstantNameAndType;
import org.apache.bcel.classfile.ConstantString;
import org.jd.core.v1.model.classfile.constant.ConstantMethodref;
import org.jd.core.v1.util.StringConstants;

import java.util.ArrayList;
import java.util.List;

import jd.core.model.classfile.ClassFile;
import jd.core.model.classfile.ConstantPool;
import jd.core.model.classfile.Field;
import jd.core.model.classfile.Method;
import jd.core.model.instruction.bytecode.ByteCodeConstants;
import jd.core.model.instruction.bytecode.instruction.*;
import jd.core.model.reference.ReferenceMap;
import jd.core.process.analyzer.classfile.visitor.ReplaceGetStaticVisitor;
import jd.core.util.SignatureUtil;

/*
 * Recontruction du mot cle '.class' depuis les instructions generees par le
 * JDK 1.4 de SUN :
 * ...
 * ifnotnull( getstatic( current or outer class, 'class$...', Class ) )
 *  dupstore( invokestatic( current or outer class, 'class$', nom de la classe ) )
 *  putstatic( current class, 'class$...', Class, dupload )
 *  ternaryOpStore( dupload )
 *  goto
 * ???( getstatic( class, 'class$...' ) )
 * ...
 */
public class DotClass14Reconstructor
{
    private DotClass14Reconstructor() {
        super();
    }

    public static void reconstruct(
        ReferenceMap referenceMap, ClassFile classFile, List<Instruction> list)
    {
        int i = list.size();

        if  (i < 6)
            return;

        i -= 5;
        ConstantPool constants = classFile.getConstantPool();

        while (i-- > 0)
        {
            Instruction instruction = list.get(i);

            if (instruction.opcode != ByteCodeConstants.IFXNULL)
                continue;

            IfInstruction ii = (IfInstruction)instruction;

            if (ii.value.opcode != Const.GETSTATIC)
                continue;

            int jumpOffset = ii.getJumpOffset();

            instruction = list.get(i+1);

            if (instruction.opcode != ByteCodeConstants.DUPSTORE)
                continue;

            DupStore ds = (DupStore)instruction;

            if (ds.objectref.opcode != Const.INVOKESTATIC)
                continue;

            Invokestatic is = (Invokestatic)ds.objectref;

            if (is.args.size() != 1)
                continue;

            instruction = is.args.get(0);

            if (instruction.opcode != Const.LDC)
                continue;

            instruction = list.get(i+2);

            if (instruction.opcode != Const.PUTSTATIC)
                continue;

            PutStatic ps = (PutStatic)instruction;

            if ((ps.valueref.opcode != ByteCodeConstants.DUPLOAD) ||
                (ds.offset != ps.valueref.offset))
                continue;

            instruction = list.get(i+3);

            if (instruction.opcode != ByteCodeConstants.TERNARYOPSTORE)
                continue;

            TernaryOpStore tos = (TernaryOpStore)instruction;

            if ((tos.objectref.opcode != ByteCodeConstants.DUPLOAD) ||
                (ds.offset != tos.objectref.offset))
                continue;

            instruction = list.get(i+4);

            if (instruction.opcode != Const.GOTO)
                continue;

            Goto g = (Goto)instruction;
            instruction = list.get(i+5);

            if ((g.offset >= jumpOffset) || (jumpOffset > instruction.offset))
                continue;

            GetStatic gs = (GetStatic)ii.value;

            if (ps.index != gs.index)
                continue;

            ConstantFieldref cfr = constants.getConstantFieldref(gs.index);

            if (searchMatchingClassFile(cfr.getClassIndex(), classFile) == null)
                continue;

            ConstantNameAndType cnatField = constants.getConstantNameAndType(
                    cfr.getNameAndTypeIndex());

            String descriptorField =
                constants.getConstantUtf8(cnatField.getSignatureIndex());

            if (! descriptorField.equals(StringConstants.INTERNAL_CLASS_SIGNATURE))
                continue;

            String nameField = constants.getConstantUtf8(cnatField.getNameIndex());

            if (!nameField.startsWith(StringConstants.CLASS_DOLLAR) &&
                !nameField.startsWith(StringConstants.ARRAY_DOLLAR))
                continue;

            ConstantMethodref cmr = constants.getConstantMethodref(is.index);

            ClassFile matchingClassFile =
                searchMatchingClassFile(cmr.getClassIndex(), classFile);
            if (matchingClassFile == null)
                continue;

            ConstantNameAndType cnatMethod =
                constants.getConstantNameAndType(cmr.getNameAndTypeIndex());
            String nameMethod =
                constants.getConstantUtf8(cnatMethod.getNameIndex());

            if (! nameMethod.equals(StringConstants.CLASS_DOLLAR))
                continue;

            Ldc ldc = (Ldc)is.args.get(0);
            Constant cv = constants.getConstantValue(ldc.index);

            if (!(cv instanceof ConstantString))
                continue;

            // Trouve !
            ConstantString cs = (ConstantString)cv;
            String signature = constants.getConstantUtf8(cs.getStringIndex());

            if (SignatureUtil.getArrayDimensionCount(signature) == 0)
            {
                String internalName = signature.replace(
                    StringConstants.PACKAGE_SEPARATOR,
                    StringConstants.INTERNAL_PACKAGE_SEPARATOR);

                referenceMap.add(internalName);

                // Ajout du nom interne
                int index = constants.addConstantUtf8(internalName);
                // Ajout d'une nouvelle classe
                index = constants.addConstantClass(index);
                ldc = new Ldc(
                    Const.LDC, ii.offset,
                    ii.lineNumber, index);

                // Remplacement de l'intruction GetStatic par l'instruction Ldc
                ReplaceGetStaticVisitor visitor =
                    new ReplaceGetStaticVisitor(gs.index, ldc);

                visitor.visit(instruction);
            }
            else
            {
                IConst iconst0 = new IConst(
                    ByteCodeConstants.ICONST, ii.offset,
                    ii.lineNumber, 0);
                Instruction newArray;

                String signatureWithoutDimension =
                    SignatureUtil.cutArrayDimensionPrefix(signature);

                if (SignatureUtil.isObjectSignature(signatureWithoutDimension))
                {
                    //  8: iconst_0
                    //  9: anewarray 62	java/lang/String
                    //  12: invokevirtual 64	java/lang/Object:getClass	()Ljava/lang/Class;
                    String tmp = signatureWithoutDimension.replace(
                        StringConstants.PACKAGE_SEPARATOR,
                        StringConstants.INTERNAL_PACKAGE_SEPARATOR);
                    String internalName = tmp.substring(1, tmp.length()-1);

                    // Ajout du nom de la classe pour generer la liste des imports
                    referenceMap.add(internalName);
                    // Ajout du nom interne
                    int index = constants.addConstantUtf8(internalName);
                    // Ajout d'une nouvelle classe
                    index = constants.addConstantClass(index);

                    newArray = new ANewArray(
                        Const.ANEWARRAY, ii.offset,
                        ii.lineNumber, index, iconst0);
                }
                else
                {
                    //  8: iconst_0
                    //  9: newarray byte
                    //  11: invokevirtual 62	java/lang/Object:getClass	()Ljava/lang/Class;
                    newArray = new NewArray(
                        Const.NEWARRAY, ii.offset, ii.lineNumber,
                        SignatureUtil.getTypeFromSignature(signatureWithoutDimension),
                        iconst0);
                }

                // Ajout de la méthode 'getClass'
                int methodNameIndex = constants.addConstantUtf8("getClass");
                int methodDescriptorIndex =
                    constants.addConstantUtf8("()Ljava/lang/Class;");
                int nameAndTypeIndex = constants.addConstantNameAndType(
                    methodNameIndex, methodDescriptorIndex);
                int cmrIndex = constants.addConstantMethodref(
                    constants.objectClassIndex, nameAndTypeIndex);

                Invokevirtual iv = new Invokevirtual(
                    Const.INVOKEVIRTUAL, ii.offset,
                    ii.lineNumber, cmrIndex, newArray,
                    new ArrayList<>(0));

                // Remplacement de l'intruction GetStatic
                ReplaceGetStaticVisitor visitor =
                    new ReplaceGetStaticVisitor(gs.index, iv);

                visitor.visit(instruction);
            }

            // Retrait de l'intruction Goto
            list.remove(i+4);
            // Retrait de l'intruction TernaryOpStore
            list.remove(i+3);
            // Retrait de l'intruction PutStatic
            list.remove(i+2);
            // Retrait de l'intruction DupStore
            list.remove(i+1);
            // Retrait de l'intruction IfNotNull
            list.remove(i);

            if (matchingClassFile == classFile)
            {
                // Recherche de l'attribut statique et ajout de l'attribut SYNTHETIC
                Field[] fields = classFile.getFields();
                int j = fields.length;

                while (j-- > 0)
                {
                    Field field = fields[j];

                    if (field.getNameIndex() == cnatField.getNameIndex())
                    {
                        field.accessFlags |= Const.ACC_SYNTHETIC;
                        break;
                    }
                }

                // Recherche de la méthode statique et ajout de l'attribut SYNTHETIC
                Method[] methods = classFile.getMethods();
                j = methods.length;

                while (j-- > 0)
                {
                    Method method = methods[j];

                    if (method.getNameIndex() == cnatMethod.getNameIndex())
                    {
                        method.accessFlags |= Const.ACC_SYNTHETIC;
                        break;
                    }
                }
            }
            else
            {
                // Recherche de l'attribut statique et ajout de l'attribut SYNTHETIC
                ConstantPool matchingConstants =
                    matchingClassFile.getConstantPool();
                Field[] fields = matchingClassFile.getFields();
                int j = fields.length;

                while (j-- > 0)
                {
                    Field field = fields[j];

                    if (nameField.equals(
                            matchingConstants.getConstantUtf8(field.getNameIndex())))
                    {
                        field.accessFlags |= Const.ACC_SYNTHETIC;
                        break;
                    }
                }

                // Recherche de la méthode statique et ajout de l'attribut SYNTHETIC
                Method[] methods = matchingClassFile.getMethods();
                j = methods.length;

                while (j-- > 0)
                {
                    Method method = methods[j];

                    if (nameMethod.equals(
                            matchingConstants.getConstantUtf8(method.getNameIndex())))
                    {
                        method.accessFlags |= Const.ACC_SYNTHETIC;
                        break;
                    }
                }
            }
        }
    }

    private static ClassFile searchMatchingClassFile(
        int classIndex, ClassFile classFile)
    {
        if (classIndex == classFile.getThisClassIndex())
            return classFile;

        String className =
            classFile.getConstantPool().getConstantClassName(classIndex);

        for (;;)
        {
            classFile = classFile.getOuterClass();

            if (classFile == null)
                return null;

            if (classFile.getThisClassName().equals(className))
                return classFile;
        }
    }
}
