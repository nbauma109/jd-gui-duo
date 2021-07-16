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
        switch (instruction.opcode)
        {
        case Const.ARRAYLENGTH:
            return visit(((ArrayLength)instruction).arrayref, dupStore);
        case ByteCodeConstants.ARRAYLOAD:
            {
                ArrayLoadInstruction ali = (ArrayLoadInstruction)instruction;
                DupLoad dupLoad = visit(ali.arrayref, dupStore);
                if (dupLoad != null)
                    return dupLoad;
                return visit(ali.indexref, dupStore);
            }
        case Const.AASTORE:
        case ByteCodeConstants.ARRAYSTORE:
            {
                ArrayStoreInstruction asi = (ArrayStoreInstruction)instruction;
                DupLoad dupLoad = visit(asi.arrayref, dupStore);
                if (dupLoad != null)
                    return dupLoad;
                dupLoad = visit(asi.indexref, dupStore);
                if (dupLoad != null)
                    return dupLoad;
                return visit(asi.valueref, dupStore);
            }
        case ByteCodeConstants.ASSERT:
            {
                AssertInstruction ai = (AssertInstruction)instruction;
                DupLoad dupLoad = visit(ai.test, dupStore);
                if (dupLoad != null)
                    return dupLoad;
                if (ai.msg == null)
                    return null;
                return visit(ai.msg, dupStore);
            }
        case Const.ATHROW:
            return visit(((AThrow)instruction).value, dupStore);
        case ByteCodeConstants.UNARYOP:
            return visit(((UnaryOperatorInstruction)instruction).value, dupStore);
        case ByteCodeConstants.BINARYOP:
        case ByteCodeConstants.ASSIGNMENT:
            {
                BinaryOperatorInstruction boi =
                    (BinaryOperatorInstruction)instruction;
                DupLoad dupLoad = visit(boi.value1, dupStore);
                if (dupLoad != null)
                    return dupLoad;
                return visit(boi.value2, dupStore);
            }
        case Const.CHECKCAST:
            return visit(((CheckCast)instruction).objectref, dupStore);
        case ByteCodeConstants.STORE:
        case Const.ASTORE:
        case Const.ISTORE:
            return visit(((StoreInstruction)instruction).valueref, dupStore);
        case ByteCodeConstants.DUPLOAD:
            if (((DupLoad)instruction).dupStore == dupStore)
                return (DupLoad)instruction;
            break;
        case ByteCodeConstants.DUPSTORE:
            return visit(((DupStore)instruction).objectref, dupStore);
        case ByteCodeConstants.CONVERT:
        case ByteCodeConstants.IMPLICITCONVERT:
            return visit(((ConvertInstruction)instruction).value, dupStore);
        case ByteCodeConstants.IFCMP:
            {
                IfCmp ifCmp = (IfCmp)instruction;
                DupLoad dupLoad = visit(ifCmp.value1, dupStore);
                if (dupLoad != null)
                    return dupLoad;
                return visit(ifCmp.value2, dupStore);
            }
        case ByteCodeConstants.IF:
        case ByteCodeConstants.IFXNULL:
            return visit(((IfInstruction)instruction).value, dupStore);
        case ByteCodeConstants.COMPLEXIF:
            {
                List<Instruction> branchList =
                    ((ComplexConditionalBranchInstruction)instruction).instructions;
                for (int i=branchList.size()-1; i>=0; --i)
                {
                    DupLoad dupLoad = visit(branchList.get(i), dupStore);
                    if (dupLoad != null)
                        return dupLoad;
                }
            }
            break;
        case Const.INSTANCEOF:
            return visit(((InstanceOf)instruction).objectref, dupStore);
        case Const.INVOKEINTERFACE:
        case Const.INVOKESPECIAL:
        case Const.INVOKEVIRTUAL:
            {
                DupLoad dupLoad = visit(
                    ((InvokeNoStaticInstruction)instruction).objectref, dupStore);
                if (dupLoad != null)
                    return dupLoad;
            }
            // intended fall through
        case Const.INVOKESTATIC:
        case ByteCodeConstants.INVOKENEW:
            {
                List<Instruction> list = ((InvokeInstruction)instruction).args;
                for (int i=list.size()-1; i>=0; --i)
                {
                    DupLoad dupLoad = visit(list.get(i), dupStore);
                    if (dupLoad != null)
                        return dupLoad;
                }
            }
            break;
        case Const.LOOKUPSWITCH:
            return visit(((LookupSwitch)instruction).key, dupStore);
        case Const.MONITORENTER:
            return visit(((MonitorEnter)instruction).objectref, dupStore);
        case Const.MONITOREXIT:
            return visit(((MonitorExit)instruction).objectref, dupStore);
        case Const.MULTIANEWARRAY:
            {
                Instruction[] dimensions = ((MultiANewArray)instruction).dimensions;
                for (int i=dimensions.length-1; i>=0; --i)
                {
                    DupLoad dupLoad = visit(dimensions[i], dupStore);
                    if (dupLoad != null)
                        return dupLoad;
                }
            }
            break;
        case Const.NEWARRAY:
            return visit(((NewArray)instruction).dimension, dupStore);
        case Const.ANEWARRAY:
            return visit(((ANewArray)instruction).dimension, dupStore);
        case Const.POP:
            return visit(((Pop)instruction).objectref, dupStore);
        case Const.PUTFIELD:
            {
                PutField putField = (PutField)instruction;
                DupLoad dupLoad = visit(putField.objectref, dupStore);
                if (dupLoad != null)
                    return dupLoad;
                return visit(putField.valueref, dupStore);
            }
        case Const.PUTSTATIC:
            return visit(((PutStatic)instruction).valueref, dupStore);
        case ByteCodeConstants.XRETURN:
            return visit(((ReturnInstruction)instruction).valueref, dupStore);
        case Const.TABLESWITCH:
            return visit(((TableSwitch)instruction).key, dupStore);
        case ByteCodeConstants.TERNARYOPSTORE:
            return visit(((TernaryOpStore)instruction).objectref, dupStore);
        case ByteCodeConstants.PREINC:
        case ByteCodeConstants.POSTINC:
            return visit(((IncInstruction)instruction).value, dupStore);
        case Const.GETFIELD:
            return visit(((GetField)instruction).objectref, dupStore);
        case ByteCodeConstants.INITARRAY:
        case ByteCodeConstants.NEWANDINITARRAY:
            {
                InitArrayInstruction iai = (InitArrayInstruction)instruction;
                DupLoad dupLoad = visit(iai.newArray, dupStore);
                if (dupLoad != null)
                    return dupLoad;
                if (iai.values != null)
                    return visit(iai.values, dupStore);
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
                    ", opcode=" + instruction.opcode);
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
