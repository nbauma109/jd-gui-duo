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
public class SearchDupLoadInstructionVisitor
{
    private SearchDupLoadInstructionVisitor() {
        super();
    }

    public static DupLoad visit(Instruction instruction, DupStore dupStore)
    {
        switch (instruction.getOpcode())
        {
        case Const.ARRAYLENGTH:
            return visit(((ArrayLength)instruction).getArrayref(), dupStore);
        case ByteCodeConstants.ARRAYLOAD:
            {
                ArrayLoadInstruction ali = (ArrayLoadInstruction)instruction;
                DupLoad dupLoad = visit(ali.getArrayref(), dupStore);
                if (dupLoad != null)
                    return dupLoad;
                return visit(ali.getIndexref(), dupStore);
            }
        case Const.AASTORE:
        case ByteCodeConstants.ARRAYSTORE:
            {
                ArrayStoreInstruction asi = (ArrayStoreInstruction)instruction;
                DupLoad dupLoad = visit(asi.getArrayref(), dupStore);
                if (dupLoad != null)
                    return dupLoad;
                dupLoad = visit(asi.getIndexref(), dupStore);
                if (dupLoad != null)
                    return dupLoad;
                return visit(asi.getValueref(), dupStore);
            }
        case ByteCodeConstants.ASSERT:
            {
                AssertInstruction ai = (AssertInstruction)instruction;
                DupLoad dupLoad = visit(ai.getTest(), dupStore);
                if (dupLoad != null)
                    return dupLoad;
                if (ai.getMsg() == null)
                    return null;
                return visit(ai.getMsg(), dupStore);
            }
        case Const.ATHROW:
            return visit(((AThrow)instruction).getValue(), dupStore);
        case ByteCodeConstants.UNARYOP:
            return visit(((UnaryOperatorInstruction)instruction).getValue(), dupStore);
        case ByteCodeConstants.BINARYOP:
        case ByteCodeConstants.ASSIGNMENT:
            {
                BinaryOperatorInstruction boi =
                    (BinaryOperatorInstruction)instruction;
                DupLoad dupLoad = visit(boi.getValue1(), dupStore);
                if (dupLoad != null)
                    return dupLoad;
                return visit(boi.getValue2(), dupStore);
            }
        case Const.CHECKCAST:
            return visit(((CheckCast)instruction).getObjectref(), dupStore);
        case ByteCodeConstants.STORE:
        case Const.ASTORE:
        case Const.ISTORE:
            return visit(((StoreInstruction)instruction).getValueref(), dupStore);
        case ByteCodeConstants.DUPLOAD:
            if (((DupLoad)instruction).getDupStore() == dupStore)
                return (DupLoad)instruction;
            break;
        case ByteCodeConstants.DUPSTORE:
            return visit(((DupStore)instruction).getObjectref(), dupStore);
        case ByteCodeConstants.CONVERT:
        case ByteCodeConstants.IMPLICITCONVERT:
            return visit(((ConvertInstruction)instruction).getValue(), dupStore);
        case ByteCodeConstants.IFCMP:
            {
                IfCmp ifCmp = (IfCmp)instruction;
                DupLoad dupLoad = visit(ifCmp.getValue1(), dupStore);
                if (dupLoad != null)
                    return dupLoad;
                return visit(ifCmp.getValue2(), dupStore);
            }
        case ByteCodeConstants.IF:
        case ByteCodeConstants.IFXNULL:
            return visit(((IfInstruction)instruction).getValue(), dupStore);
        case ByteCodeConstants.COMPLEXIF:
            {
                List<Instruction> branchList =
                    ((ComplexConditionalBranchInstruction)instruction).getInstructions();
                for (int i=branchList.size()-1; i>=0; --i)
                {
                    DupLoad dupLoad = visit(branchList.get(i), dupStore);
                    if (dupLoad != null)
                        return dupLoad;
                }
            }
            break;
        case Const.INSTANCEOF:
            return visit(((InstanceOf)instruction).getObjectref(), dupStore);
        case Const.INVOKEINTERFACE:
        case Const.INVOKESPECIAL:
        case Const.INVOKEVIRTUAL:
            {
                DupLoad dupLoad = visit(
                    ((InvokeNoStaticInstruction)instruction).getObjectref(), dupStore);
                if (dupLoad != null)
                    return dupLoad;
            }
            // intended fall through
        case Const.INVOKESTATIC:
        case ByteCodeConstants.INVOKENEW:
            {
                List<Instruction> list = ((InvokeInstruction)instruction).getArgs();
                for (int i=list.size()-1; i>=0; --i)
                {
                    DupLoad dupLoad = visit(list.get(i), dupStore);
                    if (dupLoad != null)
                        return dupLoad;
                }
            }
            break;
        case Const.LOOKUPSWITCH:
            return visit(((LookupSwitch)instruction).getKey(), dupStore);
        case Const.MONITORENTER:
            return visit(((MonitorEnter)instruction).getObjectref(), dupStore);
        case Const.MONITOREXIT:
            return visit(((MonitorExit)instruction).getObjectref(), dupStore);
        case Const.MULTIANEWARRAY:
            {
                Instruction[] dimensions = ((MultiANewArray)instruction).getDimensions();
                for (int i=dimensions.length-1; i>=0; --i)
                {
                    DupLoad dupLoad = visit(dimensions[i], dupStore);
                    if (dupLoad != null)
                        return dupLoad;
                }
            }
            break;
        case Const.NEWARRAY:
            return visit(((NewArray)instruction).getDimension(), dupStore);
        case Const.ANEWARRAY:
            return visit(((ANewArray)instruction).getDimension(), dupStore);
        case Const.POP:
            return visit(((Pop)instruction).getObjectref(), dupStore);
        case Const.PUTFIELD:
            {
                PutField putField = (PutField)instruction;
                DupLoad dupLoad = visit(putField.getObjectref(), dupStore);
                if (dupLoad != null)
                    return dupLoad;
                return visit(putField.getValueref(), dupStore);
            }
        case Const.PUTSTATIC:
            return visit(((PutStatic)instruction).getValueref(), dupStore);
        case ByteCodeConstants.XRETURN:
            return visit(((ReturnInstruction)instruction).getValueref(), dupStore);
        case Const.TABLESWITCH:
            return visit(((TableSwitch)instruction).getKey(), dupStore);
        case ByteCodeConstants.TERNARYOPSTORE:
            return visit(((TernaryOpStore)instruction).getObjectref(), dupStore);
        case ByteCodeConstants.PREINC:
        case ByteCodeConstants.POSTINC:
            return visit(((IncInstruction)instruction).getValue(), dupStore);
        case Const.GETFIELD:
            return visit(((GetField)instruction).getObjectref(), dupStore);
        case ByteCodeConstants.INITARRAY:
        case ByteCodeConstants.NEWANDINITARRAY:
            {
                InitArrayInstruction iai = (InitArrayInstruction)instruction;
                DupLoad dupLoad = visit(iai.getNewArray(), dupStore);
                if (dupLoad != null)
                    return dupLoad;
                if (iai.getValues() != null)
                    return visit(iai.getValues(), dupStore);
            }
            break;
        case Const.ACONST_NULL:
        case ByteCodeConstants.LOAD:
        case Const.ALOAD:
        case Const.ILOAD:
        case Const.BIPUSH:
        case ByteCodeConstants.ICONST:
        case ByteCodeConstants.LCONST:
        case ByteCodeConstants.FCONST:
        case ByteCodeConstants.DCONST:
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
                    "Can not search DupLoad instruction in " +
                    instruction.getClass().getName() +
                    ", opcode=" + instruction.getOpcode());
        }

        return null;
    }

    private static DupLoad visit(
        List<Instruction> instructions, DupStore dupStore)
    {
        for (int i=instructions.size()-1; i>=0; --i)
        {
            DupLoad dupLoad = visit(instructions.get(i), dupStore);
            if (dupLoad != null)
                return dupLoad;
        }

        return null;
    }
}
