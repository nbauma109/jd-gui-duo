/**
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
 */
package jd.core.process.analyzer.instruction.fast.visitor;

import java.util.List;

import jd.core.model.classfile.LocalVariable;
import jd.core.model.classfile.LocalVariables;
import jd.core.model.instruction.bytecode.ByteCodeConstants;
import jd.core.model.instruction.bytecode.instruction.*;
import jd.core.model.instruction.fast.FastConstants;
import jd.core.model.instruction.fast.instruction.*;
import jd.core.model.instruction.fast.instruction.FastTry.FastCatch;

public class CheckLocalVariableUsedVisitor
{
    private CheckLocalVariableUsedVisitor() {
    }
        public static boolean visit(
        LocalVariables localVariables, int maxOffset, Instruction instruction)
    {
        switch (instruction.opcode)
        {
        case ByteCodeConstants.ARRAYLENGTH:
            return visit(
                localVariables, maxOffset, ((ArrayLength)instruction).arrayref);
        case ByteCodeConstants.AASTORE:
        case ByteCodeConstants.ARRAYSTORE:
            {
                ArrayStoreInstruction asi = (ArrayStoreInstruction)instruction;
                return visit(localVariables, maxOffset, asi.indexref) || visit(localVariables, maxOffset, asi.valueref);
            }
        case ByteCodeConstants.ATHROW:
            return visit(localVariables, maxOffset, ((AThrow)instruction).value);
        case ByteCodeConstants.UNARYOP:
            return visit(
                localVariables, maxOffset,
                ((UnaryOperatorInstruction)instruction).value);
        case ByteCodeConstants.BINARYOP:
            {
                BinaryOperatorInstruction boi =
                    (BinaryOperatorInstruction)instruction;
                return visit(localVariables, maxOffset, boi.value1) || visit(localVariables, maxOffset, boi.value2);
            }
        case ByteCodeConstants.CHECKCAST:
            return visit(
                localVariables, maxOffset, ((CheckCast)instruction).objectref);
        case ByteCodeConstants.LOAD:
        case ByteCodeConstants.ALOAD:
        case ByteCodeConstants.ILOAD:
            {
                LoadInstruction li = (LoadInstruction)instruction;
                LocalVariable lv =
                    localVariables.getLocalVariableWithIndexAndOffset(
                        li.index, li.offset);
                return lv != null && maxOffset <= lv.startPc;
            }
        case ByteCodeConstants.STORE:
        case ByteCodeConstants.ASTORE:
        case ByteCodeConstants.ISTORE:
            {
                StoreInstruction si = (StoreInstruction)instruction;
                LocalVariable lv =
                    localVariables.getLocalVariableWithIndexAndOffset(
                        si.index, si.offset);
                return (lv != null && maxOffset <= lv.startPc) || visit(localVariables, maxOffset, si.valueref);
            }
        case ByteCodeConstants.DUPSTORE:
            return visit(
                localVariables, maxOffset, ((DupStore)instruction).objectref);
        case ByteCodeConstants.CONVERT:
        case ByteCodeConstants.IMPLICITCONVERT:
            return visit(
                localVariables, maxOffset,
                ((ConvertInstruction)instruction).value);
        case ByteCodeConstants.IFCMP:
            {
                IfCmp ifCmp = (IfCmp)instruction;
                return visit(localVariables, maxOffset, ifCmp.value1) || visit(localVariables, maxOffset, ifCmp.value2);
            }
        case ByteCodeConstants.IF:
        case ByteCodeConstants.IFXNULL:
            return visit(
                localVariables, maxOffset, ((IfInstruction)instruction).value);
        case ByteCodeConstants.COMPLEXIF:
            {
                List<Instruction> branchList =
                    ((ComplexConditionalBranchInstruction)instruction).instructions;
                for (int i=branchList.size()-1; i>=0; --i)
                {
                    if (visit(localVariables, maxOffset, branchList.get(i))) {
                        return true;
                    }
                }
                return false;
            }
        case ByteCodeConstants.INSTANCEOF:
            return visit(
                localVariables, maxOffset, ((InstanceOf)instruction).objectref);
        case ByteCodeConstants.INVOKEINTERFACE:
        case ByteCodeConstants.INVOKESPECIAL:
        case ByteCodeConstants.INVOKEVIRTUAL:
            if (visit(
                    localVariables, maxOffset,
                    ((InvokeNoStaticInstruction)instruction).objectref)) {
                return true;
            }
            // intended fall through
        case ByteCodeConstants.INVOKESTATIC:
            {
                List<Instruction> list = ((InvokeInstruction)instruction).args;
                for (int i=list.size()-1; i>=0; --i)
                {
                    if (visit(localVariables, maxOffset, list.get(i))) {
                        return true;
                    }
                }
                return false;
            }
        case ByteCodeConstants.INVOKENEW:
            {
                List<Instruction> list = ((InvokeNew)instruction).args;
                for (int i=list.size()-1; i>=0; --i)
                {
                    if (visit(localVariables, maxOffset, list.get(i))) {
                        return true;
                    }
                }
                return false;
            }
        case ByteCodeConstants.LOOKUPSWITCH:
            return visit(
                localVariables, maxOffset, ((LookupSwitch)instruction).key);
        case ByteCodeConstants.MONITORENTER:
            return visit(
                localVariables, maxOffset,
                ((MonitorEnter)instruction).objectref);
        case ByteCodeConstants.MONITOREXIT:
            return visit(
                localVariables, maxOffset,
                ((MonitorExit)instruction).objectref);
        case ByteCodeConstants.MULTIANEWARRAY:
            {
                Instruction[] dimensions = ((MultiANewArray)instruction).dimensions;
                for (int i=dimensions.length-1; i>=0; --i)
                {
                    if (visit(localVariables, maxOffset, dimensions[i])) {
                        return true;
                    }
                }
                return false;
            }
        case ByteCodeConstants.NEWARRAY:
            return visit(
                localVariables, maxOffset,
                ((NewArray)instruction).dimension);
        case ByteCodeConstants.ANEWARRAY:
            return visit(
                localVariables, maxOffset,
                ((ANewArray)instruction).dimension);
        case ByteCodeConstants.POP:
            return visit(
                localVariables, maxOffset,
                ((Pop)instruction).objectref);
        case ByteCodeConstants.PUTFIELD:
            {
                PutField putField = (PutField)instruction;
                return visit(localVariables, maxOffset, putField.objectref) || visit(localVariables, maxOffset, putField.valueref);
            }
        case ByteCodeConstants.PUTSTATIC:
            return visit(
                localVariables, maxOffset,
                ((PutStatic)instruction).valueref);
        case ByteCodeConstants.XRETURN:
            return visit(
                localVariables, maxOffset,
                ((ReturnInstruction)instruction).valueref);
        case ByteCodeConstants.TABLESWITCH:
            return visit(
                localVariables, maxOffset,
                ((TableSwitch)instruction).key);
        case ByteCodeConstants.TERNARYOPSTORE:
            return visit(
                localVariables, maxOffset,
                ((TernaryOpStore)instruction).objectref);
        case ByteCodeConstants.TERNARYOP:
            {
                TernaryOperator to = (TernaryOperator)instruction;
                return visit(localVariables, maxOffset, to.value1) || visit(localVariables, maxOffset, to.value2);
            }
        case ByteCodeConstants.ASSIGNMENT:
            {
                AssignmentInstruction ai = (AssignmentInstruction)instruction;
                return visit(localVariables, maxOffset, ai.value1) || visit(localVariables, maxOffset, ai.value2);
            }
        case ByteCodeConstants.ARRAYLOAD:
            {
                ArrayLoadInstruction ali = (ArrayLoadInstruction)instruction;
                return visit(localVariables, maxOffset, ali.arrayref) || visit(localVariables, maxOffset, ali.indexref);
            }
        case ByteCodeConstants.PREINC:
        case ByteCodeConstants.POSTINC:
            return visit(
                localVariables, maxOffset,
                ((IncInstruction)instruction).value);
        case ByteCodeConstants.GETFIELD:
            return visit(
                localVariables, maxOffset,
                ((GetField)instruction).objectref);
        case ByteCodeConstants.INITARRAY:
        case ByteCodeConstants.NEWANDINITARRAY:
            {
                InitArrayInstruction iai = (InitArrayInstruction)instruction;
                return visit(localVariables, maxOffset, iai.newArray) || (iai.values != null && visit(localVariables, maxOffset, iai.values));
            }
        case FastConstants.FOR:
            {
                FastFor ff = (FastFor)instruction;
                return (ff.init != null && visit(localVariables, maxOffset, ff.init)) || (ff.inc != null && visit(localVariables, maxOffset, ff.inc));
            }
        case FastConstants.WHILE:
        case FastConstants.DO_WHILE:
        case FastConstants.IF_SIMPLE:
            {
                Instruction test = ((FastTestList)instruction).test;
                return test != null && visit(localVariables, maxOffset, test);
            }
        case FastConstants.INFINITE_LOOP:
            {
                List<Instruction> instructions =
                        ((FastList)instruction).instructions;
                return instructions != null && visit(localVariables, maxOffset, instructions);
            }
        case FastConstants.FOREACH:
            {
                FastForEach ffe = (FastForEach)instruction;
                return visit(localVariables, maxOffset, ffe.variable) || visit(localVariables, maxOffset, ffe.values) || visit(localVariables, maxOffset, ffe.instructions);
            }
        case FastConstants.IF_ELSE:
            {
                FastTest2Lists ft2l = (FastTest2Lists)instruction;
                return visit(localVariables, maxOffset, ft2l.test) || visit(localVariables, maxOffset, ft2l.instructions) || visit(localVariables, maxOffset, ft2l.instructions2);
            }
        case FastConstants.IF_CONTINUE:
        case FastConstants.IF_BREAK:
        case FastConstants.IF_LABELED_BREAK:
        case FastConstants.GOTO_CONTINUE:
        case FastConstants.GOTO_BREAK:
        case FastConstants.GOTO_LABELED_BREAK:
            {
                FastInstruction fi = (FastInstruction)instruction;
                return fi.instruction != null && visit(localVariables, maxOffset, fi.instruction);
            }
        case FastConstants.SWITCH:
        case FastConstants.SWITCH_ENUM:
        case FastConstants.SWITCH_STRING:
            {
                FastSwitch fs = (FastSwitch)instruction;
                if (visit(localVariables, maxOffset, fs.test)) {
                    return true;
                }
                FastSwitch.Pair[] pairs = fs.pairs;
                List<Instruction> instructions;
                for (int i=pairs.length-1; i>=0; --i)
                {
                    instructions = pairs[i].getInstructions();
                    if (instructions != null && visit(localVariables, maxOffset, instructions)) {
                        return true;
                    }
                }
                return false;
            }
        case FastConstants.TRY:
            {
                FastTry ft = (FastTry)instruction;
                if (visit(localVariables, maxOffset, ft.instructions) || (ft.finallyInstructions != null && visit(localVariables, maxOffset, ft.finallyInstructions))) {
                    return true;
                }
                List<FastCatch> catchs = ft.catches;
                for (int i=catchs.size()-1; i>=0; --i) {
                    if (visit(localVariables, maxOffset, catchs.get(i).instructions)) {
                        return true;
                    }
                }
                return false;
            }
        case FastConstants.SYNCHRONIZED:
            {
                FastSynchronized fsd = (FastSynchronized)instruction;
                return visit(localVariables, maxOffset, fsd.monitor) || visit(localVariables, maxOffset, fsd.instructions);
            }
        case FastConstants.LABEL:
            {
                FastLabel fl = (FastLabel)instruction;
                return fl.instruction != null && visit(localVariables, maxOffset, fl.instruction);
            }
        case FastConstants.DECLARE:
            {
                FastDeclaration fd = (FastDeclaration)instruction;
                return fd.instruction != null && visit(localVariables, maxOffset, fd.instruction);
            }
        case ByteCodeConstants.GETSTATIC:
        case ByteCodeConstants.OUTERTHIS:
        case ByteCodeConstants.ACONST_NULL:
        case ByteCodeConstants.BIPUSH:
        case ByteCodeConstants.ICONST:
        case ByteCodeConstants.LCONST:
        case ByteCodeConstants.FCONST:
        case ByteCodeConstants.DCONST:
        case ByteCodeConstants.GOTO:
        case ByteCodeConstants.IINC:
        case ByteCodeConstants.JSR:
        case ByteCodeConstants.LDC:
        case ByteCodeConstants.LDC2_W:
        case ByteCodeConstants.NEW:
        case ByteCodeConstants.NOP:
        case ByteCodeConstants.SIPUSH:
        case ByteCodeConstants.RET:
        case ByteCodeConstants.RETURN:
        case ByteCodeConstants.EXCEPTIONLOAD:
        case ByteCodeConstants.RETURNADDRESSLOAD:
        case ByteCodeConstants.DUPLOAD:
            return false;
        default:
            System.err.println(
                    "Can not find local variable used in " +
                    instruction.getClass().getName() +
                    ", opcode=" + instruction.opcode);
            return false;
        }
    }

    private static boolean visit(
        LocalVariables localVariables, int maxOffset,
        List<Instruction> instructions)
    {
        for (int i=instructions.size()-1; i>=0; --i) {
            if (visit(localVariables, maxOffset, instructions.get(i))) {
                return true;
            }
        }
        return false;
    }
}
