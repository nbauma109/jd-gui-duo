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
 * Replace 'ALoad(1)' in constructor by 'OuterThis()':
 * replace '???.xxx' by 'TestInnerClass.this.xxx'.
 */
public class ReplaceOuterReferenceVisitor
{
    private int opcode;
    private int index;
    private int outerThisInstructionIndex;

    public ReplaceOuterReferenceVisitor(
        int opcode, int index, int outerThisInstructionIndex)
    {
        this.opcode = opcode;
        this.index = index;
        this.outerThisInstructionIndex = outerThisInstructionIndex;
    }

    public void init(int opcode, int index)
    {
        this.opcode = opcode;
        this.index = index;
    }

    public void visit(Instruction instruction)
    {
        switch (instruction.opcode)
        {
        case Const.ARRAYLENGTH:
            {
                ArrayLength al = (ArrayLength)instruction;
                if (match(al.arrayref))
                    al.arrayref = newInstruction(al.arrayref);
                else
                    visit(al.arrayref);
            }
            break;
        case Const.AASTORE:
        case ByteCodeConstants.ARRAYSTORE:
            {
                ArrayStoreInstruction asi = (ArrayStoreInstruction)instruction;
                if (match(asi.arrayref))
                    asi.arrayref = newInstruction(asi.arrayref);
                else
                    visit(asi.arrayref);
                if (match(asi.indexref))
                    asi.indexref = newInstruction(asi.indexref);
                else
                    visit(asi.indexref);
                if (match(asi.valueref))
                    asi.valueref = newInstruction(asi.valueref);
                else
                    visit(asi.valueref);
            }
            break;
        case ByteCodeConstants.ASSERT:
            {
                AssertInstruction ai = (AssertInstruction)instruction;
                if (match(ai.test))
                    ai.test = newInstruction(ai.test);
                else
                    visit(ai.test);
                if (ai.msg != null)
                {
                    if (match(ai.msg))
                        ai.msg = newInstruction(ai.msg);
                    else
                        visit(ai.msg);
                }
            }
            break;
        case Const.ATHROW:
            {
                AThrow aThrow = (AThrow)instruction;
                if (match(aThrow.value))
                    aThrow.value = newInstruction(aThrow.value);
                else
                    visit(aThrow.value);
            }
            break;
        case ByteCodeConstants.UNARYOP:
            {
                UnaryOperatorInstruction uoi = (UnaryOperatorInstruction)instruction;
                if (match(uoi.value))
                    uoi.value = newInstruction(uoi.value);
                else
                    visit(uoi.value);
            }
            break;
        case ByteCodeConstants.BINARYOP:
            {
                BinaryOperatorInstruction boi = (BinaryOperatorInstruction)instruction;
                if (match(boi.value1))
                    boi.value1 = newInstruction(boi.value1);
                else
                    visit(boi.value1);
                if (match(boi.value2))
                    boi.value2 = newInstruction(boi.value2);
                else
                    visit(boi.value2);
            }
            break;
        case Const.CHECKCAST:
            {
                CheckCast checkCast = (CheckCast)instruction;
                if (match(checkCast.objectref))
                    checkCast.objectref = newInstruction(checkCast.objectref);
                else
                    visit(checkCast.objectref);
            }
            break;
        case ByteCodeConstants.STORE:
        case Const.ASTORE:
        case Const.ISTORE:
            {
                StoreInstruction storeInstruction = (StoreInstruction)instruction;
                if (match(storeInstruction.valueref))
                    storeInstruction.valueref = newInstruction(storeInstruction.valueref);
                else
                    visit(storeInstruction.valueref);
            }
            break;
        case ByteCodeConstants.DUPSTORE:
            {
                DupStore dupStore = (DupStore)instruction;
                if (match(dupStore.objectref))
                    dupStore.objectref = newInstruction(dupStore.objectref);
                else
                    visit(dupStore.objectref);
            }
            break;
        case ByteCodeConstants.CONVERT:
        case ByteCodeConstants.IMPLICITCONVERT:
            {
                ConvertInstruction ci = (ConvertInstruction)instruction;
                if (match(ci.value))
                    ci.value = newInstruction(ci.value);
                else
                    visit(ci.value);
            }
            break;
        case ByteCodeConstants.IFCMP:
            {
                IfCmp ifCmp = (IfCmp)instruction;
                if (match(ifCmp.value1))
                    ifCmp.value1 = newInstruction(ifCmp.value1);
                else
                    visit(ifCmp.value1);
                if (match(ifCmp.value2))
                    ifCmp.value2 = newInstruction(ifCmp.value2);
                else
                    visit(ifCmp.value2);
            }
            break;
        case ByteCodeConstants.IF:
        case ByteCodeConstants.IFXNULL:
            {
                IfInstruction iff = (IfInstruction)instruction;
                if (match(iff.value))
                    iff.value = newInstruction(iff.value);
                else
                    visit(iff.value);
            }
            break;
        case ByteCodeConstants.COMPLEXIF:
            {
                List<Instruction> branchList =
                    ((ComplexConditionalBranchInstruction)instruction).instructions;
                for (int i=branchList.size()-1; i>=0; --i)
                    visit(branchList.get(i));
            }
            break;
        case Const.INSTANCEOF:
            {
                InstanceOf instanceOf = (InstanceOf)instruction;
                if (match(instanceOf.objectref))
                    instanceOf.objectref = newInstruction(instanceOf.objectref);
                else
                    visit(instanceOf.objectref);
            }
            break;
        case Const.INVOKEINTERFACE:
        case Const.INVOKESPECIAL:
        case Const.INVOKEVIRTUAL:
            {
                InvokeNoStaticInstruction insi =
                    (InvokeNoStaticInstruction)instruction;
                if (match(insi.objectref))
                    insi.objectref = newInstruction(insi.objectref);
                else
                    visit(insi.objectref);
            }
            // intended fall through
        case Const.INVOKESTATIC:
        case ByteCodeConstants.INVOKENEW:
            {
                List<Instruction> list = ((InvokeInstruction)instruction).args;
                for (int i=list.size()-1; i>=0; --i)
                {
                    if (match(list.get(i)))
                        list.set(i, newInstruction(list.get(i)));
                    else
                        visit(list.get(i));
                }
            }
            break;
        case Const.LOOKUPSWITCH:
            {
                LookupSwitch ls = (LookupSwitch)instruction;
                if (match(ls.key))
                    ls.key = newInstruction(ls.key);
                else
                    visit(ls.key);
            }
            break;
        case Const.MONITORENTER:
            {
                MonitorEnter monitorEnter = (MonitorEnter)instruction;
                if (match(monitorEnter.objectref))
                    monitorEnter.objectref = newInstruction(monitorEnter.objectref);
                else
                    visit(monitorEnter.objectref);
            }
            break;
        case Const.MONITOREXIT:
            {
                MonitorExit monitorExit = (MonitorExit)instruction;
                if (match(monitorExit.objectref))
                    monitorExit.objectref = newInstruction(monitorExit.objectref);
                else
                    visit(monitorExit.objectref);
            }
            break;
        case Const.MULTIANEWARRAY:
            {
                Instruction[] dimensions = ((MultiANewArray)instruction).dimensions;
                for (int i=dimensions.length-1; i>=0; --i)
                {
                    if (match(dimensions[i]))
                        dimensions[i] = newInstruction(dimensions[i]);
                    else
                        visit(dimensions[i]);
                }
            }
            break;
        case Const.NEWARRAY:
            {
                NewArray newArray = (NewArray)instruction;
                if (match(newArray.dimension))
                    newArray.dimension = newInstruction(newArray.dimension);
                else
                    visit(newArray.dimension);
            }
            break;
        case Const.ANEWARRAY:
            {
                ANewArray aNewArray = (ANewArray)instruction;
                if (match(aNewArray.dimension))
                    aNewArray.dimension = newInstruction(aNewArray.dimension);
                else
                    visit(aNewArray.dimension);
            }
            break;
        case Const.POP:
            {
                Pop pop = (Pop)instruction;
                if (match(pop.objectref))
                    pop.objectref = newInstruction(pop.objectref);
                else
                    visit(pop.objectref);
            }
            break;
        case Const.PUTFIELD:
            {
                PutField putField = (PutField)instruction;
                if (match(putField.objectref))
                    putField.objectref = newInstruction(putField.objectref);
                else
                    visit(putField.objectref);
                if (match(putField.valueref))
                    putField.valueref = newInstruction(putField.valueref);
                else
                    visit(putField.valueref);
            }
            break;
        case Const.PUTSTATIC:
            {
                PutStatic putStatic = (PutStatic)instruction;
                if (match(putStatic.valueref))
                    putStatic.valueref = newInstruction(putStatic.valueref);
                else
                    visit(putStatic.valueref);
            }
            break;
        case ByteCodeConstants.XRETURN:
            {
                ReturnInstruction ri = (ReturnInstruction)instruction;
                if (match(ri.valueref))
                    ri.valueref = newInstruction(ri.valueref);
                else
                    visit(ri.valueref);
            }
            break;
        case Const.TABLESWITCH:
            {
                TableSwitch ts = (TableSwitch)instruction;
                if (match(ts.key))
                    ts.key = newInstruction(ts.key);
                else
                    visit(ts.key);
            }
            break;
        case ByteCodeConstants.TERNARYOPSTORE:
            {
                TernaryOpStore tos = (TernaryOpStore)instruction;
                if (match(tos.objectref))
                    tos.objectref = newInstruction(tos.objectref);
                else
                    visit(tos.objectref);
            }
            break;
        case ByteCodeConstants.TERNARYOP:
            {
                TernaryOperator to = (TernaryOperator)instruction;
                if (match(to.test))
                    to.test = newInstruction(to.test);
                else
                    visit(to.test);
                if (match(to.value1))
                    to.value1 = newInstruction(to.value1);
                else
                    visit(to.value1);
                if (match(to.value2))
                    to.value2 = newInstruction(to.value2);
                else
                    visit(to.value2);
            }
            break;
        case ByteCodeConstants.ASSIGNMENT:
            {
                AssignmentInstruction ai = (AssignmentInstruction)instruction;
                if (match(ai.value1))
                    ai.value1 = newInstruction(ai.value1);
                else
                    visit(ai.value1);
                if (match(ai.value2))
                    ai.value2 = newInstruction(ai.value2);
                else
                    visit(ai.value2);
            }
            break;
        case ByteCodeConstants.ARRAYLOAD:
            {
                ArrayLoadInstruction ali = (ArrayLoadInstruction)instruction;
                if (match(ali.arrayref))
                    ali.arrayref = newInstruction(ali.arrayref);
                else
                    visit(ali.arrayref);
                if (match(ali.indexref))
                    ali.indexref = newInstruction(ali.indexref);
                else
                    visit(ali.indexref);
            }
            break;
        case ByteCodeConstants.PREINC:
        case ByteCodeConstants.POSTINC:
            {
                IncInstruction ii = (IncInstruction)instruction;
                if (match(ii.value))
                    ii.value = newInstruction(ii.value);
                else
                    visit(ii.value);
            }
            break;
        case Const.GETFIELD:
            {
                GetField gf = (GetField)instruction;
                if (match(gf.objectref))
                    gf.objectref = newInstruction(gf.objectref);
                else
                    visit(gf.objectref);
            }
            break;
        case ByteCodeConstants.INITARRAY:
        case ByteCodeConstants.NEWANDINITARRAY:
            {
                InitArrayInstruction iai = (InitArrayInstruction)instruction;
                if (match(iai.newArray))
                    iai.newArray = newInstruction(iai.newArray);
                else
                    visit(iai.newArray);
                if (iai.values != null)
                    visit(iai.values);
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
                    "Can not replace DupLoad in " +
                    instruction.getClass().getName() +
                    ", opcode=" + instruction.opcode);
        }
    }

    public void visit(List<Instruction> instructions)
    {
        for (int idx=instructions.size()-1; idx>=0; --idx)
        {
            Instruction i = instructions.get(idx);

            if (match(i))
                instructions.set(idx, newInstruction(i));
            else
                visit(i);
        }
    }

    private boolean match(Instruction i)
    {
        return
            (i.opcode == this.opcode) &&
            (((IndexInstruction)i).index == this.index);
    }

    private Instruction newInstruction(Instruction i)
    {
        return new GetStatic(
            ByteCodeConstants.OUTERTHIS, i.offset,
            i.lineNumber, this.outerThisInstructionIndex);
    }
}
