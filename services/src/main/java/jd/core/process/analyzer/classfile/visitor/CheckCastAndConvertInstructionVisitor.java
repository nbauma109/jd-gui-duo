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
        switch (instruction.getOpcode())
        {
        case Const.ARRAYLENGTH:
            visit(constants, ((ArrayLength)instruction).getArrayref());
            break;
        case Const.AASTORE,
             ByteCodeConstants.ARRAYSTORE:
            visit(constants, ((ArrayStoreInstruction)instruction).getArrayref());
            break;
        case ByteCodeConstants.ASSERT:
            {
                AssertInstruction ai = (AssertInstruction)instruction;
                visit(constants, ai.getTest());
                if (ai.getMsg() != null)
                    visit(constants, ai.getMsg());
            }
            break;
        case Const.ATHROW:
            visit(constants, ((AThrow)instruction).getValue());
            break;
        case ByteCodeConstants.UNARYOP:
            visit(constants, ((UnaryOperatorInstruction)instruction).getValue());
            break;
        case ByteCodeConstants.BINARYOP,
             ByteCodeConstants.ASSIGNMENT:
            {
                BinaryOperatorInstruction boi =
                    (BinaryOperatorInstruction)instruction;
                visit(constants, boi.getValue1());
                visit(constants, boi.getValue2());
            }
            break;
        case Const.CHECKCAST:
            {
                CheckCast cc = (CheckCast)instruction;
                if (cc.getObjectref().getOpcode() == Const.CHECKCAST)
                {
                    cc.setObjectref(((CheckCast)cc.getObjectref()).getObjectref());
                }
                visit(constants, cc.getObjectref());
            }
            break;
        case ByteCodeConstants.STORE,
             Const.ASTORE,
             Const.ISTORE:
            visit(constants, ((StoreInstruction)instruction).getValueref());
            break;
        case ByteCodeConstants.DUPSTORE:
            visit(constants, ((DupStore)instruction).getObjectref());
            break;
        case ByteCodeConstants.CONVERT,
             ByteCodeConstants.IMPLICITCONVERT:
            visit(constants, ((ConvertInstruction)instruction).getValue());
            break;
        case ByteCodeConstants.IFCMP:
            {
                IfCmp ifCmp = (IfCmp)instruction;
                visit(constants, ifCmp.getValue1());
                visit(constants, ifCmp.getValue2());
            }
            break;
        case ByteCodeConstants.IF,
             ByteCodeConstants.IFXNULL:
            visit(constants, ((IfInstruction)instruction).getValue());
            break;
        case ByteCodeConstants.COMPLEXIF:
            {
                List<Instruction> branchList =
                    ((ComplexConditionalBranchInstruction)instruction).getInstructions();
                for (int i=branchList.size()-1; i>=0; --i)
                {
                    visit(constants, branchList.get(i));
                }
            }
            break;
        case Const.INSTANCEOF:
            visit(constants, ((InstanceOf)instruction).getObjectref());
            break;
        case Const.INVOKEINTERFACE,
             Const.INVOKESPECIAL,
             Const.INVOKEVIRTUAL:
            {
                visit(constants, ((InvokeNoStaticInstruction)instruction).getObjectref());
            }
            // intended fall through
        case Const.INVOKESTATIC,
             ByteCodeConstants.INVOKENEW:
            {
                List<String> parameterSignatures =
                    ((InvokeInstruction)instruction).
                        getListOfParameterSignatures(constants);

                if (parameterSignatures != null)
                {
                    List<Instruction> args =
                        ((InvokeInstruction)instruction).getArgs();
                    int i = parameterSignatures.size();
                    int j = args.size();

                    while ((i-- > 0) && (j-- > 0))
                    {
                        Instruction arg = args.get(j);

                        switch (arg.getOpcode())
                        {
                        case Const.SIPUSH,
                             Const.BIPUSH,
                             ByteCodeConstants.ICONST:
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
                                            arg.getOffset()-1, arg.getLineNumber(),
                                            arg, parameterSignature));
                                    }
                                }
                                else
                                {
                                    switch (parameterSignature.charAt(0))
                                    {
                                    case 'B', 'S':
                                        // Ajout d'une instruction cast pour les
                                        // parametres numeriques de type byte ou short
                                        args.set(j, new ConvertInstruction(
                                            ByteCodeConstants.CONVERT,
                                            arg.getOffset()-1, arg.getLineNumber(),
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
            visit(constants, ((LookupSwitch)instruction).getKey());
            break;
        case Const.MONITORENTER:
            visit(constants, ((MonitorEnter)instruction).getObjectref());
            break;
        case Const.MONITOREXIT:
            visit(constants, ((MonitorExit)instruction).getObjectref());
            break;
        case Const.MULTIANEWARRAY:
            {
                Instruction[] dimensions = ((MultiANewArray)instruction).getDimensions();
                for (int i=dimensions.length-1; i>=0; --i)
                {
                    visit(constants, dimensions[i]);
                }
            }
            break;
        case Const.NEWARRAY:
            visit(constants, ((NewArray)instruction).getDimension());
            break;
        case Const.ANEWARRAY:
            visit(constants, ((ANewArray)instruction).getDimension());
            break;
        case Const.POP:
            visit(constants, ((Pop)instruction).getObjectref());
            break;
        case Const.PUTFIELD:
            {
                PutField putField = (PutField)instruction;
                visit(constants, putField.getObjectref());
                visit(constants, putField.getValueref());
            }
            break;
        case Const.PUTSTATIC:
            visit(constants, ((PutStatic)instruction).getValueref());
            break;
        case ByteCodeConstants.XRETURN:
            visit(constants, ((ReturnInstruction)instruction).getValueref());
            break;
        case Const.TABLESWITCH:
            visit(constants, ((TableSwitch)instruction).getKey());
            break;
        case ByteCodeConstants.TERNARYOPSTORE:
            visit(constants, ((TernaryOpStore)instruction).getObjectref());
            break;
        case ByteCodeConstants.PREINC,
             ByteCodeConstants.POSTINC:
            visit(constants, ((IncInstruction)instruction).getValue());
            break;
        case Const.GETFIELD:
            visit(constants, ((GetField)instruction).getObjectref());
            break;
        case ByteCodeConstants.INITARRAY,
             ByteCodeConstants.NEWANDINITARRAY:
            {
                InitArrayInstruction iai = (InitArrayInstruction)instruction;
                visit(constants, iai.getNewArray());
                if (iai.getValues() != null)
                    visit(constants, iai.getValues());
            }
            break;
        case Const.ACONST_NULL,
             ByteCodeConstants.ARRAYLOAD,
             ByteCodeConstants.LOAD,
             Const.ALOAD,
             Const.ILOAD,
             Const.BIPUSH,
             ByteCodeConstants.ICONST,
             ByteCodeConstants.LCONST,
             ByteCodeConstants.FCONST,
             ByteCodeConstants.DCONST,
             ByteCodeConstants.DUPLOAD,
             Const.GETSTATIC,
             ByteCodeConstants.OUTERTHIS,
             Const.GOTO,
             Const.IINC,
             Const.JSR,
             Const.LDC,
             Const.LDC2_W,
             Const.NEW,
             Const.NOP,
             Const.SIPUSH,
             Const.RET,
             Const.RETURN,
             ByteCodeConstants.EXCEPTIONLOAD,
             ByteCodeConstants.RETURNADDRESSLOAD:
            break;
        default:
            System.err.println(
                    "Can not check cast and convert instruction in " +
                    instruction.getClass().getName() +
                    ", opcode=" + instruction.getOpcode());
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
