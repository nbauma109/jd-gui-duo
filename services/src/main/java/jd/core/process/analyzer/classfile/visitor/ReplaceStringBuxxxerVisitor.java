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
import org.apache.bcel.classfile.ConstantClass;
import org.apache.bcel.classfile.ConstantNameAndType;
import org.jd.core.v1.model.classfile.constant.ConstantMethodref;
import org.jd.core.v1.util.StringConstants;

import java.util.List;

import jd.core.model.classfile.ConstantPool;
import jd.core.model.instruction.bytecode.ByteCodeConstants;
import jd.core.model.instruction.bytecode.instruction.*;

public class ReplaceStringBuxxxerVisitor
{
    private ConstantPool constants;

    public ReplaceStringBuxxxerVisitor(ConstantPool constants)
    {
        this.constants = constants;
    }

    public void visit(Instruction instruction)
    {
        switch (instruction.opcode)
        {
        case Const.ARRAYLENGTH:
            {
                ArrayLength al = (ArrayLength)instruction;
                Instruction i = match(al.arrayref);
                if (i == null)
                    visit(al.arrayref);
                else
                    al.arrayref = i;
            }
            break;
        case ByteCodeConstants.ARRAYLOAD:
            {
                ArrayLoadInstruction ali = (ArrayLoadInstruction)instruction;
                Instruction i = match(ali.arrayref);
                if (i == null)
                    visit(ali.arrayref);
                else
                    ali.arrayref = i;

                i = match(ali.indexref);
                if (i == null)
                    visit(ali.indexref);
                else
                    ali.indexref = i;
            }
            break;
        case Const.AASTORE:
        case ByteCodeConstants.ARRAYSTORE:
            {
                ArrayStoreInstruction asi = (ArrayStoreInstruction)instruction;
                Instruction i = match(asi.arrayref);
                if (i == null)
                    visit(asi.arrayref);
                else
                    asi.arrayref = i;

                i = match(asi.indexref);
                if (i == null)
                    visit(asi.indexref);
                else
                    asi.indexref = i;

                i = match(asi.valueref);
                if (i == null)
                    visit(asi.valueref);
                else
                    asi.valueref = i;
            }
            break;
        case ByteCodeConstants.ASSERT:
            {
                AssertInstruction ai = (AssertInstruction)instruction;
                Instruction i = match(ai.test);
                if (i == null)
                    visit(ai.test);
                else
                    ai.test = i;
                if (ai.msg != null)
                {
                    i = match(ai.msg);
                    if (i == null)
                        visit(ai.msg);
                    else
                        ai.msg = i;
                }
            }
            break;
        case ByteCodeConstants.ASSIGNMENT:
            {
                AssignmentInstruction ai = (AssignmentInstruction)instruction;
                Instruction i = match(ai.value1);
                if (i == null)
                    visit(ai.value1);
                else
                    ai.value1 = i;

                i = match(ai.value2);
                if (i == null)
                    visit(ai.value2);
                else
                    ai.value2 = i;
            }
            break;
        case Const.ATHROW:
            {
                AThrow aThrow = (AThrow)instruction;
                visit(aThrow.value);
            }
            break;
        case ByteCodeConstants.BINARYOP:
            {
                BinaryOperatorInstruction boi =
                    (BinaryOperatorInstruction)instruction;
                Instruction i = match(boi.value1);
                if (i == null)
                    visit(boi.value1);
                else
                    boi.value1 = i;

                i = match(boi.value2);
                if (i == null)
                    visit(boi.value2);
                else
                    boi.value2 = i;
            }
            break;
        case ByteCodeConstants.UNARYOP:
            {
                UnaryOperatorInstruction uoi =
                    (UnaryOperatorInstruction)instruction;
                Instruction i = match(uoi.value);
                if (i == null)
                    visit(uoi.value);
                else
                    uoi.value = i;
            }
            break;
        case ByteCodeConstants.DUPSTORE:
            {
                DupStore dupStore = (DupStore)instruction;
                Instruction i = match(dupStore.objectref);
                if (i == null)
                    visit(dupStore.objectref);
                else
                    dupStore.objectref = i;
            }
            break;
        case Const.CHECKCAST:
            {
                CheckCast cc = (CheckCast)instruction;
                Instruction i = match(cc.objectref);
                if (i == null)
                    visit(cc.objectref);
                else
                    cc.objectref = i;
            }
            break;
        case ByteCodeConstants.CONVERT:
        case ByteCodeConstants.IMPLICITCONVERT:
            {
                ConvertInstruction ci = (ConvertInstruction)instruction;
                Instruction i = match(ci.value);
                if (i == null)
                    visit(ci.value);
                else
                    ci.value = i;
            }
            break;
        case ByteCodeConstants.IF:
        case ByteCodeConstants.IFXNULL:
            {
                IfInstruction ifInstruction = (IfInstruction)instruction;
                Instruction i = match(ifInstruction.value);
                if (i == null)
                    visit(ifInstruction.value);
                else
                    ifInstruction.value = i;
            }
            break;
        case ByteCodeConstants.IFCMP:
            {
                IfCmp ifCmpInstruction = (IfCmp)instruction;
                Instruction i = match(ifCmpInstruction.value1);
                if (i == null)
                    visit(ifCmpInstruction.value1);
                else
                    ifCmpInstruction.value1 = i;

                i = match(ifCmpInstruction.value2);
                if (i == null)
                    visit(ifCmpInstruction.value2);
                else
                    ifCmpInstruction.value2 = i;
            }
            break;
        case Const.INSTANCEOF:
            {
                InstanceOf instanceOf = (InstanceOf)instruction;
                Instruction i = match(instanceOf.objectref);
                if (i == null)
                    visit(instanceOf.objectref);
                else
                    instanceOf.objectref = i;
            }
            break;
        case ByteCodeConstants.COMPLEXIF:
            {
                ComplexConditionalBranchInstruction complexIf = (ComplexConditionalBranchInstruction)instruction;
                List<Instruction> branchList = complexIf.instructions;
                for (int i=branchList.size()-1; i>=0; --i)
                {
                    visit(branchList.get(i));
                }
            }
            break;
        case Const.GETFIELD:
            {
                GetField getField = (GetField)instruction;
                Instruction i = match(getField.objectref);
                if (i == null)
                    visit(getField.objectref);
                else
                    getField.objectref = i;
            }
            break;
        case Const.INVOKEVIRTUAL:
        case Const.INVOKEINTERFACE:
        case Const.INVOKESPECIAL:
            {
                InvokeNoStaticInstruction insi =
                    (InvokeNoStaticInstruction)instruction;
                Instruction i = match(insi.objectref);
                if (i == null)
                    visit(insi.objectref);
                else
                    insi.objectref = i;
                replaceInArgs(insi.args);
            }
            break;
        case Const.INVOKESTATIC:
        case ByteCodeConstants.INVOKENEW:
            replaceInArgs(((InvokeInstruction)instruction).args);
            break;
        case Const.LOOKUPSWITCH:
            {
                LookupSwitch lookupSwitch = (LookupSwitch)instruction;
                Instruction i = match(lookupSwitch.key);
                if (i == null)
                    visit(lookupSwitch.key);
                else
                    lookupSwitch.key = i;
            }
            break;
        case Const.MULTIANEWARRAY:
            {
                MultiANewArray multiANewArray = (MultiANewArray)instruction;
                Instruction[] dimensions = multiANewArray.dimensions;
                Instruction ins;

                for (int i=dimensions.length-1; i>=0; i--)
                {
                    ins = match(dimensions[i]);
                    if (ins == null)
                        visit(dimensions[i]);
                    else
                        dimensions[i] = ins;
                }
            }
            break;
        case Const.NEWARRAY:
            {
                NewArray newArray = (NewArray)instruction;
                Instruction i = match(newArray.dimension);
                if (i == null)
                    visit(newArray.dimension);
                else
                    newArray.dimension = i;
            }
            break;
        case Const.ANEWARRAY:
            {
                ANewArray newArray = (ANewArray)instruction;
                Instruction i = match(newArray.dimension);
                if (i == null)
                    visit(newArray.dimension);
                else
                    newArray.dimension = i;
            }
            break;
        case Const.POP:
            visit(((Pop)instruction).objectref);
            break;
        case Const.PUTFIELD:
            {
                PutField putField = (PutField)instruction;
                Instruction i = match(putField.objectref);
                if (i == null)
                    visit(putField.objectref);
                else
                    putField.objectref = i;

                i = match(putField.valueref);
                if (i == null)
                    visit(putField.valueref);
                else
                    putField.valueref = i;
            }
            break;
        case Const.PUTSTATIC:
            {
                PutStatic putStatic = (PutStatic)instruction;
                Instruction i = match(putStatic.valueref);
                if (i == null)
                    visit(putStatic.valueref);
                else
                    putStatic.valueref = i;
            }
            break;
        case ByteCodeConstants.XRETURN:
            {
                ReturnInstruction returnInstruction =
                    (ReturnInstruction)instruction;
                Instruction i = match(returnInstruction.valueref);
                if (i == null)
                    visit(returnInstruction.valueref);
                else
                    returnInstruction.valueref = i;
            }
            break;
        case ByteCodeConstants.STORE:
        case Const.ASTORE:
        case Const.ISTORE:
            {
                StoreInstruction storeInstruction =
                    (StoreInstruction)instruction;
                Instruction i = match(storeInstruction.valueref);
                if (i == null)
                    visit(storeInstruction.valueref);
                else
                    storeInstruction.valueref = i;
            }
            break;
        case Const.TABLESWITCH:
            {
                TableSwitch tableSwitch = (TableSwitch)instruction;
                Instruction i = match(tableSwitch.key);
                if (i == null)
                    visit(tableSwitch.key);
                else
                    tableSwitch.key = i;
            }
            break;
        case ByteCodeConstants.TERNARYOPSTORE:
            {
                TernaryOpStore tosInstruction = (TernaryOpStore)instruction;
                Instruction i = match(tosInstruction.objectref);
                if (i == null)
                    visit(tosInstruction.objectref);
                else
                    tosInstruction.objectref = i;
            }
            break;
        case ByteCodeConstants.TERNARYOP:
            {
                TernaryOperator to = (TernaryOperator)instruction;
                Instruction i = match(to.value1);
                if (i == null)
                    visit(to.value1);
                else
                    to.value1 = i;

                i = match(to.value2);
                if (i == null)
                    visit(to.value2);
                else
                    to.value2 = i;
            }
            break;
        case Const.MONITORENTER:
            {
                MonitorEnter meInstruction = (MonitorEnter)instruction;
                Instruction i = match(meInstruction.objectref);
                if (i == null)
                    visit(meInstruction.objectref);
                else
                    meInstruction.objectref = i;
            }
            break;
        case Const.MONITOREXIT:
            {
                MonitorExit meInstruction = (MonitorExit)instruction;
                Instruction i = match(meInstruction.objectref);
                if (i == null)
                    visit(meInstruction.objectref);
                else
                    meInstruction.objectref = i;
            }
            break;
        case ByteCodeConstants.INITARRAY:
        case ByteCodeConstants.NEWANDINITARRAY:
            {
                InitArrayInstruction iaInstruction =
                    (InitArrayInstruction)instruction;
                Instruction i = match(iaInstruction.newArray);
                if (i == null)
                    visit(iaInstruction.newArray);
                else
                    iaInstruction.newArray = i;

                for (int index=iaInstruction.values.size()-1; index>=0; --index)
                {
                    i = match(iaInstruction.values.get(index));
                    if (i == null)
                        visit(iaInstruction.values.get(index));
                    else
                        iaInstruction.values.set(index, i);
                }
            }
            break;
        case Const.ACONST_NULL:
        case ByteCodeConstants.DUPLOAD:
        case Const.LDC:
        case Const.LDC2_W:
        case Const.NEW:
        case Const.RETURN:
        case Const.BIPUSH:
        case ByteCodeConstants.DCONST:
        case ByteCodeConstants.FCONST:
        case ByteCodeConstants.ICONST:
        case ByteCodeConstants.LCONST:
        case Const.IINC:
        case ByteCodeConstants.PREINC:
        case ByteCodeConstants.POSTINC:
        case Const.JSR:
        case Const.GETSTATIC:
        case ByteCodeConstants.OUTERTHIS:
        case Const.SIPUSH:
        case ByteCodeConstants.LOAD:
        case Const.ALOAD:
        case Const.ILOAD:
        case Const.GOTO:
        case ByteCodeConstants.EXCEPTIONLOAD:
        case Const.RET:
        case ByteCodeConstants.RETURNADDRESSLOAD:
            break;
        default:
            System.err.println(
                    "Can not replace StringBuxxxer in " +
                    instruction.getClass().getName() + " " +
                    instruction.opcode);
        }
    }

    private void replaceInArgs(List<Instruction> args)
    {
        if (!args.isEmpty())
        {
            Instruction ins;

            for (int i=args.size()-1; i>=0; --i)
            {
                ins = match(args.get(i));
                if (ins == null)
                    visit(args.get(i));
                else
                    args.set(i, ins);
            }
        }
    }

    private Instruction match(Instruction i)
    {
        if (i.opcode == Const.INVOKEVIRTUAL)
        {
            Invokevirtual iv = (Invokevirtual)i;
            ConstantMethodref cmr = this.constants.getConstantMethodref(iv.index);
            ConstantClass cc = this.constants.getConstantClass(cmr.getClassIndex());

            if ((cc.getNameIndex() == constants.stringBufferClassNameIndex) ||
                (cc.getNameIndex() == constants.stringBuilderClassNameIndex))
            {
                ConstantNameAndType cnat =
                    constants.getConstantNameAndType(cmr.getNameAndTypeIndex());

                if (cnat.getNameIndex() == constants.toStringIndex)
                    return match(iv.objectref, cmr.getClassIndex());
            }
        }

        return null;
    }

    private Instruction match(Instruction i, int classIndex)
    {
        if (i.opcode == Const.INVOKEVIRTUAL)
        {
            InvokeNoStaticInstruction insi = (InvokeNoStaticInstruction)i;
            ConstantMethodref cmr =
                this.constants.getConstantMethodref(insi.index);

            if (cmr.getClassIndex() == classIndex)
            {
                ConstantNameAndType cnat =
                    constants.getConstantNameAndType(cmr.getNameAndTypeIndex());

                if ((cnat.getNameIndex() == this.constants.appendIndex) &&
                    (insi.args.size() == 1))
                {
                    Instruction result = match(insi.objectref, cmr.getClassIndex());

                    if (result == null)
                    {
                        return insi.args.get(0);
                    }
                    return new BinaryOperatorInstruction(
                        ByteCodeConstants.BINARYOP, i.offset, i.lineNumber,
                        4,  StringConstants.INTERNAL_STRING_SIGNATURE, "+",
                        result, insi.args.get(0));
                }
            }
        }
        else if (i.opcode == ByteCodeConstants.INVOKENEW)
        {
            InvokeNew in = (InvokeNew)i;
            ConstantMethodref cmr =
                this.constants.getConstantMethodref(in.index);

            if ((cmr.getClassIndex() == classIndex) && (in.args.size() == 1))
            {
                Instruction arg0 = in.args.get(0);

                // Remove String.valueOf for String
                if (arg0.opcode == Const.INVOKESTATIC)
                {
                    Invokestatic is = (Invokestatic)arg0;
                    cmr = this.constants.getConstantMethodref(is.index);
                    ConstantClass cc = this.constants.getConstantClass(cmr.getClassIndex());

                    if (cc.getNameIndex() == this.constants.stringClassNameIndex)
                    {
                        ConstantNameAndType cnat =
                            this.constants.getConstantNameAndType(cmr.getNameAndTypeIndex());

                        if ((cnat.getNameIndex() == this.constants.valueOfIndex) &&
                            (is.args.size() == 1))
                            return is.args.get(0);
                    }
                }

                return arg0;
            }
        }

        return null;
    }
}
