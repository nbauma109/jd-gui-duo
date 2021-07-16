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
import jd.core.model.instruction.fast.instruction.FastDeclaration;
import jd.core.model.instruction.fast.instruction.FastSynchronized;
import jd.core.model.instruction.fast.instruction.FastTestList;
import jd.core.model.instruction.fast.instruction.FastTry;

/*
 * Utilisé par TernaryOpReconstructor
 */
public class ReplaceInstructionVisitor
{
    private int offset;
    private Instruction newInstruction;
    private Instruction oldInstruction;

    public ReplaceInstructionVisitor(int offset, Instruction newInstruction)
    {
        init(offset, newInstruction);
    }

    public void init(int offset, Instruction newInstruction)
    {
        this.offset = offset;
        this.newInstruction = newInstruction;
        this.oldInstruction = null;
    }

    public void visit(Instruction instruction)
    {
        switch (instruction.opcode)
        {
        case Const.ARRAYLENGTH:
            {
                ArrayLength al = (ArrayLength)instruction;
                if (al.arrayref.offset == this.offset)
                {
                    this.oldInstruction = al.arrayref;
                    al.arrayref = this.newInstruction;
                }
                else
                {
                    visit(al.arrayref);
                }
            }
            break;
        case Const.AASTORE:
        case ByteCodeConstants.ARRAYSTORE:
            {
                ArrayStoreInstruction asi = (ArrayStoreInstruction)instruction;
                if (asi.arrayref.offset == this.offset)
                {
                    this.oldInstruction = asi.arrayref;
                    asi.arrayref = this.newInstruction;
                }
                else
                {
                    visit(asi.arrayref);

                    if (this.oldInstruction == null)
                    {
                        if (asi.indexref.offset == this.offset)
                        {
                            this.oldInstruction = asi.indexref;
                            asi.indexref = this.newInstruction;
                        }
                        else
                        {
                            visit(asi.indexref);

                            if (this.oldInstruction == null)
                            {
                                if (asi.valueref.offset == this.offset)
                                {
                                    this.oldInstruction = asi.valueref;
                                    asi.valueref = this.newInstruction;
                                }
                                else
                                {
                                    visit(asi.valueref);
                                }
                            }
                        }
                    }
                }
            }
            break;
        case ByteCodeConstants.ASSERT:
            {
                AssertInstruction ai = (AssertInstruction)instruction;
                if (ai.test.offset == this.offset)
                {
                    this.oldInstruction = ai.test;
                    ai.test = this.newInstruction;
                }
                else
                {
                    visit(ai.test);

                    if ((this.oldInstruction == null) && (ai.msg != null))
                    {
                        if (ai.msg.offset == this.offset)
                        {
                            this.oldInstruction = ai.msg;
                            ai.msg = this.newInstruction;
                        }
                        else
                        {
                            visit(ai.msg);
                        }
                    }
                }
            }
            break;
        case Const.ATHROW:
            {
                AThrow aThrow = (AThrow)instruction;
                if (aThrow.value.offset == this.offset)
                {
                    this.oldInstruction = aThrow.value;
                    aThrow.value = this.newInstruction;
                }
                else
                {
                    visit(aThrow.value);
                }
            }
            break;
        case ByteCodeConstants.UNARYOP:
            {
                UnaryOperatorInstruction uoi = (UnaryOperatorInstruction)instruction;
                if (uoi.value.offset == this.offset)
                {
                    this.oldInstruction = uoi.value;
                    uoi.value = this.newInstruction;
                }
                else
                {
                    visit(uoi.value);
                }
            }
            break;
        case ByteCodeConstants.BINARYOP:
        case ByteCodeConstants.ASSIGNMENT:
            {
                BinaryOperatorInstruction boi = (BinaryOperatorInstruction)instruction;
                if (boi.value1.offset == this.offset)
                {
                    this.oldInstruction = boi.value1;
                    boi.value1 = this.newInstruction;
                }
                else
                {
                    visit(boi.value1);

                    if (this.oldInstruction == null)
                    {
                        if (boi.value2.offset == this.offset)
                        {
                            this.oldInstruction = boi.value2;
                            boi.value2 = this.newInstruction;
                        }
                        else
                        {
                            visit(boi.value2);
                        }
                    }
                }
            }
            break;
        case Const.CHECKCAST:
            {
                CheckCast checkCast = (CheckCast)instruction;
                if (checkCast.objectref.offset == this.offset)
                {
                    this.oldInstruction = checkCast.objectref;
                    checkCast.objectref = this.newInstruction;
                }
                else
                {
                    visit(checkCast.objectref);
                }
            }
            break;
        case ByteCodeConstants.STORE:
        case Const.ASTORE:
        case Const.ISTORE:
            {
                StoreInstruction storeInstruction = (StoreInstruction)instruction;
                if (storeInstruction.valueref.offset == this.offset)
                {
                    this.oldInstruction = storeInstruction.valueref;
                    storeInstruction.valueref = this.newInstruction;
                }
                else
                {
                    visit(storeInstruction.valueref);
                }
            }
            break;
        case ByteCodeConstants.DUPSTORE:
            {
                DupStore dupStore = (DupStore)instruction;
                if (dupStore.objectref.offset == this.offset)
                {
                    this.oldInstruction = dupStore.objectref;
                    dupStore.objectref = this.newInstruction;
                }
                else
                {
                    visit(dupStore.objectref);
                }
            }
            break;
        case ByteCodeConstants.CONVERT:
        case ByteCodeConstants.IMPLICITCONVERT:
            {
                ConvertInstruction ci = (ConvertInstruction)instruction;
                if (ci.value.offset == this.offset)
                {
                    this.oldInstruction = ci.value;
                    ci.value = this.newInstruction;
                }
                else
                {
                    visit(ci.value);
                }
            }
            break;
        case ByteCodeConstants.IFCMP:
            {
                IfCmp ifCmp = (IfCmp)instruction;
                if (ifCmp.value1.offset == this.offset)
                {
                    this.oldInstruction = ifCmp.value1;
                    ifCmp.value1 = this.newInstruction;
                }
                else
                {
                    visit(ifCmp.value1);

                    if (this.oldInstruction == null)
                    {
                        if (ifCmp.value2.offset == this.offset)
                        {
                            this.oldInstruction = ifCmp.value2;
                            ifCmp.value2 = this.newInstruction;
                        }
                        else
                        {
                            visit(ifCmp.value2);
                        }
                    }
                }
            }
            break;
        case ByteCodeConstants.IF:
        case ByteCodeConstants.IFXNULL:
            {
                IfInstruction iff = (IfInstruction)instruction;
                if (iff.value.offset == this.offset)
                {
                    this.oldInstruction = iff.value;
                    iff.value = this.newInstruction;
                }
                else
                {
                    visit(iff.value);
                }
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
                if (instanceOf.objectref.offset == this.offset)
                {
                    this.oldInstruction = instanceOf.objectref;
                    instanceOf.objectref = this.newInstruction;
                }
                else
                {
                    visit(instanceOf.objectref);
                }
            }
            break;
        case Const.INVOKEINTERFACE:
        case Const.INVOKESPECIAL:
        case Const.INVOKEVIRTUAL:
            {
                InvokeNoStaticInstruction insi =
                    (InvokeNoStaticInstruction)instruction;
                if (insi.objectref.offset == this.offset)
                {
                    this.oldInstruction = insi.objectref;
                    insi.objectref = this.newInstruction;
                }
                else
                {
                    visit(insi.objectref);
                }
            }
            // intended fall through
        case Const.INVOKESTATIC:
            {
                List<Instruction> list = ((InvokeInstruction)instruction).args;
                for (int i=list.size()-1; (i>=0) && (this.oldInstruction == null); --i)
                {
                    Instruction instuction = list.get(i);
                    if (instuction.offset == this.offset)
                    {
                        this.oldInstruction = instuction;
                        list.set(i, this.newInstruction);
                    }
                    else
                    {
                        visit(instuction);
                    }
                }
            }
            break;
        case ByteCodeConstants.INVOKENEW:
            {
                List<Instruction> list = ((InvokeNew)instruction).args;
                for (int i=list.size()-1; (i>=0) && (this.oldInstruction == null); --i)
                {
                    Instruction instuction = list.get(i);
                    if (instuction.offset == this.offset)
                    {
                        this.oldInstruction = instuction;
                        list.set(i, this.newInstruction);
                    }
                    else
                    {
                        visit(instuction);
                    }
                }
            }
            break;
        case Const.LOOKUPSWITCH:
            {
                LookupSwitch ls = (LookupSwitch)instruction;
                if (ls.key.offset == this.offset)
                {
                    this.oldInstruction = ls.key;
                    ls.key = this.newInstruction;
                }
                else
                {
                    visit(ls.key);
                }
            }
            break;
        case Const.MONITORENTER:
            {
                MonitorEnter monitorEnter = (MonitorEnter)instruction;
                if (monitorEnter.objectref.offset == this.offset)
                {
                    this.oldInstruction = monitorEnter.objectref;
                    monitorEnter.objectref = this.newInstruction;
                }
                else
                {
                    visit(monitorEnter.objectref);
                }
            }
            break;
        case Const.MONITOREXIT:
            {
                MonitorExit monitorExit = (MonitorExit)instruction;
                if (monitorExit.objectref.offset == this.offset)
                {
                    this.oldInstruction = monitorExit.objectref;
                    monitorExit.objectref = this.newInstruction;
                }
                else
                {
                    visit(monitorExit.objectref);
                }
            }
            break;
        case Const.MULTIANEWARRAY:
            {
                Instruction[] dimensions = ((MultiANewArray)instruction).dimensions;
                for (int i=dimensions.length-1; (i>=0) && (this.oldInstruction == null); --i)
                {
                    if (dimensions[i].offset == this.offset)
                    {
                        this.oldInstruction = dimensions[i];
                        dimensions[i] = this.newInstruction;
                    }
                    else
                    {
                        visit(dimensions[i]);
                    }
                }
            }
            break;
        case Const.NEWARRAY:
            {
                NewArray newArray = (NewArray)instruction;
                if (newArray.dimension.offset == this.offset)
                {
                    this.oldInstruction = newArray.dimension;
                    newArray.dimension = this.newInstruction;
                }
                else
                {
                    visit(newArray.dimension);
                }
            }
            break;
        case Const.ANEWARRAY:
            {
                ANewArray aNewArray = (ANewArray)instruction;
                if (aNewArray.dimension.offset == this.offset)
                {
                    this.oldInstruction = aNewArray.dimension;
                    aNewArray.dimension = this.newInstruction;
                }
                else
                {
                    visit(aNewArray.dimension);
                }
            }
            break;
        case Const.POP:
            {
                Pop pop = (Pop)instruction;
                if (pop.objectref.offset == this.offset)
                {
                    this.oldInstruction = pop.objectref;
                    pop.objectref = this.newInstruction;
                }
                else
                {
                    visit(pop.objectref);
                }
            }
            break;
        case Const.PUTFIELD:
            {
                PutField putField = (PutField)instruction;
                if (putField.objectref.offset == this.offset)
                {
                    this.oldInstruction = putField.objectref;
                    putField.objectref = this.newInstruction;
                }
                else
                {
                    visit(putField.objectref);

                    if (this.oldInstruction == null)
                    {
                        if (putField.valueref.offset == this.offset)
                        {
                            this.oldInstruction = putField.valueref;
                            putField.valueref = this.newInstruction;
                        }
                        else
                        {
                            visit(putField.valueref);
                        }
                    }
                }
            }
            break;
        case Const.PUTSTATIC:
            {
                PutStatic putStatic = (PutStatic)instruction;
                if (putStatic.valueref.offset == this.offset)
                {
                    this.oldInstruction = putStatic.valueref;
                    putStatic.valueref = this.newInstruction;
                }
                else
                {
                    visit(putStatic.valueref);
                }
            }
            break;
        case ByteCodeConstants.XRETURN:
            {
                ReturnInstruction ri = (ReturnInstruction)instruction;
                if (ri.valueref.offset == this.offset)
                {
                    this.oldInstruction = ri.valueref;
                    ri.valueref = this.newInstruction;
                }
                else
                {
                    visit(ri.valueref);
                }
            }
            break;
        case Const.TABLESWITCH:
            {
                TableSwitch ts = (TableSwitch)instruction;
                if (ts.key.offset == this.offset)
                {
                    this.oldInstruction = ts.key;
                    ts.key = this.newInstruction;
                }
                else
                {
                    visit(ts.key);
                }
            }
            break;
        case ByteCodeConstants.TERNARYOPSTORE:
            {
                TernaryOpStore tos = (TernaryOpStore)instruction;
                if (tos.objectref.offset == this.offset)
                {
                    this.oldInstruction = tos.objectref;
                    tos.objectref = this.newInstruction;
                }
                else
                {
                    visit(tos.objectref);
                }
            }
            break;
        case ByteCodeConstants.PREINC:
        case ByteCodeConstants.POSTINC:
            {
                IncInstruction ii = (IncInstruction)instruction;
                if (ii.value.offset == this.offset)
                {
                    this.oldInstruction = ii.value;
                    ii.value = this.newInstruction;
                }
                else
                {
                    visit(ii.value);
                }
            }
            break;
        case Const.GETFIELD:
            {
                GetField gf = (GetField)instruction;
                if (gf.objectref.offset == this.offset)
                {
                    this.oldInstruction = gf.objectref;
                    gf.objectref = this.newInstruction;
                }
                else
                {
                    visit(gf.objectref);
                }
            }
            break;
        case ByteCodeConstants.INITARRAY:
        case ByteCodeConstants.NEWANDINITARRAY:
            {
                InitArrayInstruction iai = (InitArrayInstruction)instruction;
                if (iai.newArray.offset == this.offset)
                {
                    this.oldInstruction = iai.newArray;
                    iai.newArray = this.newInstruction;
                }
                else
                {
                    visit(iai.newArray);

                    if (iai.values != null)
                        visit(iai.values);
                }
            }
            break;
        case ByteCodeConstants.TERNARYOP:
            {
                TernaryOperator to = (TernaryOperator)instruction;
                if (to.test.offset == this.offset)
                {
                    this.oldInstruction = to.test;
                    to.test = this.newInstruction;
                }
                else
                {
                    visit(to.test);

                    if (this.oldInstruction == null)
                    {
                        if (to.value1.offset == this.offset)
                        {
                            this.oldInstruction = to.value1;
                            to.value1 = this.newInstruction;
                        }
                        else
                        {
                            visit(to.value1);

                            if (this.oldInstruction == null)
                            {
                                if (to.value2.offset == this.offset)
                                {
                                    this.oldInstruction = to.value2;
                                    to.value2 = this.newInstruction;
                                }
                                else
                                {
                                    visit(to.value2);
                                }
                            }
                        }
                    }
                }
            }
            break;
        case FastConstants.TRY:
            {
                FastTry ft = (FastTry)instruction;

                visit(ft.instructions);

                if (this.oldInstruction == null)
                {
                    if (ft.finallyInstructions != null)
                        visit(ft.finallyInstructions);

                    for (int i=ft.catches.size()-1; (i>=0) && (this.oldInstruction == null); --i)
                        visit(ft.catches.get(i).instructions);
                }
            }
            break;
        case FastConstants.DECLARE:
            {
                FastDeclaration fd = (FastDeclaration)instruction;

                if (fd.instruction != null)
                {
                    if (fd.instruction.offset == this.offset)
                    {
                        this.oldInstruction = fd.instruction;
                        fd.instruction = this.newInstruction;
                    }
                    else
                    {
                        visit(fd.instruction);
                    }
                }
            }
            break;
        case FastConstants.SYNCHRONIZED:
            {
                FastSynchronized fsy = (FastSynchronized)instruction;

                if (fsy.monitor.offset == this.offset)
                {
                    this.oldInstruction = fsy.monitor;
                    fsy.monitor = this.newInstruction;
                }
                else
                {
                    visit(fsy.monitor);

                    if (this.oldInstruction == null)
                        visit(fsy.instructions);
                }
            }
            break;
        case FastConstants.IF_SIMPLE:
            {
                FastTestList ftl = (FastTestList)instruction;

                if (ftl.test.offset == this.offset)
                {
                    this.oldInstruction = ftl.test;
                    ftl.test = this.newInstruction;
                }
                else
                {
                    visit(ftl.test);

                    if ((this.oldInstruction == null) &&
                        (ftl.instructions != null))
                        visit(ftl.instructions);
                }
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
                    "Can not replace code in " +
                    instruction.getClass().getName() +
                    ", opcode=" + instruction.opcode);
        }
    }

    private void visit(List<Instruction> instructions)
    {
        for (int i=instructions.size()-1; i>=0; --i)
            visit(instructions.get(i));
    }

    public Instruction getOldInstruction()
    {
        return oldInstruction;
    }
}
