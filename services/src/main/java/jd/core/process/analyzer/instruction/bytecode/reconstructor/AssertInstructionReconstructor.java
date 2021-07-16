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
package jd.core.process.analyzer.instruction.bytecode.reconstructor;

import org.apache.bcel.Const;
import org.apache.bcel.classfile.ConstantFieldref;
import org.apache.bcel.classfile.ConstantNameAndType;
import org.jd.core.v1.model.classfile.constant.ConstantMethodref;
import org.jd.core.v1.util.StringConstants;

import java.util.List;

import jd.core.model.classfile.ClassFile;
import jd.core.model.classfile.ConstantPool;
import jd.core.model.instruction.bytecode.ByteCodeConstants;
import jd.core.model.instruction.bytecode.instruction.*;

/*
 * Recontruction des des instructions 'assert' depuis le motif :
 * ...
 * complexif( (!($assertionsDisabled)) && (test) )
 *  athrow( newinvoke( classindex="AssertionError", args=["msg"] ));
 * ...
 */
public class AssertInstructionReconstructor
{
    private AssertInstructionReconstructor() {
        super();
    }

    public static void reconstruct(ClassFile classFile, List<Instruction> list)
    {
        int index = list.size();
        if (index-- == 0)
            return;

        while (index-- > 1)
        {
            Instruction instruction = list.get(index);

            if (instruction.opcode != Const.ATHROW)
                continue;

            // AThrow trouve
            AThrow athrow = (AThrow)instruction;
            if (athrow.value.opcode != ByteCodeConstants.INVOKENEW)
                continue;

            instruction = list.get(index-1);
            if (instruction.opcode != ByteCodeConstants.COMPLEXIF)
                continue;

            // ComplexConditionalBranchInstruction trouve
            ComplexConditionalBranchInstruction cbl =
                (ComplexConditionalBranchInstruction)instruction;
            int jumpOffset = cbl.getJumpOffset();
            int lastOffset = list.get(index+1).offset;

            if ((athrow.offset >= jumpOffset) || (jumpOffset > lastOffset))
                continue;

            if ((cbl.cmp != 2) || (cbl.instructions.isEmpty()))
                continue;

            instruction = cbl.instructions.get(0);
            if (instruction.opcode != ByteCodeConstants.IF)
                continue;

            IfInstruction if1 = (IfInstruction)instruction;
            if ((if1.cmp != 7) || (if1.value.opcode != Const.GETSTATIC))
                continue;

            GetStatic gs = (GetStatic)if1.value;
            ConstantPool constants = classFile.getConstantPool();
            ConstantFieldref cfr = constants.getConstantFieldref(gs.index);

            if (cfr.getClassIndex() != classFile.getThisClassIndex())
                continue;

            ConstantNameAndType cnat =
                constants.getConstantNameAndType(cfr.getNameAndTypeIndex());
            String fieldName = constants.getConstantUtf8(cnat.getNameIndex());

            if (! fieldName.equals("$assertionsDisabled"))
                continue;

            InvokeNew in = (InvokeNew)athrow.value;
            ConstantMethodref cmr =
                constants.getConstantMethodref(in.index);
            String className = constants.getConstantClassName(cmr.getClassIndex());

            if (! className.equals(StringConstants.JAVA_LANG_ASSERTION_ERROR))
                continue;

            // Remove first condition "!($assertionsDisabled)"
            cbl.instructions.remove(0);

            Instruction msg = (in.args.isEmpty()) ? null : in.args.get(0);
            list.remove(index--);

            list.set(index, new AssertInstruction(
                ByteCodeConstants.ASSERT, athrow.offset,
                cbl.lineNumber, cbl, msg));
        }
    }
}
