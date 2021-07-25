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
package jd.core.process.analyzer.instruction.bytecode.factory;

import org.apache.bcel.Const;

import java.util.Deque;
import java.util.List;

import jd.core.model.classfile.ClassFile;
import jd.core.model.classfile.Method;
import jd.core.model.instruction.bytecode.ByteCodeConstants;
import jd.core.model.instruction.bytecode.instruction.Goto;
import jd.core.model.instruction.bytecode.instruction.Instruction;
import jd.core.model.instruction.bytecode.instruction.TernaryOpStore;
import jd.core.process.analyzer.instruction.bytecode.util.ByteCodeUtil;

public class GotoFactory implements InstructionFactory
{
    @Override
    public int create(
            ClassFile classFile, Method method, List<Instruction> list,
            List<Instruction> listForAnalyze,
            Deque<Instruction> stack, byte[] code, int offset,
            int lineNumber, boolean[] jumps)
    {
        final int opcode = code[offset] & 255;
        final int value  =
            (short)((code[offset+1] & 255) << 8 | code[offset+2] & 255);

        if (!stack.isEmpty() && !list.isEmpty()) {
			generateTernaryOpStore(
                list, listForAnalyze, stack, code, offset, value);
		}

        list.add(new Goto(opcode, offset, lineNumber, value));

        return Const.getNoOfOperands(opcode);
    }

    private static void generateTernaryOpStore(
        List<Instruction> list, List<Instruction> listForAnalyze,
        Deque<Instruction> stack, byte[] code, int offset, int value)
    {
        int i = list.size();

        while (i-- > 0)
        {
            Instruction previousInstruction = list.get(i);

            switch (previousInstruction.getOpcode())
            {
            case ByteCodeConstants.IF,
                 ByteCodeConstants.IFCMP,
                 ByteCodeConstants.IFXNULL:
                {
                    // Gestion de l'operateur ternaire
                    final int ternaryOp2ndValueOffset =
                        search2ndValueOffset(code, offset, offset+value);

                    final Instruction value0 = stack.pop();
                    TernaryOpStore tos = new TernaryOpStore(
                        ByteCodeConstants.TERNARYOPSTORE, offset-1,
                        value0.getLineNumber(), value0, ternaryOp2ndValueOffset);

                    list.add(tos);
                    listForAnalyze.add(tos);
                }
                return;
            }
        }
    }

    private static int search2ndValueOffset(
            byte[] code, int offset, int jumpOffset)
    {
        int result = offset;

        while (offset < jumpOffset)
        {
            int opcode = code[offset] & 255;
            // on retient l'offset de la derniere opération placant une
            // information sur la pile.
            switch (opcode)
            {
            case Const.ACONST_NULL,
                 Const.ICONST_M1,
                 Const.ICONST_0,
                 Const.ICONST_1,
                 Const.ICONST_2,
                 Const.ICONST_3,
                 Const.ICONST_4,
                 Const.ICONST_5,
                 Const.LCONST_0,
                 Const.LCONST_1,
                 Const.FCONST_0,
                 Const.FCONST_1,
                 Const.FCONST_2,
                 Const.DCONST_0,
                 Const.DCONST_1,
                 Const.BIPUSH,
                 Const.SIPUSH,
                 Const.LDC,
                 Const.LDC_W,
                 Const.LDC2_W,
                 Const.ILOAD,
                 Const.LLOAD,
                 Const.FLOAD,
                 Const.DLOAD,
                 Const.ALOAD,
                 Const.ILOAD_0,
                 Const.ILOAD_1,
                 Const.ILOAD_2,
                 Const.ILOAD_3,
                 Const.LLOAD_0,
                 Const.LLOAD_1,
                 Const.LLOAD_2,
                 Const.LLOAD_3,
                 Const.FLOAD_0,
                 Const.FLOAD_1,
                 Const.FLOAD_2,
                 Const.FLOAD_3,
                 Const.DLOAD_0,
                 Const.DLOAD_1,
                 Const.DLOAD_2,
                 Const.DLOAD_3,
                 Const.ALOAD_0,
                 Const.ALOAD_1,
                 Const.ALOAD_2,
                 Const.ALOAD_3,
                 Const.IALOAD,
                 Const.LALOAD,
                 Const.FALOAD,
                 Const.DALOAD,
                 Const.AALOAD,
                 Const.BALOAD,
                 Const.CALOAD,
                 Const.SALOAD,
                 Const.DUP,
                 Const.DUP_X1,
                 Const.DUP_X2,
                 Const.DUP2,
                 Const.DUP2_X1,
                 Const.DUP2_X2,
                 Const.SWAP,
                 Const.IADD,
                 Const.LADD,
                 Const.FADD,
                 Const.DADD,
                 Const.ISUB,
                 Const.LSUB,
                 Const.FSUB,
                 Const.DSUB,
                 Const.IMUL,
                 Const.LMUL,
                 Const.FMUL,
                 Const.DMUL,
                 Const.IDIV,
                 Const.LDIV,
                 Const.FDIV,
                 Const.DDIV,
                 Const.IREM,
                 Const.LREM,
                 Const.FREM,
                 Const.DREM,
                 Const.INEG,
                 Const.LNEG,
                 Const.FNEG,
                 Const.DNEG,
                 Const.ISHL,
                 Const.LSHL,
                 Const.ISHR,
                 Const.LSHR,
                 Const.IUSHR,
                 Const.LUSHR,
                 Const.IAND,
                 Const.LAND,
                 Const.IOR,
                 Const.LOR,
                 Const.IXOR,
                 Const.LXOR,
                 Const.IINC,
                 Const.I2L,
                 Const.I2F,
                 Const.I2D,
                 Const.L2I,
                 Const.L2F,
                 Const.L2D,
                 Const.F2I,
                 Const.F2L,
                 Const.F2D,
                 Const.D2I,
                 Const.D2L,
                 Const.D2F,
                 Const.I2B,
                 Const.I2C,
                 Const.I2S,
                 Const.LCMP,
                 Const.FCMPL,
                 Const.FCMPG,
                 Const.DCMPL,
                 Const.DCMPG,
                 Const.GETSTATIC,
                 ByteCodeConstants.OUTERTHIS,
                 Const.GETFIELD,
                 Const.INVOKEVIRTUAL,
                 Const.INVOKESPECIAL,
                 Const.INVOKESTATIC,
                 Const.INVOKEINTERFACE,
                 Const.NEW,
                 Const.NEWARRAY,
                 Const.ANEWARRAY,
                 Const.ARRAYLENGTH,
                 Const.CHECKCAST,
                 Const.INSTANCEOF,
                 Const.WIDE,
                 Const.MULTIANEWARRAY:
            // Extension for decompiler
            case ByteCodeConstants.ICONST,
                 ByteCodeConstants.LCONST,
                 ByteCodeConstants.FCONST,
                 ByteCodeConstants.DCONST,
                 ByteCodeConstants.DUPLOAD,
                 ByteCodeConstants.ASSIGNMENT,
                 ByteCodeConstants.UNARYOP,
                 ByteCodeConstants.BINARYOP,
                 ByteCodeConstants.LOAD,
                 ByteCodeConstants.EXCEPTIONLOAD,
                 ByteCodeConstants.ARRAYLOAD,
                 ByteCodeConstants.INVOKENEW,
                 ByteCodeConstants.CONVERT,
                 ByteCodeConstants.IMPLICITCONVERT,
                 ByteCodeConstants.PREINC,
                 ByteCodeConstants.POSTINC:
                result = offset;
            }

            int nbOfOperands = Const.getNoOfOperands(opcode);

            switch (nbOfOperands)
            {
            case Const.UNPREDICTABLE:
                switch (opcode)
                {
                case Const.TABLESWITCH:
                    offset = ByteCodeUtil.nextTableSwitchOffset(code, offset);
                    break;
                case Const.LOOKUPSWITCH:
                    offset = ByteCodeUtil.nextLookupSwitchOffset(code, offset);
                    break;
                case Const.WIDE:
                    offset = ByteCodeUtil.nextWideOffset(code, offset);
                }
                break;
            case Const.UNDEFINED:
                break;
            default:
                offset += nbOfOperands;
            }

            ++offset;
        }

        return result;
    }
}
