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

import jd.core.model.classfile.ConstantPool;
import jd.core.model.instruction.bytecode.ByteCodeConstants;
import jd.core.model.instruction.bytecode.instruction.*;
import jd.core.util.SignatureUtil;

/*
 * Elimine les doubles casts et ajoute des casts devant les constantes
 * numeriques si necessaire.
 */
public class CheckCastAndConvertInstructionVisitor
{
    private CheckCastAndConvertInstructionVisitor() {
        super();
    }

    private static void visit(ConstantPool constants, Instruction instruction)
    {
        switch (instruction.opcode)
        {
        case Const.ARRAYLENGTH:
            visit(constants, ((ArrayLength)instruction).arrayref);
            break;
        case Const.AASTORE:
        case ByteCodeConstants.ARRAYSTORE:
            visit(constants, ((ArrayStoreInstruction)instruction).arrayref);
            break;
        case ByteCodeConstants.ASSERT:
            {
                AssertInstruction ai = (AssertInstruction)instruction;
                visit(constants, ai.test);
                if (ai.msg != null)
                    visit(constants, ai.msg);
            }
            break;
        case Const.ATHROW:
            visit(constants, ((AThrow)instruction).value);
            break;
        case ByteCodeConstants.UNARYOP:
            visit(constants, ((UnaryOperatorInstruction)instruction).value);
            break;
        case ByteCodeConstants.BINARYOP:
        case ByteCodeConstants.ASSIGNMENT:
            {
                BinaryOperatorInstruction boi =
                    (BinaryOperatorInstruction)instruction;
                visit(constants, boi.value1);
                visit(constants, boi.value2);
            }
            break;
        case Const.CHECKCAST:
            {
                CheckCast cc = (CheckCast)instruction;
                if (cc.objectref.opcode == Const.CHECKCAST)
                {
                    cc.objectref = ((CheckCast)cc.objectref).objectref;
                }
                visit(constants, cc.objectref);
            }
            break;
        case ByteCodeConstants.STORE:
        case Const.ASTORE:
        case Const.ISTORE:
            visit(constants, ((StoreInstruction)instruction).valueref);
            break;
        case ByteCodeConstants.DUPSTORE:
            visit(constants, ((DupStore)instruction).objectref);
            break;
        case ByteCodeConstants.CONVERT:
        case ByteCodeConstants.IMPLICITCONVERT:
            visit(constants, ((ConvertInstruction)instruction).value);
            break;
        case ByteCodeConstants.IFCMP:
            {
                IfCmp ifCmp = (IfCmp)instruction;
                visit(constants, ifCmp.value1);
                visit(constants, ifCmp.value2);
            }
            break;
        case ByteCodeConstants.IF:
        case ByteCodeConstants.IFXNULL:
            visit(constants, ((IfInstruction)instruction).value);
            break;
        case ByteCodeConstants.COMPLEXIF:
            {
                List<Instruction> branchList =
                    ((ComplexConditionalBranchInstruction)instruction).instructions;
                for (int i=branchList.size()-1; i>=0; --i)
                {
                    visit(constants, branchList.get(i));
                }
            }
            break;
        case Const.INSTANCEOF:
            visit(constants, ((InstanceOf)instruction).objectref);
            break;
        case Const.INVOKEINTERFACE:
        case Const.INVOKESPECIAL:
        case Const.INVOKEVIRTUAL:
            {
                visit(constants, ((InvokeNoStaticInstruction)instruction).objectref);
            }
            // intended fall through
        case Const.INVOKESTATIC:
        case ByteCodeConstants.INVOKENEW:
            {
                List<String> parameterSignatures =
                    ((InvokeInstruction)instruction).
                        getListOfParameterSignatures(constants);

                if (parameterSignatures != null)
                {
                    List<Instruction> args =
                        ((InvokeInstruction)instruction).args;
                    int i = parameterSignatures.size();
                    int j = args.size();

                    while ((i-- > 0) && (j-- > 0))
                    {
                        Instruction arg = args.get(j);

                        switch (arg.opcode)
                        {
                        case Const.SIPUSH:
                        case Const.BIPUSH:
                        case ByteCodeConstants.ICONST:
                            {
                                String argSignature = ((IConst)arg).getSignature();
                                String parameterSignature = parameterSignatures.get(i);

                                if (!parameterSignature.equals(argSignature))
                                {
                                    // Types differents
                                    int argBitFields =
                                            SignatureUtil.createArgOrReturnBitFields(argSignature);
                                    int paramBitFields =
                                            SignatureUtil.createTypesBitField(parameterSignature);

                                    if ((argBitFields|paramBitFields) == 0)
                                    {
                                        // Ajout d'une instruction cast si les types
                                        // sont differents
                                        args.set(j, new ConvertInstruction(
                                            ByteCodeConstants.CONVERT,
                                            arg.offset-1, arg.lineNumber,
                                            arg, parameterSignature));
                                    }
                                }
                                else
                                {
                                    switch (parameterSignature.charAt(0))
                                    {
                                    case 'B':
                                    case 'S':
                                        // Ajout d'une instruction cast pour les
                                        // parametres numeriques de type byte ou short
                                        args.set(j, new ConvertInstruction(
                                            ByteCodeConstants.CONVERT,
                                            arg.offset-1, arg.lineNumber,
                                            arg, parameterSignature));
                                        break;
                                    default:
                                        visit(constants, arg);
                                    }
                                }
                            }
                            break;
                        default:
                            visit(constants, arg);
                        }
                    }
                }
            }
            break;
        case Const.LOOKUPSWITCH:
            visit(constants, ((LookupSwitch)instruction).key);
            break;
        case Const.MONITORENTER:
            visit(constants, ((MonitorEnter)instruction).objectref);
            break;
        case Const.MONITOREXIT:
            visit(constants, ((MonitorExit)instruction).objectref);
            break;
        case Const.MULTIANEWARRAY:
            {
                Instruction[] dimensions = ((MultiANewArray)instruction).dimensions;
                for (int i=dimensions.length-1; i>=0; --i)
                {
                    visit(constants, dimensions[i]);
                }
            }
            break;
        case Const.NEWARRAY:
            visit(constants, ((NewArray)instruction).dimension);
            break;
        case Const.ANEWARRAY:
            visit(constants, ((ANewArray)instruction).dimension);
            break;
        case Const.POP:
            visit(constants, ((Pop)instruction).objectref);
            break;
        case Const.PUTFIELD:
            {
                PutField putField = (PutField)instruction;
                visit(constants, putField.objectref);
                visit(constants, putField.valueref);
            }
            break;
        case Const.PUTSTATIC:
            visit(constants, ((PutStatic)instruction).valueref);
            break;
        case ByteCodeConstants.XRETURN:
            visit(constants, ((ReturnInstruction)instruction).valueref);
            break;
        case Const.TABLESWITCH:
            visit(constants, ((TableSwitch)instruction).key);
            break;
        case ByteCodeConstants.TERNARYOPSTORE:
            visit(constants, ((TernaryOpStore)instruction).objectref);
            break;
        case ByteCodeConstants.PREINC:
        case ByteCodeConstants.POSTINC:
            visit(constants, ((IncInstruction)instruction).value);
            break;
        case Const.GETFIELD:
            visit(constants, ((GetField)instruction).objectref);
            break;
        case ByteCodeConstants.INITARRAY:
        case ByteCodeConstants.NEWANDINITARRAY:
            {
                InitArrayInstruction iai = (InitArrayInstruction)instruction;
                visit(constants, iai.newArray);
                if (iai.values != null)
                    visit(constants, iai.values);
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
                    "Can not check cast and convert instruction in " +
                    instruction.getClass().getName() +
                    ", opcode=" + instruction.opcode);
        }
    }

    public static void visit(
        ConstantPool constants, List<Instruction> instructions)
    {
        for (int i=instructions.size()-1; i>=0; --i)
        {
            visit(constants, instructions.get(i));
        }
    }
}
