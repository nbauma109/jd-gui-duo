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
package jd.core.process.layouter.visitor;

import org.apache.bcel.Const;
import org.jd.core.v1.model.classfile.constant.ConstantMethodref;
import org.jd.core.v1.util.StringConstants;

import java.util.List;

import jd.core.model.classfile.ClassFile;
import jd.core.model.classfile.ConstantPool;
import jd.core.model.instruction.bytecode.ByteCodeConstants;
import jd.core.model.instruction.bytecode.instruction.*;
import jd.core.model.instruction.fast.FastConstants;
import jd.core.model.instruction.fast.instruction.FastDeclaration;

public abstract class BaseInstructionSplitterVisitor
{
    protected ClassFile classFile;
    protected ConstantPool constants;

    protected BaseInstructionSplitterVisitor() {}

    public void start(ClassFile classFile)
    {
        this.classFile = classFile;
        this.constants = (classFile == null) ? null : classFile.getConstantPool();
    }

    public void visit(Instruction instruction)
    {
        visit(null, instruction);
    }

    protected void visit(Instruction parent, Instruction instruction)
    {
        switch (instruction.opcode)
        {
        case Const.ARRAYLENGTH:
            visit(instruction, ((ArrayLength)instruction).arrayref);
            break;
        case ByteCodeConstants.ARRAYLOAD:
            {
                ArrayLoadInstruction ali = (ArrayLoadInstruction)instruction;
                visit(instruction, ali.arrayref);
                visit(instruction, ali.indexref);
            }
            break;
        case Const.AASTORE:
        case ByteCodeConstants.ARRAYSTORE:
            {
                ArrayStoreInstruction asi = (ArrayStoreInstruction)instruction;
                visit(instruction, asi.arrayref);
                visit(instruction, asi.indexref);
                visit(instruction, asi.valueref);
            }
            break;
        case Const.ANEWARRAY:
            visit(instruction, ((ANewArray)instruction).dimension);
            break;
        case ByteCodeConstants.ASSERT:
            {
                AssertInstruction ai = (AssertInstruction)instruction;
                visit(instruction, ai.test);
                if (ai.msg != null)
                    visit(instruction, ai.msg);
            }
            break;
        case ByteCodeConstants.ASSIGNMENT:
        case ByteCodeConstants.BINARYOP:
            {
                BinaryOperatorInstruction boi =
                    (BinaryOperatorInstruction)instruction;
                visit(instruction, boi.value1);
                visit(instruction, boi.value2);
            }
            break;
        case Const.ATHROW:
            visit(instruction, ((AThrow)instruction).value);
            break;
        case ByteCodeConstants.UNARYOP:
            visit(instruction, ((UnaryOperatorInstruction)instruction).value);
            break;
        case ByteCodeConstants.CONVERT:
        case ByteCodeConstants.IMPLICITCONVERT:
            visit(instruction, ((ConvertInstruction)instruction).value);
            break;
        case Const.CHECKCAST:
            visit(instruction, ((CheckCast)instruction).objectref);
            break;
        case FastConstants.DECLARE:
            {
                FastDeclaration fd = (FastDeclaration)instruction;
                if (fd.instruction != null)
                    visit(instruction, fd.instruction);
            }
            break;
        case Const.GETFIELD:
            visit(instruction, ((GetField)instruction).objectref);
            break;
        case ByteCodeConstants.IF:
        case ByteCodeConstants.IFXNULL:
            visit(instruction, ((IfInstruction)instruction).value);
            break;
        case ByteCodeConstants.IFCMP:
            {
                IfCmp ic = (IfCmp)instruction;
                visit(instruction, ic.value1);
                visit(instruction, ic.value2);
            }
            break;
        case ByteCodeConstants.COMPLEXIF:
            {
                List<Instruction> branchList =
                    ((ComplexConditionalBranchInstruction)instruction).instructions;
                int length = branchList.size();
                for (int i=0; i<length; i++)
                    visit(instruction, branchList.get(i));
            }
            break;
        case ByteCodeConstants.PREINC:
        case ByteCodeConstants.POSTINC:
            visit(instruction, ((IncInstruction)instruction).value);
            break;
        case ByteCodeConstants.INVOKENEW:
            {
                InvokeNew in = (InvokeNew)instruction;
                List<Instruction> args = in.args;
                int length = args.size();
                for (int i=0; i<length; i++)
                    visit(instruction, args.get(i));

                ConstantMethodref cmr =
                    this.constants.getConstantMethodref(in.index);
                String internalClassName =
                    this.constants.getConstantClassName(cmr.getClassIndex());
                String prefix =
                    this.classFile.getThisClassName() +
                    StringConstants.INTERNAL_INNER_SEPARATOR;

                if (internalClassName.startsWith(prefix))
                {
                    ClassFile innerClassFile =
                        this.classFile.getInnerClassFile(internalClassName);

                    if ((innerClassFile != null) &&
                        (innerClassFile.getInternalAnonymousClassName() != null))
                    {
                        // Anonymous new invoke
                        visitAnonymousNewInvoke(
                            (parent==null) ? in : parent, in, innerClassFile);
                    }
                    //else
                    //{
                        // Inner class new invoke
                    //}
                }
                //else
                //{
                    // Normal new invoke
                //}
            }
            break;
        case Const.INSTANCEOF:
            visit(instruction, ((InstanceOf)instruction).objectref);
            break;
        case Const.INVOKEINTERFACE:
        case Const.INVOKEVIRTUAL:
        case Const.INVOKESPECIAL:
            visit(instruction, ((InvokeNoStaticInstruction)instruction).objectref);
            // intended fall through
        case Const.INVOKESTATIC:
            {
                List<Instruction> args = ((InvokeInstruction)instruction).args;
                int length = args.size();
                for (int i=0; i<length; i++)
                    visit(instruction, args.get(i));
            }
            break;
        case Const.LOOKUPSWITCH:
        case Const.TABLESWITCH:
            visit(instruction, ((Switch)instruction).key);
            break;
        case Const.MULTIANEWARRAY:
            {
                Instruction[] dimensions =
                    ((MultiANewArray)instruction).dimensions;
                int length = dimensions.length;
                for (int i=0; i<length; i++)
                    visit(instruction, dimensions[i]);
            }
            break;
        case Const.NEWARRAY:
            visit(instruction, ((NewArray)instruction).dimension);
            break;
        case Const.POP:
            visit(instruction, ((Pop)instruction).objectref);
            break;
        case Const.PUTFIELD:
            {
                PutField putField = (PutField)instruction;
                visit(instruction, putField.objectref);
                visit(instruction, putField.valueref);
            }
            break;
        case Const.PUTSTATIC:
            visit(instruction, ((PutStatic)instruction).valueref);
            break;
        case ByteCodeConstants.XRETURN:
            visit(instruction, ((ReturnInstruction)instruction).valueref);
            break;
        case ByteCodeConstants.STORE:
        case Const.ASTORE:
        case Const.ISTORE:
            visit(instruction, ((StoreInstruction)instruction).valueref);
            break;
        case ByteCodeConstants.TERNARYOPSTORE:
            visit(instruction, ((TernaryOpStore)instruction).objectref);
            break;
        case ByteCodeConstants.TERNARYOP:
            {
                TernaryOperator tp = (TernaryOperator)instruction;
                visit(instruction, tp.test);
                visit(instruction, tp.value1);
                visit(instruction, tp.value2);
            }
            break;
        case ByteCodeConstants.INITARRAY:
        case ByteCodeConstants.NEWANDINITARRAY:
            {
                InitArrayInstruction iai = (InitArrayInstruction)instruction;
                visit(instruction, iai.newArray);
                List<Instruction> values = iai.values;
                int length = values.size();
                for (int i=0; i<length; i++)
                    visit(instruction, values.get(i));
            }
            break;
        }
    }

    public abstract void visitAnonymousNewInvoke(
        Instruction parent, InvokeNew in, ClassFile innerClassFile);
}
