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
import org.apache.bcel.classfile.ConstantFieldref;
import org.apache.bcel.classfile.ConstantNameAndType;
import org.jd.core.v1.model.classfile.constant.ConstantMethodref;

import java.util.List;

import jd.core.model.classfile.ClassFile;
import jd.core.model.classfile.ConstantPool;
import jd.core.model.classfile.Method;
import jd.core.model.instruction.bytecode.ByteCodeConstants;
import jd.core.model.instruction.bytecode.instruction.*;

/*
 * Replace static call to "OuterClass access$0(InnerClass)" methods.
 */
public class ReplaceOuterAccessorVisitor
{
    protected ClassFile classFile;

    public ReplaceOuterAccessorVisitor(ClassFile classFile)
    {
        this.classFile = classFile;
    }

    public void visit(Instruction instruction)
    {
        switch (instruction.opcode)
        {
        case Const.ARRAYLENGTH:
            {
                ArrayLength al = (ArrayLength)instruction;
                ClassFile matchedClassFile = match(al.arrayref);
                if (matchedClassFile != null)
                    al.arrayref = newInstruction(matchedClassFile, al.arrayref);
                else
                    visit(al.arrayref);
            }
            break;
        case Const.AASTORE:
        case ByteCodeConstants.ARRAYSTORE:
            {
                ArrayStoreInstruction asi = (ArrayStoreInstruction)instruction;
                ClassFile matchedClassFile = match(asi.arrayref);
                if (matchedClassFile != null)
                    asi.arrayref = newInstruction(matchedClassFile, asi.arrayref);
                else
                    visit(asi.arrayref);
                matchedClassFile = match(asi.indexref);
                if (matchedClassFile != null)
                    asi.indexref = newInstruction(matchedClassFile, asi.indexref);
                else
                    visit(asi.indexref);
                matchedClassFile = match(asi.valueref);
                if (matchedClassFile != null)
                    asi.valueref = newInstruction(matchedClassFile, asi.valueref);
                else
                    visit(asi.valueref);
            }
            break;
        case ByteCodeConstants.ASSERT:
            {
                AssertInstruction ai = (AssertInstruction)instruction;
                ClassFile matchedClassFile = match(ai.test);
                if (matchedClassFile != null)
                    ai.test = newInstruction(matchedClassFile, ai.test);
                else
                    visit(ai.test);
                if (ai.msg != null)
                {
                    matchedClassFile = match(ai.msg);
                    if (matchedClassFile != null)
                        ai.msg = newInstruction(matchedClassFile, ai.msg);
                    else
                        visit(ai.msg);
                }
            }
            break;
        case Const.ATHROW:
            {
                AThrow aThrow = (AThrow)instruction;
                ClassFile matchedClassFile = match(aThrow.value);
                if (matchedClassFile != null)
                    aThrow.value = newInstruction(matchedClassFile, aThrow.value);
                else
                    visit(aThrow.value);
            }
            break;
        case ByteCodeConstants.UNARYOP:
            {
                UnaryOperatorInstruction uoi = (UnaryOperatorInstruction)instruction;
                ClassFile matchedClassFile = match(uoi.value);
                if (matchedClassFile != null)
                    uoi.value = newInstruction(matchedClassFile, uoi.value);
                else
                    visit(uoi.value);
            }
            break;
        case ByteCodeConstants.BINARYOP:
            {
                BinaryOperatorInstruction boi = (BinaryOperatorInstruction)instruction;
                ClassFile matchedClassFile = match(boi.value1);
                if (matchedClassFile != null)
                    boi.value1 = newInstruction(matchedClassFile, boi.value1);
                else
                    visit(boi.value1);
                matchedClassFile = match(boi.value2);
                if (matchedClassFile != null)
                    boi.value2 = newInstruction(matchedClassFile, boi.value2);
                else
                    visit(boi.value2);
            }
            break;
        case Const.CHECKCAST:
            {
                CheckCast checkCast = (CheckCast)instruction;
                ClassFile matchedClassFile = match(checkCast.objectref);
                if (matchedClassFile != null)
                    checkCast.objectref = newInstruction(matchedClassFile, checkCast.objectref);
                else
                    visit(checkCast.objectref);
            }
            break;
        case ByteCodeConstants.STORE:
        case Const.ASTORE:
        case Const.ISTORE:
            {
                StoreInstruction storeInstruction = (StoreInstruction)instruction;
                ClassFile matchedClassFile = match(storeInstruction.valueref);
                if (matchedClassFile != null)
                    storeInstruction.valueref = newInstruction(matchedClassFile, storeInstruction.valueref);
                else
                    visit(storeInstruction.valueref);
            }
            break;
        case ByteCodeConstants.DUPSTORE:
            {
                DupStore dupStore = (DupStore)instruction;
                ClassFile matchedClassFile = match(dupStore.objectref);
                if (matchedClassFile != null)
                    dupStore.objectref = newInstruction(matchedClassFile, dupStore.objectref);
                else
                    visit(dupStore.objectref);
            }
            break;
        case ByteCodeConstants.CONVERT:
        case ByteCodeConstants.IMPLICITCONVERT:
            {
                ConvertInstruction ci = (ConvertInstruction)instruction;
                ClassFile matchedClassFile = match(ci.value);
                if (matchedClassFile != null)
                    ci.value = newInstruction(matchedClassFile, ci.value);
                else
                    visit(ci.value);
            }
            break;
        case ByteCodeConstants.IFCMP:
            {
                IfCmp ifCmp = (IfCmp)instruction;
                ClassFile matchedClassFile = match(ifCmp.value1);
                if (matchedClassFile != null)
                    ifCmp.value1 = newInstruction(matchedClassFile, ifCmp.value1);
                else
                    visit(ifCmp.value1);
                matchedClassFile = match(ifCmp.value2);
                if (matchedClassFile != null)
                    ifCmp.value2 = newInstruction(matchedClassFile, ifCmp.value2);
                else
                    visit(ifCmp.value2);
            }
            break;
        case ByteCodeConstants.IF:
        case ByteCodeConstants.IFXNULL:
            {
                IfInstruction iff = (IfInstruction)instruction;
                ClassFile matchedClassFile = match(iff.value);
                if (matchedClassFile != null)
                    iff.value = newInstruction(matchedClassFile, iff.value);
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
                ClassFile matchedClassFile = match(instanceOf.objectref);
                if (matchedClassFile != null)
                    instanceOf.objectref = newInstruction(matchedClassFile, instanceOf.objectref);
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
                ClassFile matchedClassFile = match(insi.objectref);
                if (matchedClassFile != null)
                    insi.objectref = newInstruction(matchedClassFile, insi.objectref);
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
                    ClassFile matchedClassFile = match(list.get(i));
                    if (matchedClassFile != null)
                        list.set(i, newInstruction(matchedClassFile, list.get(i)));
                    else
                        visit(list.get(i));
                }
            }
            break;
        case Const.LOOKUPSWITCH:
            {
                LookupSwitch ls = (LookupSwitch)instruction;
                ClassFile matchedClassFile = match(ls.key);
                if (matchedClassFile != null)
                    ls.key = newInstruction(matchedClassFile, ls.key);
                else
                    visit(ls.key);
            }
            break;
        case Const.MONITORENTER:
            {
                MonitorEnter monitorEnter = (MonitorEnter)instruction;
                ClassFile matchedClassFile = match(monitorEnter.objectref);
                if (matchedClassFile != null)
                    monitorEnter.objectref = newInstruction(matchedClassFile, monitorEnter.objectref);
                else
                    visit(monitorEnter.objectref);
            }
            break;
        case Const.MONITOREXIT:
            {
                MonitorExit monitorExit = (MonitorExit)instruction;
                ClassFile matchedClassFile = match(monitorExit.objectref);
                if (matchedClassFile != null)
                    monitorExit.objectref = newInstruction(matchedClassFile, monitorExit.objectref);
                else
                    visit(monitorExit.objectref);
            }
            break;
        case Const.MULTIANEWARRAY:
            {
                Instruction[] dimensions = ((MultiANewArray)instruction).dimensions;
                for (int i=dimensions.length-1; i>=0; --i)
                {
                    ClassFile matchedClassFile = match(dimensions[i]);
                    if (matchedClassFile != null)
                        dimensions[i] = newInstruction(matchedClassFile, dimensions[i]);
                    else
                        visit(dimensions[i]);
                }
            }
            break;
        case Const.NEWARRAY:
            {
                NewArray newArray = (NewArray)instruction;
                ClassFile matchedClassFile = match(newArray.dimension);
                if (matchedClassFile != null)
                    newArray.dimension = newInstruction(matchedClassFile, newArray.dimension);
                else
                    visit(newArray.dimension);
            }
            break;
        case Const.ANEWARRAY:
            {
                ANewArray aNewArray = (ANewArray)instruction;
                ClassFile matchedClassFile = match(aNewArray.dimension);
                if (matchedClassFile != null)
                    aNewArray.dimension = newInstruction(matchedClassFile, aNewArray.dimension);
                else
                    visit(aNewArray.dimension);
            }
            break;
        case Const.POP:
            {
                Pop pop = (Pop)instruction;
                ClassFile matchedClassFile = match(pop.objectref);
                if (matchedClassFile != null)
                    pop.objectref = newInstruction(matchedClassFile, pop.objectref);
                else
                    visit(pop.objectref);
            }
            break;
        case Const.PUTFIELD:
            {
                PutField putField = (PutField)instruction;
                ClassFile matchedClassFile = match(putField.objectref);
                if (matchedClassFile != null)
                    putField.objectref = newInstruction(matchedClassFile, putField.objectref);
                else
                    visit(putField.objectref);
                matchedClassFile = match(putField.valueref);
                if (matchedClassFile != null)
                    putField.valueref = newInstruction(matchedClassFile, putField.valueref);
                else
                    visit(putField.valueref);
            }
            break;
        case Const.PUTSTATIC:
            {
                PutStatic putStatic = (PutStatic)instruction;
                ClassFile matchedClassFile = match(putStatic.valueref);
                if (matchedClassFile != null)
                    putStatic.valueref = newInstruction(matchedClassFile, putStatic.valueref);
                else
                    visit(putStatic.valueref);
            }
            break;
        case ByteCodeConstants.XRETURN:
            {
                ReturnInstruction ri = (ReturnInstruction)instruction;
                ClassFile matchedClassFile = match(ri.valueref);
                if (matchedClassFile != null)
                    ri.valueref = newInstruction(matchedClassFile, ri.valueref);
                else
                    visit(ri.valueref);
            }
            break;
        case Const.TABLESWITCH:
            {
                TableSwitch ts = (TableSwitch)instruction;
                ClassFile matchedClassFile = match(ts.key);
                if (matchedClassFile != null)
                    ts.key = newInstruction(matchedClassFile, ts.key);
                else
                    visit(ts.key);
            }
            break;
        case ByteCodeConstants.TERNARYOPSTORE:
            {
                TernaryOpStore tos = (TernaryOpStore)instruction;
                ClassFile matchedClassFile = match(tos.objectref);
                if (matchedClassFile != null)
                    tos.objectref = newInstruction(matchedClassFile, tos.objectref);
                else
                    visit(tos.objectref);
            }
            break;
        case ByteCodeConstants.TERNARYOP:
            {
                TernaryOperator to = (TernaryOperator)instruction;
                ClassFile matchedClassFile = match(to.test);
                if (matchedClassFile != null)
                    to.test = newInstruction(matchedClassFile, to.test);
                else
                    visit(to.test);
                matchedClassFile = match(to.value1);
                if (matchedClassFile != null)
                    to.value1 = newInstruction(matchedClassFile, to.value1);
                else
                    visit(to.value1);
                matchedClassFile = match(to.value2);
                if (matchedClassFile != null)
                    to.value2 = newInstruction(matchedClassFile, to.value2);
                else
                    visit(to.value2);
            }
            break;
        case ByteCodeConstants.ASSIGNMENT:
            {
                AssignmentInstruction ai = (AssignmentInstruction)instruction;
                ClassFile matchedClassFile = match(ai.value1);
                if (matchedClassFile != null)
                    ai.value1 = newInstruction(matchedClassFile, ai.value1);
                else
                    visit(ai.value1);
                matchedClassFile = match(ai.value2);
                if (matchedClassFile != null)
                    ai.value2 = newInstruction(matchedClassFile, ai.value2);
                else
                    visit(ai.value2);
            }
            break;
        case ByteCodeConstants.ARRAYLOAD:
            {
                ArrayLoadInstruction ali = (ArrayLoadInstruction)instruction;
                ClassFile matchedClassFile = match(ali.arrayref);
                if (matchedClassFile != null)
                    ali.arrayref = newInstruction(matchedClassFile, ali.arrayref);
                else
                    visit(ali.arrayref);
                matchedClassFile = match(ali.indexref);
                if (matchedClassFile != null)
                    ali.indexref = newInstruction(matchedClassFile, ali.indexref);
                else
                    visit(ali.indexref);
            }
            break;
        case ByteCodeConstants.PREINC:
        case ByteCodeConstants.POSTINC:
            {
                IncInstruction ii = (IncInstruction)instruction;
                ClassFile matchedClassFile = match(ii.value);
                if (matchedClassFile != null)
                    ii.value = newInstruction(matchedClassFile, ii.value);
                else
                    visit(ii.value);
            }
            break;
        case Const.GETFIELD:
            {
                GetField gf = (GetField)instruction;
                ClassFile matchedClassFile = match(gf.objectref);
                if (matchedClassFile != null)
                    gf.objectref = newInstruction(matchedClassFile, gf.objectref);
                else
                    visit(gf.objectref);
            }
            break;
        case ByteCodeConstants.INITARRAY:
        case ByteCodeConstants.NEWANDINITARRAY:
            {
                InitArrayInstruction iai = (InitArrayInstruction)instruction;
                ClassFile matchedClassFile = match(iai.newArray);
                if (matchedClassFile != null)
                    iai.newArray = newInstruction(matchedClassFile, iai.newArray);
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
        for (int index=instructions.size()-1; index>=0; --index)
        {
            Instruction i = instructions.get(index);
            ClassFile matchedClassFile = match(i);

            if (matchedClassFile != null)
                instructions.set(index, newInstruction(matchedClassFile, i));
            else
                visit(i);
        }
    }

    protected ClassFile match(Instruction instruction)
    {
        if (instruction.opcode != Const.INVOKESTATIC)
            return null;

        Invokestatic is = (Invokestatic)instruction;
        if (is.args.size() != 1)
            return null;

        ClassFile matchedClassFile = innerMatch(is.args.get(0));

        if ((matchedClassFile == null) || !matchedClassFile.isAInnerClass())
            return null;

        ConstantPool constants = classFile.getConstantPool();

        ConstantMethodref cmr =
            constants.getConstantMethodref(is.index);
        String className =
            constants.getConstantClassName(cmr.getClassIndex());

        if (!className.equals(matchedClassFile.getThisClassName()))
            return null;

        ConstantNameAndType cnat =
            constants.getConstantNameAndType(cmr.getNameAndTypeIndex());
        String methodName = constants.getConstantUtf8(cnat.getNameIndex());
        String methodDescriptor =
            constants.getConstantUtf8(cnat.getSignatureIndex());
        Method method =
            matchedClassFile.getMethod(methodName, methodDescriptor);

        if ((method == null) ||
            ((method.accessFlags & (Const.ACC_SYNTHETIC|Const.ACC_STATIC))
                != (Const.ACC_SYNTHETIC|Const.ACC_STATIC)))
            return null;

        ClassFile outerClassFile = matchedClassFile.getOuterClass();
        String returnedSignature = cmr.getReturnedSignature();

        if (!returnedSignature.equals(outerClassFile.getInternalClassName()))
            return null;

        return outerClassFile;
    }

    private ClassFile innerMatch(Instruction instruction)
    {
        switch (instruction.opcode)
        {
        case ByteCodeConstants.OUTERTHIS:
            {
                GetStatic gs = (GetStatic)instruction;
                ConstantPool constants = classFile.getConstantPool();

                ConstantFieldref cfr = constants.getConstantFieldref(gs.index);
                String className =
                    constants.getConstantClassName(cfr.getClassIndex());
                ClassFile outerClassFile = classFile.getOuterClass();

                if ((outerClassFile == null) ||
                    !className.equals(outerClassFile.getThisClassName()))
                    return null;

                ConstantNameAndType cnat =
                    constants.getConstantNameAndType(cfr.getNameAndTypeIndex());
                String descriptor =
                    constants.getConstantUtf8(cnat.getSignatureIndex());

                if (! descriptor.equals(outerClassFile.getInternalClassName()))
                    return null;

                return outerClassFile;
            }
        case Const.INVOKESTATIC:
            return match(instruction);
        default:
            return null;
        }
    }

    private Instruction newInstruction(ClassFile matchedClassFile, Instruction i)
    {
        String internalMatchedClassName =
            matchedClassFile.getInternalClassName();
        String matchedClassName = matchedClassFile.getThisClassName();

        ConstantPool constants = this.classFile.getConstantPool();

        int signatureIndex = constants.addConstantUtf8(matchedClassName);
        int classIndex = constants.addConstantClass(signatureIndex);
        int thisIndex = constants.thisLocalVariableNameIndex;
        int descriptorIndex =
            constants.addConstantUtf8(internalMatchedClassName);
        int nameAndTypeIndex = constants.addConstantNameAndType(
            thisIndex, descriptorIndex);

        int matchedThisFieldrefIndex =
            constants.addConstantFieldref(classIndex, nameAndTypeIndex);

        return new GetStatic(
            ByteCodeConstants.OUTERTHIS, i.offset,
            i.lineNumber, matchedThisFieldrefIndex);
    }
}
