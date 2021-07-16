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
package jd.core.process.analyzer.instruction.fast.visitor;

import org.apache.bcel.Const;

import java.util.List;

import jd.core.model.instruction.bytecode.ByteCodeConstants;
import jd.core.model.instruction.bytecode.instruction.*;
import jd.core.model.instruction.fast.FastConstants;
import jd.core.model.instruction.fast.instruction.*;
import jd.core.model.instruction.fast.instruction.FastTry.FastCatch;

public class CountDupLoadVisitor
{
    private DupStore dupStore;
    private int counter;

    public CountDupLoadVisitor()
    {
        init(null);
    }

    public void init(DupStore dupStore)
    {
        this.dupStore = dupStore;
        this.counter = 0;
    }

    public void visit(Instruction instruction)
    {
        switch (instruction.opcode)
        {
        case Const.ARRAYLENGTH:
            visit(((ArrayLength)instruction).arrayref);
            break;
        case Const.AASTORE:
        case ByteCodeConstants.ARRAYSTORE:
            {
                ArrayStoreInstruction asi = (ArrayStoreInstruction)instruction;
                visit(asi.arrayref);
                visit(asi.indexref);
                visit(asi.valueref);
            }
            break;
        case Const.ATHROW:
            visit(((AThrow)instruction).value);
            break;
        case ByteCodeConstants.UNARYOP:
            visit(((UnaryOperatorInstruction)instruction).value);
            break;
        case ByteCodeConstants.BINARYOP:
            {
                BinaryOperatorInstruction boi = (BinaryOperatorInstruction)instruction;
                visit(boi.value1);
                visit(boi.value2);
            }
            break;
        case Const.CHECKCAST:
            visit(((CheckCast)instruction).objectref);
            break;
        case ByteCodeConstants.STORE:
        case Const.ASTORE:
        case Const.ISTORE:
            visit(((StoreInstruction)instruction).valueref);
            break;
        case ByteCodeConstants.DUPSTORE:
            visit(((DupStore)instruction).objectref);
            break;
        case ByteCodeConstants.CONVERT:
        case ByteCodeConstants.IMPLICITCONVERT:
            visit(((ConvertInstruction)instruction).value);
            break;
        case ByteCodeConstants.IFCMP:
            {
                IfCmp ifCmp = (IfCmp)instruction;
                visit(ifCmp.value1);
                visit(ifCmp.value2);
            }
            break;
        case ByteCodeConstants.IF:
        case ByteCodeConstants.IFXNULL:
            visit(((IfInstruction)instruction).value);
            break;
        case ByteCodeConstants.COMPLEXIF:
            {
                List<Instruction> branchList =
                    ((ComplexConditionalBranchInstruction)instruction).instructions;
                for (int i=branchList.size()-1; i>=0; --i)
                {
                    visit(branchList.get(i));
                }
            }
            break;
        case Const.INSTANCEOF:
            visit(((InstanceOf)instruction).objectref);
            break;
        case Const.INVOKEINTERFACE:
        case Const.INVOKESPECIAL:
        case Const.INVOKEVIRTUAL:
            visit(((InvokeNoStaticInstruction)instruction).objectref);
            // intended fall through
        case Const.INVOKESTATIC:
            {
                List<Instruction> list = ((InvokeInstruction)instruction).args;
                for (int i=list.size()-1; i>=0; --i)
                {
                    visit(list.get(i));
                }
            }
            break;
        case ByteCodeConstants.INVOKENEW:
            {
                List<Instruction> list = ((InvokeNew)instruction).args;
                for (int i=list.size()-1; i>=0; --i)
                {
                    visit(list.get(i));
                }
            }
            break;
        case Const.LOOKUPSWITCH:
            visit(((LookupSwitch)instruction).key);
            break;
        case Const.MONITORENTER:
            visit(((MonitorEnter)instruction).objectref);
            break;
        case Const.MONITOREXIT:
            visit(((MonitorExit)instruction).objectref);
            break;
        case Const.MULTIANEWARRAY:
            {
                Instruction[] dimensions = ((MultiANewArray)instruction).dimensions;
                for (int i=dimensions.length-1; i>=0; --i)
                {
                    visit(dimensions[i]);
                }
            }
            break;
        case Const.NEWARRAY:
            visit(((NewArray)instruction).dimension);
            break;
        case Const.ANEWARRAY:
            visit(((ANewArray)instruction).dimension);
            break;
        case Const.POP:
            visit(((Pop)instruction).objectref);
            break;
        case Const.PUTFIELD:
            {
                PutField putField = (PutField)instruction;
                visit(putField.objectref);
                visit(putField.valueref);
            }
            break;
        case Const.PUTSTATIC:
            visit(((PutStatic)instruction).valueref);
            break;
        case ByteCodeConstants.XRETURN:
            visit(((ReturnInstruction)instruction).valueref);
            break;
        case Const.TABLESWITCH:
            visit(((TableSwitch)instruction).key);
            break;
        case ByteCodeConstants.TERNARYOPSTORE:
            visit(((TernaryOpStore)instruction).objectref);
            break;
        case ByteCodeConstants.TERNARYOP:
            {
                TernaryOperator to = (TernaryOperator)instruction;
                visit(to.value1);
                visit(to.value2);
            }
            break;
        case ByteCodeConstants.ASSIGNMENT:
            {
                AssignmentInstruction ai = (AssignmentInstruction)instruction;
                visit(ai.value1);
                visit(ai.value2);
            }
            break;
        case ByteCodeConstants.ARRAYLOAD:
            {
                ArrayLoadInstruction ali = (ArrayLoadInstruction)instruction;
                visit(ali.arrayref);
                visit(ali.indexref);
            }
            break;
        case ByteCodeConstants.PREINC:
        case ByteCodeConstants.POSTINC:
            visit(((IncInstruction)instruction).value);
            break;
        case Const.GETFIELD:
            visit(((GetField)instruction).objectref);
            break;
        case ByteCodeConstants.DUPLOAD:
            {
                if (((DupLoad)instruction).dupStore == this.dupStore)
                    this.counter++;
            }
            break;
        case ByteCodeConstants.INITARRAY:
        case ByteCodeConstants.NEWANDINITARRAY:
            {
                InitArrayInstruction iai = (InitArrayInstruction)instruction;
                visit(iai.newArray);
                if (iai.values != null)
                    visit(iai.values);
            }
            break;
        case FastConstants.FOR:
            {
                FastFor ff = (FastFor)instruction;
                if (ff.init != null)
                    visit(ff.init);
                if (ff.inc != null)
                    visit(ff.inc);
            }
            // intended fall through
        case FastConstants.WHILE:
        case FastConstants.DO_WHILE:
        case FastConstants.IF_SIMPLE:
            {
                Instruction test = ((FastTestList)instruction).test;
                if (test != null)
                    visit(test);
            }
            // intended fall through
        case FastConstants.INFINITE_LOOP:
            {
                List<Instruction> instructions =
                    ((FastList)instruction).instructions;
                if (instructions != null)
                    visit(instructions);
            }
            break;
        case FastConstants.FOREACH:
            {
                FastForEach ffe = (FastForEach)instruction;
                visit(ffe.variable);
                visit(ffe.values);
                visit(ffe.instructions);
            }
            break;
        case FastConstants.IF_ELSE:
            {
                FastTest2Lists ft2l = (FastTest2Lists)instruction;
                visit(ft2l.test);
                visit(ft2l.instructions);
                visit(ft2l.instructions2);
            }
            break;
        case FastConstants.IF_CONTINUE:
        case FastConstants.IF_BREAK:
        case FastConstants.IF_LABELED_BREAK:
        case FastConstants.GOTO_CONTINUE:
        case FastConstants.GOTO_BREAK:
        case FastConstants.GOTO_LABELED_BREAK:
            {
                FastInstruction fi = (FastInstruction)instruction;
                if (fi.instruction != null)
                    visit(fi.instruction);
            }
            break;
        case FastConstants.SWITCH:
        case FastConstants.SWITCH_ENUM:
        case FastConstants.SWITCH_STRING:
            {
                FastSwitch fs = (FastSwitch)instruction;
                visit(fs.test);
                FastSwitch.Pair[] pairs = fs.pairs;
                for (int i=pairs.length-1; i>=0; --i)
                {
                    List<Instruction> instructions = pairs[i].getInstructions();
                    if (instructions != null)
                        visit(instructions);
                }
            }
            break;
        case FastConstants.TRY:
            {
                FastTry ft = (FastTry)instruction;
                visit(ft.instructions);
                if (ft.finallyInstructions != null)
                    visit(ft.finallyInstructions);
                List<FastCatch> catchs = ft.catches;
                for (int i=catchs.size()-1; i>=0; --i)
                    visit(catchs.get(i).instructions);
            }
            break;
        case FastConstants.SYNCHRONIZED:
            {
                FastSynchronized fsd = (FastSynchronized)instruction;
                visit(fsd.monitor);
                visit(fsd.instructions);
            }
            break;
        case FastConstants.LABEL:
            {
                FastLabel fl = (FastLabel)instruction;
                if (fl.instruction != null)
                    visit(fl.instruction);
            }
            break;
        case FastConstants.DECLARE:
            {
                FastDeclaration fd = (FastDeclaration)instruction;
                if (fd.instruction != null)
                    visit(fd.instruction);
            }
            break;
        case Const.GETSTATIC:
        case ByteCodeConstants.OUTERTHIS:
        case Const.ACONST_NULL:
        case ByteCodeConstants.LOAD:
        case Const.ALOAD:
        case Const.ILOAD:
        case Const.BIPUSH:
        case ByteCodeConstants.ICONST:
        case ByteCodeConstants.LCONST:
        case ByteCodeConstants.FCONST:
        case ByteCodeConstants.DCONST:
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
                    "Can not count DupLoad in " +
                    instruction.getClass().getName() +
                    ", opcode=" + instruction.opcode);
        }
    }

    private void visit(List<Instruction> instructions)
    {
        for (int i=instructions.size()-1; i>=0; --i)
            visit(instructions.get(i));
    }

    /**
     * @return le dernier parent sur lequel une substitution a été faite
     */
    public int getCounter()
    {
        return this.counter;
    }
}
