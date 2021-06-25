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

import java.util.List;

import jd.core.model.instruction.bytecode.ByteCodeConstants;
import jd.core.model.instruction.bytecode.instruction.*;
import jd.core.model.instruction.fast.FastConstants;
import jd.core.model.instruction.fast.instruction.*;
import jd.core.model.instruction.fast.instruction.FastSwitch.Pair;
import jd.core.model.instruction.fast.instruction.FastTry.FastCatch;

/*
 * Utilisé par TernaryOpReconstructor
 */
public class SearchInstructionByOpcodeVisitor
{
    private SearchInstructionByOpcodeVisitor() {
        super();
    }

    public static Instruction visit(Instruction instruction, int opcode)
        throws RuntimeException
    {
        if (instruction == null)
            throw new IllegalStateException("Null instruction");

        if (instruction.opcode == opcode)
            return instruction;

        switch (instruction.opcode)
        {
        case ByteCodeConstants.ARRAYLENGTH:
            return visit(((ArrayLength)instruction).arrayref, opcode);
        case ByteCodeConstants.AASTORE:
        case ByteCodeConstants.ARRAYSTORE:
            return visit(((ArrayStoreInstruction)instruction).arrayref, opcode);
        case ByteCodeConstants.ATHROW:
            return visit(((AThrow)instruction).value, opcode);
        case ByteCodeConstants.UNARYOP:
            return visit(((UnaryOperatorInstruction)instruction).value, opcode);
        case ByteCodeConstants.BINARYOP:
        case ByteCodeConstants.ASSIGNMENT:
            {
                BinaryOperatorInstruction boi =
                    (BinaryOperatorInstruction)instruction;
                Instruction tmp = visit(boi.value1, opcode);
                if (tmp != null)
                    return tmp;
                return visit(boi.value2, opcode);
            }
        case ByteCodeConstants.CHECKCAST:
            return visit(((CheckCast)instruction).objectref, opcode);
        case ByteCodeConstants.STORE:
        case ByteCodeConstants.ASTORE:
        case ByteCodeConstants.ISTORE:
            return visit(((StoreInstruction)instruction).valueref, opcode);
        case ByteCodeConstants.DUPSTORE:
            return visit(((DupStore)instruction).objectref, opcode);
        case ByteCodeConstants.CONVERT:
        case ByteCodeConstants.IMPLICITCONVERT:
            return visit(((ConvertInstruction)instruction).value, opcode);
        case ByteCodeConstants.IFCMP:
            {
                IfCmp ifCmp = (IfCmp)instruction;
                Instruction tmp = visit(ifCmp.value1, opcode);
                if (tmp != null)
                    return tmp;
                return visit(ifCmp.value2, opcode);
            }
        case ByteCodeConstants.IF:
        case ByteCodeConstants.IFXNULL:
            return visit(((IfInstruction)instruction).value, opcode);
        case ByteCodeConstants.COMPLEXIF:
            {
                List<Instruction> branchList =
                    ((ComplexConditionalBranchInstruction)instruction).instructions;
                for (int i=branchList.size()-1; i>=0; --i)
                {
                    Instruction tmp = visit(branchList.get(i), opcode);
                    if (tmp != null)
                        return tmp;
                }
            }
            break;
        case ByteCodeConstants.INSTANCEOF:
            return visit(((InstanceOf)instruction).objectref, opcode);
        case ByteCodeConstants.INVOKEINTERFACE:
        case ByteCodeConstants.INVOKESPECIAL:
        case ByteCodeConstants.INVOKEVIRTUAL:
            {
                Instruction result = visit(
                    ((InvokeNoStaticInstruction)instruction).objectref, opcode);
                if (result != null)
                    return result;
            }
            // intended fall through
        case ByteCodeConstants.INVOKESTATIC:
        case ByteCodeConstants.INVOKENEW:
            {
                List<Instruction> list = ((InvokeInstruction)instruction).args;
                for (int i=list.size()-1; i>=0; --i)
                {
                    Instruction tmp = visit(list.get(i), opcode);
                    if (tmp != null)
                        return tmp;
                }
            }
            break;
        case ByteCodeConstants.LOOKUPSWITCH:
            return visit(((LookupSwitch)instruction).key, opcode);
        case ByteCodeConstants.MONITORENTER:
            return visit(((MonitorEnter)instruction).objectref, opcode);
        case ByteCodeConstants.MONITOREXIT:
            return visit(((MonitorExit)instruction).objectref, opcode);
        case ByteCodeConstants.MULTIANEWARRAY:
            {
                Instruction[] dimensions = ((MultiANewArray)instruction).dimensions;
                for (int i=dimensions.length-1; i>=0; --i)
                {
                    Instruction tmp = visit(dimensions[i], opcode);
                    if (tmp != null)
                        return tmp;
                }
            }
            break;
        case ByteCodeConstants.NEWARRAY:
            return visit(((NewArray)instruction).dimension, opcode);
        case ByteCodeConstants.ANEWARRAY:
            return visit(((ANewArray)instruction).dimension, opcode);
        case ByteCodeConstants.POP:
            return visit(((Pop)instruction).objectref, opcode);
        case ByteCodeConstants.PUTFIELD:
            {
                PutField putField = (PutField)instruction;
                Instruction tmp = visit(putField.objectref, opcode);
                if (tmp != null)
                    return tmp;
                return visit(putField.valueref, opcode);
            }
        case ByteCodeConstants.PUTSTATIC:
            return visit(((PutStatic)instruction).valueref, opcode);
        case ByteCodeConstants.XRETURN:
            return visit(((ReturnInstruction)instruction).valueref, opcode);
        case ByteCodeConstants.TABLESWITCH:
            return visit(((TableSwitch)instruction).key, opcode);
        case ByteCodeConstants.TERNARYOPSTORE:
            return visit(((TernaryOpStore)instruction).objectref, opcode);
        case ByteCodeConstants.PREINC:
        case ByteCodeConstants.POSTINC:
            return visit(((IncInstruction)instruction).value, opcode);
        case ByteCodeConstants.GETFIELD:
            return visit(((GetField)instruction).objectref, opcode);
        case ByteCodeConstants.INITARRAY:
        case ByteCodeConstants.NEWANDINITARRAY:
            {
                InitArrayInstruction iai = (InitArrayInstruction)instruction;
                Instruction tmp = visit(iai.newArray, opcode);
                if (tmp != null)
                    return tmp;
                if (iai.values != null)
                    return visit(iai.values, opcode);
            }
            break;
        case ByteCodeConstants.TERNARYOP:
            {
                TernaryOperator to = (TernaryOperator)instruction;
                Instruction tmp = visit(to.value1, opcode);
                if (tmp != null)
                    return tmp;
                return visit(to.value2, opcode);
            }
        case FastConstants.TRY:
            {
                FastTry ft = (FastTry)instruction;
                Instruction tmp = visit(ft.instructions, opcode);
                if (tmp != null)
                    return tmp;
                List<FastCatch> catches = ft.catches;
                for (int i=catches.size()-1; i>=0; --i)
                {
                    tmp = visit(catches.get(i).instructions, opcode);
                    if (tmp != null)
                        return tmp;
                }
                if (ft.finallyInstructions != null)
                    return visit(ft.finallyInstructions, opcode);
            }
            break;
        case FastConstants.SYNCHRONIZED:
            {
                FastSynchronized fsy = (FastSynchronized)instruction;
                Instruction tmp = visit(fsy.monitor, opcode);
                if (tmp != null)
                    return tmp;
                return visit(fsy.instructions, opcode);
            }
        case FastConstants.FOR:
            {
                FastFor ff = (FastFor)instruction;
                if (ff.init != null)
                {
                    Instruction tmp = visit(ff.init, opcode);
                    if (tmp != null)
                        return tmp;
                }
                if (ff.inc != null)
                {
                    Instruction tmp = visit(ff.inc, opcode);
                    if (tmp != null)
                        return tmp;
                }
            }
            // intended fall through
        case FastConstants.WHILE:
        case FastConstants.DO_WHILE:
        case FastConstants.IF_SIMPLE:
            {
                FastTestList ftl = (FastTestList)instruction;
                if (ftl.test != null)
                {
                    Instruction tmp = visit(ftl.test, opcode);
                    if (tmp != null)
                        return tmp;
                }
            }
            // intended fall through
        case FastConstants.INFINITE_LOOP:
            {
                List<Instruction> instructions =
                        ((FastList)instruction).instructions;
                if (instructions != null)
                    return visit(instructions, opcode);
            }
            break;
        case FastConstants.FOREACH:
            {
                FastForEach ffe = (FastForEach)instruction;
                Instruction tmp = visit(ffe.variable, opcode);
                if (tmp != null)
                    return tmp;
                tmp = visit(ffe.values, opcode);
                if (tmp != null)
                    return tmp;
                return visit(ffe.instructions, opcode);
            }
        case FastConstants.IF_ELSE:
            {
                FastTest2Lists ft2l = (FastTest2Lists)instruction;
                Instruction tmp = visit(ft2l.test, opcode);
                if (tmp != null)
                    return tmp;
                tmp = visit(ft2l.instructions, opcode);
                if (tmp != null)
                    return tmp;
                return visit(ft2l.instructions2, opcode);
            }
        case FastConstants.IF_CONTINUE:
        case FastConstants.IF_BREAK:
        case FastConstants.IF_LABELED_BREAK:
        case FastConstants.GOTO_CONTINUE:
        case FastConstants.GOTO_BREAK:
        case FastConstants.GOTO_LABELED_BREAK:
            {
                FastInstruction fi = (FastInstruction)instruction;
                if (fi.instruction != null)
                    return visit(fi.instruction, opcode);
            }
            break;
        case FastConstants.DECLARE:
            {
                FastDeclaration fd = (FastDeclaration)instruction;
                if (fd.instruction != null)
                    return visit(fd.instruction, opcode);
            }
            break;
        case FastConstants.SWITCH:
        case FastConstants.SWITCH_ENUM:
        case FastConstants.SWITCH_STRING:
            {
                FastSwitch fs = (FastSwitch)instruction;
                Instruction tmp = visit(fs.test, opcode);
                if (tmp != null)
                    return tmp;

                Pair[] pairs = fs.pairs;
                for (int i=pairs.length-1; i>=0; --i)
                {
                    List<Instruction> instructions = pairs[i].getInstructions();
                    if (instructions != null)
                    {
                        tmp = visit(instructions, opcode);
                        if (tmp != null)
                            return tmp;
                    }
                }
            }
            break;
        case FastConstants.LABEL:
            {
                FastLabel fla = (FastLabel)instruction;
                if (fla.instruction != null)
                    return visit(fla.instruction, opcode);
            }
            break;
        case ByteCodeConstants.ACONST_NULL:
        case ByteCodeConstants.ARRAYLOAD:
        case ByteCodeConstants.LOAD:
        case ByteCodeConstants.ALOAD:
        case ByteCodeConstants.ILOAD:
        case ByteCodeConstants.BIPUSH:
        case ByteCodeConstants.ICONST:
        case ByteCodeConstants.LCONST:
        case ByteCodeConstants.FCONST:
        case ByteCodeConstants.DCONST:
        case ByteCodeConstants.DUPLOAD:
        case ByteCodeConstants.EXCEPTIONLOAD:
        case ByteCodeConstants.GETSTATIC:
        case ByteCodeConstants.OUTERTHIS:
        case ByteCodeConstants.GOTO:
        case ByteCodeConstants.IINC:
        case ByteCodeConstants.JSR:
        case ByteCodeConstants.LDC:
        case ByteCodeConstants.LDC2_W:
        case ByteCodeConstants.NEW:
        case ByteCodeConstants.NOP:
        case ByteCodeConstants.RET:
        case ByteCodeConstants.RETURN:
        case ByteCodeConstants.RETURNADDRESSLOAD:
        case ByteCodeConstants.SIPUSH:
            break;
        default:
            System.err.println(
                    "Can not search instruction in " +
                    instruction.getClass().getName() +
                    ", opcode=" + instruction.opcode);
        }

        return null;
    }

    private static Instruction visit(List<Instruction> instructions, int opcode)
        throws RuntimeException
    {
        for (int i=instructions.size()-1; i>=0; --i)
        {
            Instruction instruction = visit(instructions.get(i), opcode);
            if (instruction != null)
                return instruction;
        }

        return null;
    }
}
