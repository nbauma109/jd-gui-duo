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

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import jd.core.model.instruction.bytecode.ByteCodeConstants;
import jd.core.model.instruction.bytecode.instruction.*;

public class CompareInstructionVisitor
{
    public boolean visit(Instruction i1, Instruction i2)
    {
        if (i1.opcode != i2.opcode)
            return false;

        switch (i1.opcode)
        {
        case Const.ARRAYLENGTH:
            return visit(
                ((ArrayLength)i1).arrayref, ((ArrayLength)i2).arrayref);
        case Const.AASTORE:
        case ByteCodeConstants.ARRAYSTORE:
            {
                if (Objects.compare(((ArrayStoreInstruction)i1).signature,
                        ((ArrayStoreInstruction)i2).signature, Comparator.naturalOrder()) != 0)
                    return false;

                if (! visit(
                        ((ArrayStoreInstruction)i1).arrayref,
                        ((ArrayStoreInstruction)i2).arrayref))
                    return false;

                if (! visit(
                        ((ArrayStoreInstruction)i1).indexref,
                        ((ArrayStoreInstruction)i2).indexref))
                    return false;

                return visit(
                        ((ArrayStoreInstruction)i1).valueref,
                        ((ArrayStoreInstruction)i2).valueref);
            }
        case ByteCodeConstants.ASSERT:
            {
                if (! visit(
                        ((AssertInstruction)i1).test,
                        ((AssertInstruction)i2).test))
                    return false;

                Instruction msg1 = ((AssertInstruction)i1).msg;
                Instruction msg2 = ((AssertInstruction)i2).msg;

                if (msg1 == msg2)
                    return true;
                if ((msg1 == null) || (msg2 == null))
                    return false;
                return visit(msg1, msg2);
            }
        case Const.ATHROW:
            return visit(((AThrow)i1).value, ((AThrow)i2).value);
        case ByteCodeConstants.UNARYOP:
            {
                if (((UnaryOperatorInstruction)i1).getPriority() !=
                    ((UnaryOperatorInstruction)i2).getPriority())
                    return false;

                if (((UnaryOperatorInstruction)i1).signature.compareTo(
                        ((UnaryOperatorInstruction)i2).signature) != 0)
                    return false;

                if (((UnaryOperatorInstruction)i1).operator.compareTo(
                        ((UnaryOperatorInstruction)i2).operator) != 0)
                    return false;

                return visit(
                    ((UnaryOperatorInstruction)i1).value,
                    ((UnaryOperatorInstruction)i2).value);
            }
        case ByteCodeConstants.BINARYOP:
            {
                if (((BinaryOperatorInstruction)i1).getPriority() !=
                    ((BinaryOperatorInstruction)i2).getPriority())
                    return false;

                if (((BinaryOperatorInstruction)i1).signature.compareTo(
                        ((BinaryOperatorInstruction)i2).signature) != 0)
                    return false;

                if (((BinaryOperatorInstruction)i1).operator.compareTo(
                        ((BinaryOperatorInstruction)i2).operator) != 0)
                    return false;

                if (! visit(
                        ((BinaryOperatorInstruction)i1).value1,
                        ((BinaryOperatorInstruction)i2).value1))
                    return false;

                return visit(
                    ((BinaryOperatorInstruction)i1).value2,
                    ((BinaryOperatorInstruction)i2).value2);
            }
        case Const.CHECKCAST:
            {
                if (((CheckCast)i1).index != ((CheckCast)i2).index)
                    return false;

                return visit(
                    ((CheckCast)i1).objectref, ((CheckCast)i2).objectref);
            }
        case ByteCodeConstants.STORE:
        case Const.ASTORE:
        case Const.ISTORE:
            {
                String rs1 = ((StoreInstruction)i1).getReturnedSignature(null, null);
                String rs2 = ((StoreInstruction)i2).getReturnedSignature(null, null);
                if ((rs1 == null) ? (rs2 != null) : (rs1.compareTo(rs2) != 0))
                    return false;

                return visit(
                    ((StoreInstruction)i1).valueref,
                    ((StoreInstruction)i2).valueref);
            }
        case ByteCodeConstants.DUPSTORE:
            return visit(
                ((DupStore)i1).objectref, ((DupStore)i2).objectref);
        case ByteCodeConstants.CONVERT:
        case ByteCodeConstants.IMPLICITCONVERT:
            {
                if (((ConvertInstruction)i1).signature.compareTo(
                        ((ConvertInstruction)i2).signature) != 0)
                    return false;

                return visit(
                    ((ConvertInstruction)i1).value,
                    ((ConvertInstruction)i2).value);
            }
        case ByteCodeConstants.IFCMP:
            {
                if (((IfCmp)i1).cmp != ((IfCmp)i2).cmp)
                    return false;

                // Ce test perturbe le retrait des instructions 'finally' dans
                // les blocs 'try' et 'catch'.
                //  if (((IfCmp)i1).branch != ((IfCmp)i2).branch)
                //	  return false;

                if (! visit(((IfCmp)i1).value1, ((IfCmp)i2).value1))
                    return false;

                return visit(((IfCmp)i1).value2, ((IfCmp)i2).value2);
            }
        case ByteCodeConstants.IF:
        case ByteCodeConstants.IFXNULL:
            {
                if (((IfInstruction)i1).cmp != ((IfInstruction)i2).cmp)
                    return false;

                return visit(
                    ((IfInstruction)i1).value, ((IfInstruction)i2).value);
            }
        case ByteCodeConstants.COMPLEXIF:
            {
                if (((ComplexConditionalBranchInstruction)i1).cmp != ((ComplexConditionalBranchInstruction)i2).cmp)
                    return false;

                if (((ComplexConditionalBranchInstruction)i1).branch != ((ComplexConditionalBranchInstruction)i2).branch)
                    return false;

                return visit(
                        ((ComplexConditionalBranchInstruction)i1).instructions,
                        ((ComplexConditionalBranchInstruction)i2).instructions);
            }
        case Const.INSTANCEOF:
            {
                if (((InstanceOf)i1).index != ((InstanceOf)i2).index)
                    return false;

                return visit(
                    ((InstanceOf)i1).objectref, ((InstanceOf)i2).objectref);
            }
        case Const.INVOKEINTERFACE:
        case Const.INVOKESPECIAL:
        case Const.INVOKEVIRTUAL:
            {
                if (! visit(
                        ((InvokeNoStaticInstruction)i1).objectref,
                        ((InvokeNoStaticInstruction)i2).objectref))
                    return false;
            }
            // intended fall through
        case Const.INVOKESTATIC:
            return visit(
                ((InvokeInstruction)i1).args, ((InvokeInstruction)i2).args);
        case ByteCodeConstants.INVOKENEW:
            {
                if (((InvokeNew)i1).index != ((InvokeNew)i2).index)
                    return false;

                return visit(
                    ((InvokeNew)i1).args, ((InvokeNew)i2).args);
            }
        case Const.MULTIANEWARRAY:
            {
                if (((MultiANewArray)i1).index != ((MultiANewArray)i2).index)
                    return false;

                Instruction[] dimensions1 = ((MultiANewArray)i1).dimensions;
                Instruction[] dimensions2 = ((MultiANewArray)i2).dimensions;

                if (dimensions1.length != dimensions2.length)
                    return false;

                for (int i=dimensions1.length-1; i>=0; --i)
                {
                    if (! visit(dimensions1[i], dimensions2[i]))
                        return false;
                }

                return true;
            }
        case Const.NEWARRAY:
            {
                if (((NewArray)i1).type != ((NewArray)i2).type)
                    return false;

                return visit(
                    ((NewArray)i1).dimension, ((NewArray)i2).dimension);
            }
        case Const.ANEWARRAY:
            {
                if (((ANewArray)i1).index != ((ANewArray)i2).index)
                    return false;

                return visit(
                    ((ANewArray)i1).dimension, ((ANewArray)i2).dimension);
            }
        case Const.PUTFIELD:
            {
                if (! visit(
                        ((PutField)i1).objectref,
                        ((PutField)i2).objectref))
                    return false;

                return visit(
                        ((PutField)i1).valueref, ((PutField)i2).valueref);
            }
        case ByteCodeConstants.TERNARYOPSTORE:
            {
                if (((TernaryOpStore)i1).ternaryOp2ndValueOffset-i1.offset !=
                    ((TernaryOpStore)i2).ternaryOp2ndValueOffset-i2.offset)
                    return false;

                return visit(
                    ((TernaryOpStore)i1).objectref,
                    ((TernaryOpStore)i2).objectref);
            }
        case ByteCodeConstants.TERNARYOP:
            {
                if (! visit(
                        ((TernaryOperator)i1).test,
                        ((TernaryOperator)i2).test))
                    return false;

                if (! visit(
                        ((TernaryOperator)i1).value1,
                        ((TernaryOperator)i2).value1))
                    return false;

                return visit(
                        ((TernaryOperator)i1).value2,
                        ((TernaryOperator)i2).value2);
            }
        case ByteCodeConstants.ASSIGNMENT:
            {
                if (((AssignmentInstruction)i1).getPriority() !=
                    ((AssignmentInstruction)i2).getPriority())
                    return false;

                if (((AssignmentInstruction)i1).operator.compareTo(
                        ((AssignmentInstruction)i2).operator) != 0)
                    return false;

                if (! visit(
                        ((AssignmentInstruction)i1).value1,
                        ((AssignmentInstruction)i2).value1))
                    return false;

                return visit(
                    ((AssignmentInstruction)i1).value2,
                    ((AssignmentInstruction)i2).value2);
            }
        case ByteCodeConstants.ARRAYLOAD:
            {
                String s1 = ((ArrayLoadInstruction)i1).getReturnedSignature(null, null);
                String s2 = ((ArrayLoadInstruction)i2).getReturnedSignature(null, null);

                if (s1 == null)
                {
                    if (s2 != null)
                        return false;
                }
                else
                {
                    if (s1.compareTo(s2) != 0)
                        return false;
                }

                if (! visit(
                        ((ArrayLoadInstruction)i1).arrayref,
                        ((ArrayLoadInstruction)i2).arrayref))
                    return false;

                return visit(
                    ((ArrayLoadInstruction)i1).indexref,
                    ((ArrayLoadInstruction)i2).indexref);
            }
        case ByteCodeConstants.PREINC:
        case ByteCodeConstants.POSTINC:
            {
                if (((IncInstruction)i1).count != ((IncInstruction)i2).count)
                    return false;

                return visit(
                        ((IncInstruction)i1).value,
                        ((IncInstruction)i2).value);
            }
        case Const.GETFIELD:
            {
                if (((GetField)i1).index != ((GetField)i2).index)
                    return false;

                return visit(
                    ((GetField)i1).objectref, ((GetField)i2).objectref);
            }
        case ByteCodeConstants.INITARRAY:
        case ByteCodeConstants.NEWANDINITARRAY:
            {
                if (! visit(
                        ((InitArrayInstruction)i1).newArray,
                        ((InitArrayInstruction)i2).newArray))
                    return false;

                return visit(((InitArrayInstruction)i1).values,
                        ((InitArrayInstruction)i2).values);
            }
        case Const.ALOAD:
        case Const.ILOAD:
            {
                String rs1 = ((LoadInstruction)i1).getReturnedSignature(null, null);
                String rs2 = ((LoadInstruction)i2).getReturnedSignature(null, null);
                return (rs1 == null) ? (rs2 == null) : (rs1.compareTo(rs2) == 0);
            }
        case ByteCodeConstants.ICONST:
        case Const.BIPUSH:
        case Const.SIPUSH:
            {
                if (((IConst)i1).value != ((IConst)i2).value)
                    return false;

                return
                    ((IConst)i1).signature.compareTo(
                        ((IConst)i2).signature) == 0;
            }
        case ByteCodeConstants.DCONST:
        case ByteCodeConstants.LCONST:
        case ByteCodeConstants.FCONST:
            return ((ConstInstruction)i1).value == ((ConstInstruction)i2).value;
        case ByteCodeConstants.DUPLOAD:
            return ((DupLoad)i1).dupStore == ((DupLoad)i2).dupStore;
        case Const.TABLESWITCH:
        case ByteCodeConstants.XRETURN:
        case Const.PUTSTATIC:
        case Const.LOOKUPSWITCH:
        case Const.MONITORENTER:
        case Const.MONITOREXIT:
        case Const.POP:
        case Const.ACONST_NULL:
        case ByteCodeConstants.LOAD:
        case Const.GETSTATIC:
        case ByteCodeConstants.OUTERTHIS:
        case Const.GOTO:
        case Const.IINC:
        case Const.JSR:
        case Const.LDC:
        case Const.LDC2_W:
        case Const.NEW:
        case Const.NOP:
        case Const.RET:
        case Const.RETURN:
        case ByteCodeConstants.EXCEPTIONLOAD:
        case ByteCodeConstants.RETURNADDRESSLOAD:
            return true;
        default:
            System.err.println(
                "Can not compare instruction " +
                i1.getClass().getName() + " and " + i2.getClass().getName());
            return false;
        }
    }

    protected boolean visit(
        List<Instruction> l1, List<Instruction> l2)
    {
        int i = l1.size();

        if (i != l2.size())
            return false;

        while (i-- > 0)
        {
            if (! visit(l1.get(i), l2.get(i)))
                return false;
        }

        return true;
    }
}
