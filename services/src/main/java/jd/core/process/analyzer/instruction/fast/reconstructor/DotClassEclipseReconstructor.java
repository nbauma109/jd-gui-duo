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
package jd.core.process.analyzer.instruction.fast.reconstructor;

import org.apache.bcel.Const;
import org.apache.bcel.classfile.Constant;
import org.apache.bcel.classfile.ConstantFieldref;
import org.apache.bcel.classfile.ConstantNameAndType;
import org.apache.bcel.classfile.ConstantString;
import org.jd.core.v1.model.classfile.constant.ConstantMethodref;
import org.jd.core.v1.util.StringConstants;

import java.util.List;

import jd.core.model.classfile.ClassFile;
import jd.core.model.classfile.ConstantPool;
import jd.core.model.classfile.Field;
import jd.core.model.instruction.bytecode.ByteCodeConstants;
import jd.core.model.instruction.bytecode.instruction.*;
import jd.core.model.instruction.fast.FastConstants;
import jd.core.model.instruction.fast.instruction.FastTry;
import jd.core.model.instruction.fast.instruction.FastTry.FastCatch;
import jd.core.model.reference.ReferenceMap;
import jd.core.process.analyzer.classfile.visitor.ReplaceDupLoadVisitor;

/*
 * Recontruction du mot cle '.class' depuis les instructions generees par le
 * compilateur d'Eclipse :
 * ...
 * ifnotnull( getstatic( current class, 'class$...', Class ) )
 *  try
 *  {
 *   dupstore( invokestatic( 'Class', 'forName', nom de la classe ) )
 *   putstatic( current class, 'class$...', Class, dupload )
 *  }
 *  catch (Ljava/lang/ClassNotFoundException;)
 *  {
 *    athrow ...
 *  }
 * ???( getstatic( class, 'class$...' ) )
 * ...
 */
public class DotClassEclipseReconstructor
{
    private DotClassEclipseReconstructor() {
        super();
    }

    public static void reconstruct(
        ReferenceMap referenceMap, ClassFile classFile, List<Instruction> list)
    {
        int i = list.size();

        if  (i < 3)
            return;

        i -= 2;
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

            if (instruction.opcode != FastConstants.TRY)
                continue;

            FastTry ft = (FastTry)instruction;

            if ((ft.catches.size() != 1) || (ft.finallyInstructions != null) ||
                (ft.instructions.size() != 2))
                continue;

            FastCatch fc = ft.catches.get(0);

            if ((fc.instructions.size() != 1) ||
                (fc.otherExceptionTypeIndexes != null))
                    continue;

            instruction = list.get(i+2);

            if ((ft.offset >= jumpOffset) || (jumpOffset > instruction.offset))
                continue;

            GetStatic gs = (GetStatic)ii.value;

            ConstantFieldref cfr = constants.getConstantFieldref(gs.index);

            if (cfr.getClassIndex() != classFile.getThisClassIndex())
                continue;

            ConstantNameAndType cnatField = constants.getConstantNameAndType(
                    cfr.getNameAndTypeIndex());

            String signature =
                constants.getConstantUtf8(cnatField.getSignatureIndex());

            if (! StringConstants.INTERNAL_CLASS_SIGNATURE.equals(signature))
                continue;

            String name = constants.getConstantUtf8(cnatField.getNameIndex());

            if (! name.startsWith(StringConstants.CLASS_DOLLAR))
                continue;

            instruction = ft.instructions.get(0);

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

            ConstantMethodref cmr = constants.getConstantMethodref(is.index);

            name = constants.getConstantClassName(cmr.getClassIndex());

            if (! name.equals(StringConstants.JAVA_LANG_CLASS))
                continue;

            ConstantNameAndType cnatMethod =
                constants.getConstantNameAndType(cmr.getNameAndTypeIndex());
            name = constants.getConstantUtf8(cnatMethod.getNameIndex());

            if (! name.equals(StringConstants.FORNAME_METHOD_NAME))
                continue;

            Ldc ldc = (Ldc)instruction;
            Constant cv = constants.getConstantValue(ldc.index);

            if (!(cv instanceof ConstantString))
                continue;

            instruction = ft.instructions.get(1);

            if (instruction.opcode != Const.PUTSTATIC)
                continue;

            PutStatic ps = (PutStatic)instruction;

            if ((ps.index != gs.index) ||
                (ps.valueref.opcode != ByteCodeConstants.DUPLOAD) ||
                (ps.valueref.offset != ds.offset))
                continue;

            String exceptionName =
                constants.getConstantClassName(fc.exceptionTypeIndex);

            if (! exceptionName.equals(StringConstants.INTERNAL_CLASSNOTFOUNDEXCEPTION_SIGNATURE))
                continue;

            if (fc.instructions.get(0).opcode != Const.ATHROW)
                continue;

            // Trouve !
            ConstantString cs = (ConstantString)cv;
            String className = constants.getConstantUtf8(cs.getStringIndex());
            String internalName = className.replace(
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
            ReplaceDupLoadVisitor visitor = new ReplaceDupLoadVisitor(ds, ldc);

            visitor.visit(list.get(i+2));

            // Retrait de l'intruction FastTry
            list.remove(i+1);
            // Retrait de l'intruction IfNotNull
            list.remove(i);

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
        }
    }
}
