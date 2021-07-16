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
package jd.core.process.analyzer.classfile.visitor;

import org.apache.bcel.Const;

import java.util.List;

import jd.core.model.instruction.bytecode.ByteCodeConstants;
import jd.core.model.instruction.bytecode.instruction.*;

/*
 * Utilisé par TernaryOpReconstructor
 */
public class SearchInstructionByOffsetVisitor
{
    private SearchInstructionByOffsetVisitor() {
        super();
    }

    public static Instruction visit(Instruction instruction, int offset)
    {
        if (instruction.offset == offset)
            return instruction;

        switch (instruction.opcode)
        {
        case Const.ARRAYLENGTH:
            return visit(((ArrayLength)instruction).arrayref, offset);
        case Const.AASTORE:
        case ByteCodeConstants.ARRAYSTORE:
            return visit(((ArrayStoreInstruction)instruction).arrayref, offset);
        case ByteCodeConstants.ASSERT:
            {
                AssertInstruction ai = (AssertInstruction)instruction;
                instruction = visit(ai.test, offset);
                if (instruction != null)
                    return instruction;
                if (ai.msg == null)
                    return null;
                return visit(ai.msg, offset);
            }
        case Const.ATHROW:
            return visit(((AThrow)instruction).value, offset);
        case ByteCodeConstants.UNARYOP:
            return visit(((UnaryOperatorInstruction)instruction).value, offset);
        case ByteCodeConstants.BINARYOP:
        case ByteCodeConstants.ASSIGNMENT:
            {
                BinaryOperatorInstruction boi =
                    (BinaryOperatorInstruction)instruction;
                instruction = visit(boi.value1, offset);
                if (instruction != null)
                    return instruction;
                return visit(boi.value2, offset);
            }
        case Const.CHECKCAST:
            return visit(((CheckCast)instruction).objectref, offset);
        case ByteCodeConstants.STORE:
        case Const.ASTORE:
        case Const.ISTORE:
            return visit(((StoreInstruction)instruction).valueref, offset);
        case ByteCodeConstants.DUPSTORE:
            return visit(((DupStore)instruction).objectref, offset);
        case ByteCodeConstants.CONVERT:
        case ByteCodeConstants.IMPLICITCONVERT:
            return visit(((ConvertInstruction)instruction).value, offset);
        case ByteCodeConstants.IFCMP:
            {
                IfCmp ifCmp = (IfCmp)instruction;
                instruction = visit(ifCmp.value1, offset);
                if (instruction != null)
                    return instruction;
                return visit(ifCmp.value2, offset);
            }
        case ByteCodeConstants.IF:
        case ByteCodeConstants.IFXNULL:
            return visit(((IfInstruction)instruction).value, offset);
        case ByteCodeConstants.COMPLEXIF:
            {
                List<Instruction> branchList =
                    ((ComplexConditionalBranchInstruction)instruction).instructions;
                for (int i=branchList.size()-1; i>=0; --i)
                {
                    instruction = visit(branchList.get(i), offset);
                    if (instruction != null)
                        return instruction;
                }
            }
            break;
        case Const.INSTANCEOF:
            return visit(((InstanceOf)instruction).objectref, offset);
        case Const.INVOKEINTERFACE:
        case Const.INVOKESPECIAL:
        case Const.INVOKEVIRTUAL:
            {
                Instruction result = visit(
                    ((InvokeNoStaticInstruction)instruction).objectref, offset);
                if (result != null)
                    return result;
            }
            // intended fall through
        case Const.INVOKESTATIC:
        case ByteCodeConstants.INVOKENEW:
            {
                List<Instruction> list = ((InvokeInstruction)instruction).args;
                for (int i=list.size()-1; i>=0; --i)
                {
                    instruction = visit(list.get(i), offset);
                    if (instruction != null)
                        return instruction;
                }
            }
            break;
        case Const.LOOKUPSWITCH:
            return visit(((LookupSwitch)instruction).key, offset);
        case Const.MONITORENTER:
            return visit(((MonitorEnter)instruction).objectref, offset);
        case Const.MONITOREXIT:
            return visit(((MonitorExit)instruction).objectref, offset);
        case Const.MULTIANEWARRAY:
            {
                Instruction[] dimensions = ((MultiANewArray)instruction).dimensions;
                for (int i=dimensions.length-1; i>=0; --i)
                {
                    instruction = visit(dimensions[i], offset);
                    if (instruction != null)
                        return instruction;
                }
            }
            break;
        case Const.NEWARRAY:
            return visit(((NewArray)instruction).dimension, offset);
        case Const.ANEWARRAY:
            return visit(((ANewArray)instruction).dimension, offset);
        case Const.POP:
            return visit(((Pop)instruction).objectref, offset);
        case Const.PUTFIELD:
            {
                PutField putField = (PutField)instruction;
                instruction = visit(putField.objectref, offset);
                if (instruction != null)
                    return instruction;
                return visit(putField.valueref, offset);
            }
        case Const.PUTSTATIC:
            return visit(((PutStatic)instruction).valueref, offset);
        case ByteCodeConstants.XRETURN:
            return visit(((ReturnInstruction)instruction).valueref, offset);
        case Const.TABLESWITCH:
            return visit(((TableSwitch)instruction).key, offset);
        case ByteCodeConstants.TERNARYOPSTORE:
            return visit(((TernaryOpStore)instruction).objectref, offset);
        case ByteCodeConstants.PREINC:
        case ByteCodeConstants.POSTINC:
            return visit(((IncInstruction)instruction).value, offset);
        case Const.GETFIELD:
            return visit(((GetField)instruction).objectref, offset);
        case ByteCodeConstants.INITARRAY:
        case ByteCodeConstants.NEWANDINITARRAY:
            {
                InitArrayInstruction iai = (InitArrayInstruction)instruction;
                instruction = visit(iai.newArray, offset);
                if (instruction != null)
                    return instruction;
                if (iai.values != null)
                    return visit(iai.values, offset);
            }
            break;
        case Const.ACONST_NULL:
        case ByteCodeConstants.ARRAYLOAD:
        case ByteCodeConstants.LOAD:
        case Const.ALOAD:
        case Const.ILOAD:
        case Const.BIPUSH:
        case ByteCodeConstants.ICONST:
        case ByteCodeConstants.LCONST:
        case ByteCodeConstants.FCONST:
        case ByteCodeConstants.DCONST:
        case ByteCodeConstants.DUPLOAD:
        case Const.GETSTATIC:
        case ByteCodeConstants.OUTERTHIS:
        case Const.GOTO:
        case Const.IINC:
        case Const.JSR:
        case Const.LDC:
        case Const.LDC2_W:
        case Const.NEW:
        case Const.NOP:
        case Const.SIPUSH:
        case Const.RET:
        case Const.RETURN:
        case ByteCodeConstants.EXCEPTIONLOAD:
        case ByteCodeConstants.RETURNADDRESSLOAD:
            break;
        default:
            System.err.println(
                    "Can not search instruction in " +
                    instruction.getClass().getName() +
                    ", opcode=" + instruction.opcode);
        }

        return null;
    }

    private static Instruction visit(List<Instruction> instructions, int offset)
    {
        for (int i=instructions.size()-1; i>=0; --i)
        {
            Instruction instruction = visit(instructions.get(i), offset);
            if (instruction != null)
                return instruction;
        }

        return null;
    }
}
