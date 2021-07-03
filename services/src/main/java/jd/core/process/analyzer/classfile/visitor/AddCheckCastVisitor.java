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
package jd.core.process.analyzer.classfile.visitor;

import org.jd.core.v1.model.classfile.constant.ConstantClass;
import org.jd.core.v1.model.classfile.constant.ConstantFieldref;
import org.jd.core.v1.model.classfile.constant.ConstantMethodref;
import org.jd.core.v1.model.classfile.constant.ConstantNameAndType;
import org.jd.core.v1.util.StringConstants;

import java.util.List;

import jd.core.model.classfile.ConstantPool;
import jd.core.model.classfile.LocalVariable;
import jd.core.model.classfile.LocalVariables;
import jd.core.model.instruction.bytecode.ByteCodeConstants;
import jd.core.model.instruction.bytecode.instruction.*;
import jd.core.util.SignatureUtil;

/**
 * Ajout de 'cast' sur les instructions 'throw', 'astore', 'invokeXXX',
 * 'putfield', 'putstatic' et 'xreturn'.
 */
public class AddCheckCastVisitor
{
    private ConstantPool constants;
    private LocalVariables localVariables;
    private LocalVariable localVariable;

    public AddCheckCastVisitor(
            ConstantPool constants, LocalVariables localVariables,
            LocalVariable localVariable)
    {
        this.constants = constants;
        this.localVariables = localVariables;
        this.localVariable = localVariable;
    }

    public void visit(Instruction instruction)
    {
        switch (instruction.opcode)
        {
        case ByteCodeConstants.ARRAYLENGTH:
            {
                ArrayLength al = (ArrayLength)instruction;
                visit(al.arrayref);
            }
            break;
        case ByteCodeConstants.AASTORE:
        case ByteCodeConstants.ARRAYSTORE:
            {
                ArrayStoreInstruction asi = (ArrayStoreInstruction)instruction;
                visit(asi.arrayref);
                visit(asi.valueref);
            }
            break;
        case ByteCodeConstants.ASSERT:
            {
                AssertInstruction ai = (AssertInstruction)instruction;
                visit(ai.test);
                if (ai.msg != null) {
                    visit(ai.msg);
                }
            }
            break;
        case ByteCodeConstants.ATHROW:
            {
                AThrow aThrow = (AThrow)instruction;
                if (match(aThrow.value))
                {
                    LoadInstruction li = (LoadInstruction)aThrow.value;
                    LocalVariable lv =
                        this.localVariables.getLocalVariableWithIndexAndOffset(
                            li.index, li.offset);

                    if (lv.signatureIndex == this.constants.objectSignatureIndex)
                    {
                        // Add Throwable cast
                        int nameIndex = this.constants.addConstantUtf8(StringConstants.JAVA_LANG_THROWABLE);
                        int classIndex =
                            this.constants.addConstantClass(nameIndex);
                        Instruction i = aThrow.value;
                        aThrow.value = new CheckCast(
                            ByteCodeConstants.CHECKCAST, i.offset,
                            i.lineNumber, classIndex, i);
                    }
                }
                else
                {
                    visit(aThrow.value);
                }
            }
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
        case ByteCodeConstants.CHECKCAST:
            visit(((CheckCast)instruction).objectref);
            break;
        case ByteCodeConstants.STORE:
        case ByteCodeConstants.ISTORE:
            visit(((StoreInstruction)instruction).valueref);
            break;
        case ByteCodeConstants.ASTORE:
            {
                StoreInstruction storeInstruction = (StoreInstruction)instruction;
                if (match(storeInstruction.valueref))
                {
                    LocalVariable lv =
                        this.localVariables.getLocalVariableWithIndexAndOffset(
                            storeInstruction.index, storeInstruction.offset);

                    // AStore est associé à une variable correctment typée
                    if (lv.signatureIndex > 0 && lv.signatureIndex != this.constants.objectSignatureIndex)
                    {
                        String signature =
                            this.constants.getConstantUtf8(lv.signatureIndex);
                        storeInstruction.valueref = newInstruction(
                            signature, storeInstruction.valueref);
                    }
                }
                else
                {
                    visit(storeInstruction.valueref);
                }
            }
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
        case ByteCodeConstants.INSTANCEOF:
            visit(((InstanceOf)instruction).objectref);
            break;
        case ByteCodeConstants.INVOKEINTERFACE:
        case ByteCodeConstants.INVOKESPECIAL:
        case ByteCodeConstants.INVOKEVIRTUAL:
            {
                InvokeNoStaticInstruction insi =
                    (InvokeNoStaticInstruction)instruction;
                if (match(insi.objectref))
                {
                    ConstantMethodref cmr = this.constants.getConstantMethodref(insi.index);
                    ConstantClass cc = this.constants.getConstantClass(cmr.getClassIndex());

                    if (this.constants.objectClassNameIndex != cc.getNameIndex())
                    {
                        Instruction i = insi.objectref;
                        insi.objectref = new CheckCast(
                            ByteCodeConstants.CHECKCAST, i.offset,
                            i.lineNumber, cmr.getClassIndex(), i);
                    }
                }
                else
                {
                    visit(insi.objectref);
                }
            }
            // intended fall through
        case ByteCodeConstants.INVOKESTATIC:
            {
                List<Instruction> list = ((InvokeInstruction)instruction).args;
                List<String> types = ((InvokeInstruction)instruction)
                    .getListOfParameterSignatures(this.constants);

                Instruction arg;
                for (int i=list.size()-1; i>=0; --i)
                {
                    arg = list.get(i);
                    if (match(arg))
                    {
                        String signature = types.get(i);

                        if (! StringConstants.INTERNAL_OBJECT_SIGNATURE.equals(signature))
                        {
                            list.set(i, newInstruction(signature, arg));
                        }
                    }
                    else
                    {
                        visit(arg);
                    }
                }
            }
            break;
        case ByteCodeConstants.LOOKUPSWITCH:
            visit(((LookupSwitch)instruction).key);
            break;
        case ByteCodeConstants.MONITORENTER:
            visit(((MonitorEnter)instruction).objectref);
            break;
        case ByteCodeConstants.MONITOREXIT:
            visit(((MonitorExit)instruction).objectref);
            break;
        case ByteCodeConstants.MULTIANEWARRAY:
            {
                Instruction[] dimensions = ((MultiANewArray)instruction).dimensions;
                for (int i=dimensions.length-1; i>=0; --i) {
                    visit(dimensions[i]);
                }
            }
            break;
        case ByteCodeConstants.NEWARRAY:
            visit(((NewArray)instruction).dimension);
            break;
        case ByteCodeConstants.ANEWARRAY:
            visit(((ANewArray)instruction).dimension);
            break;
        case ByteCodeConstants.POP:
            visit(((Pop)instruction).objectref);
            break;
        case ByteCodeConstants.GETFIELD:
            {
                GetField getField = (GetField)instruction;
                if (match(getField.objectref))
                {
                    ConstantFieldref cfr =
                        this.constants.getConstantFieldref(getField.index);
                    ConstantClass cc = this.constants.getConstantClass(cfr.getClassIndex());

                    if (this.constants.objectClassNameIndex != cc.getNameIndex())
                    {
                        Instruction i = getField.objectref;
                        getField.objectref = new CheckCast(
                            ByteCodeConstants.CHECKCAST, i.offset,
                            i.lineNumber, cfr.getClassIndex(), i);
                    }
                }
                else
                {
                    visit(getField.objectref);
                }
            }
            break;
        case ByteCodeConstants.PUTFIELD:
            {
                PutField putField = (PutField)instruction;
                if (match(putField.objectref))
                {
                    ConstantFieldref cfr =
                        this.constants.getConstantFieldref(putField.index);
                    ConstantClass cc = this.constants.getConstantClass(cfr.getClassIndex());

                    if (this.constants.objectClassNameIndex != cc.getNameIndex())
                    {
                        Instruction i = putField.objectref;
                        putField.objectref = new CheckCast(
                            ByteCodeConstants.CHECKCAST, i.offset,
                            i.lineNumber, cfr.getClassIndex(), i);
                    }
                }
                else
                {
                    visit(putField.objectref);
                }
                if (match(putField.valueref))
                {
                    ConstantFieldref cfr = constants.getConstantFieldref(putField.index);
                    ConstantNameAndType cnat =
                        constants.getConstantNameAndType(cfr.getNameAndTypeIndex());

                    if (cnat.getDescriptorIndex() != this.constants.objectSignatureIndex)
                    {
                        String signature =
                            this.constants.getConstantUtf8(cnat.getDescriptorIndex());
                        putField.valueref = newInstruction(
                            signature, putField.valueref);
                    }
                }
                else
                {
                    visit(putField.valueref);
                }
            }
            break;
        case ByteCodeConstants.PUTSTATIC:
            {
                PutStatic putStatic = (PutStatic)instruction;
                if (match(putStatic.valueref))
                {
                    ConstantFieldref cfr = constants.getConstantFieldref(putStatic.index);
                    ConstantNameAndType cnat =
                        constants.getConstantNameAndType(cfr.getNameAndTypeIndex());

                    if (cnat.getDescriptorIndex() != this.constants.objectSignatureIndex)
                    {
                        String signature =
                            this.constants.getConstantUtf8(cnat.getDescriptorIndex());
                        putStatic.valueref = newInstruction(
                            signature, putStatic.valueref);
                    }
                }
                else
                {
                    visit(putStatic.valueref);
                }
            }
            break;
        case ByteCodeConstants.XRETURN:
            visit(((ReturnInstruction)instruction).valueref);
            break;
        case ByteCodeConstants.TABLESWITCH:
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
        case ByteCodeConstants.GETSTATIC:
        case ByteCodeConstants.OUTERTHIS:
        case ByteCodeConstants.GOTO:
        case ByteCodeConstants.INVOKENEW:
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
        case ByteCodeConstants.IINC:
        case ByteCodeConstants.PREINC:
        case ByteCodeConstants.POSTINC:
            break;
        default:
            System.err.println(
                    "Can not add cast in " +
                    instruction.getClass().getName() +
                    ", opcode=" + instruction.opcode);
        }
    }

    private boolean match(Instruction i)
    {
        if (i.opcode == ByteCodeConstants.ALOAD)
        {
            LoadInstruction li = (LoadInstruction)i;
            if (li.index == this.localVariable.index)
            {
                LocalVariable lv =
                    this.localVariables.getLocalVariableWithIndexAndOffset(
                            li.index, li.offset);
                return lv == this.localVariable;
            }
        }

        return false;
    }

    private Instruction newInstruction(String signature, Instruction i)
    {
        if (SignatureUtil.isPrimitiveSignature(signature))
        {
            return new ConvertInstruction(
                ByteCodeConstants.CONVERT, i.offset,
                i.lineNumber, i, signature);
        }
        int nameIndex;
        if (signature.charAt(0) == 'L')
        {
            String name = SignatureUtil.getInnerName(signature);
            nameIndex = this.constants.addConstantUtf8(name);
        }
        else
        {
            nameIndex = this.constants.addConstantUtf8(signature);
        }
        int classIndex =
            this.constants.addConstantClass(nameIndex);
        return new CheckCast(
            ByteCodeConstants.CHECKCAST, i.offset,
            i.lineNumber, classIndex, i);
    }
}
