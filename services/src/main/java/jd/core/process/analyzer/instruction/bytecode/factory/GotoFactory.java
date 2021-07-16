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

public class GotoFactory extends InstructionFactory
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
            (short)(((code[offset+1] & 255) << 8) | (code[offset+2] & 255));

        if (!stack.isEmpty() && !list.isEmpty())
            generateTernaryOpStore(
                list, listForAnalyze, stack, code, offset, value);

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

            switch (previousInstruction.opcode)
            {
            case ByteCodeConstants.IF:
            case ByteCodeConstants.IFCMP:
            case ByteCodeConstants.IFXNULL:
                {
                    // Gestion de l'operateur ternaire
                    final int ternaryOp2ndValueOffset =
                        search2ndValueOffset(code, offset, offset+value);

                    final Instruction value0 = stack.pop();
                    TernaryOpStore tos = new TernaryOpStore(
                        ByteCodeConstants.TERNARYOPSTORE, offset-1,
                        value0.lineNumber, value0, ternaryOp2ndValueOffset);

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
            case Const.ACONST_NULL:
            case Const.ICONST_M1:
            case Const.ICONST_0:
            case Const.ICONST_1:
            case Const.ICONST_2:
            case Const.ICONST_3:
            case Const.ICONST_4:
            case Const.ICONST_5:
            case Const.LCONST_0:
            case Const.LCONST_1:
            case Const.FCONST_0:
            case Const.FCONST_1:
            case Const.FCONST_2:
            case Const.DCONST_0:
            case Const.DCONST_1:
            case Const.BIPUSH:
            case Const.SIPUSH:
            case Const.LDC:
            case Const.LDC_W:
            case Const.LDC2_W:
            case Const.ILOAD:
            case Const.LLOAD:
            case Const.FLOAD:
            case Const.DLOAD:
            case Const.ALOAD:
            case Const.ILOAD_0:
            case Const.ILOAD_1:
            case Const.ILOAD_2:
            case Const.ILOAD_3:
            case Const.LLOAD_0:
            case Const.LLOAD_1:
            case Const.LLOAD_2:
            case Const.LLOAD_3:
            case Const.FLOAD_0:
            case Const.FLOAD_1:
            case Const.FLOAD_2:
            case Const.FLOAD_3:
            case Const.DLOAD_0:
            case Const.DLOAD_1:
            case Const.DLOAD_2:
            case Const.DLOAD_3:
            case Const.ALOAD_0:
            case Const.ALOAD_1:
            case Const.ALOAD_2:
            case Const.ALOAD_3:
            case Const.IALOAD:
            case Const.LALOAD:
            case Const.FALOAD:
            case Const.DALOAD:
            case Const.AALOAD:
            case Const.BALOAD:
            case Const.CALOAD:
            case Const.SALOAD:
            case Const.DUP:
            case Const.DUP_X1:
            case Const.DUP_X2:
            case Const.DUP2:
            case Const.DUP2_X1:
            case Const.DUP2_X2:
            case Const.SWAP:
            case Const.IADD:
            case Const.LADD:
            case Const.FADD:
            case Const.DADD:
            case Const.ISUB:
            case Const.LSUB:
            case Const.FSUB:
            case Const.DSUB:
            case Const.IMUL:
            case Const.LMUL:
            case Const.FMUL:
            case Const.DMUL:
            case Const.IDIV:
            case Const.LDIV:
            case Const.FDIV:
            case Const.DDIV:
            case Const.IREM:
            case Const.LREM:
            case Const.FREM:
            case Const.DREM:
            case Const.INEG:
            case Const.LNEG:
            case Const.FNEG:
            case Const.DNEG:
            case Const.ISHL:
            case Const.LSHL:
            case Const.ISHR:
            case Const.LSHR:
            case Const.IUSHR:
            case Const.LUSHR:
            case Const.IAND:
            case Const.LAND:
            case Const.IOR:
            case Const.LOR:
            case Const.IXOR:
            case Const.LXOR:
            case Const.IINC:
            case Const.I2L:
            case Const.I2F:
            case Const.I2D:
            case Const.L2I:
            case Const.L2F:
            case Const.L2D:
            case Const.F2I:
            case Const.F2L:
            case Const.F2D:
            case Const.D2I:
            case Const.D2L:
            case Const.D2F:
            case Const.I2B:
            case Const.I2C:
            case Const.I2S:
            case Const.LCMP:
            case Const.FCMPL:
            case Const.FCMPG:
            case Const.DCMPL:
            case Const.DCMPG:
            case Const.GETSTATIC:
            case ByteCodeConstants.OUTERTHIS:
            case Const.GETFIELD:
            case Const.INVOKEVIRTUAL:
            case Const.INVOKESPECIAL:
            case Const.INVOKESTATIC:
            case Const.INVOKEINTERFACE:
            case Const.NEW:
            case Const.NEWARRAY:
            case Const.ANEWARRAY:
            case Const.ARRAYLENGTH:
            case Const.CHECKCAST:
            case Const.INSTANCEOF:
            case Const.WIDE:
            case Const.MULTIANEWARRAY:
            // Extension for decompiler
            case ByteCodeConstants.ICONST:
            case ByteCodeConstants.LCONST:
            case ByteCodeConstants.FCONST:
            case ByteCodeConstants.DCONST:
            case ByteCodeConstants.DUPLOAD:
            case ByteCodeConstants.ASSIGNMENT:
            case ByteCodeConstants.UNARYOP:
            case ByteCodeConstants.BINARYOP:
            case ByteCodeConstants.LOAD:
            case ByteCodeConstants.EXCEPTIONLOAD:
            case ByteCodeConstants.ARRAYLOAD:
            case ByteCodeConstants.INVOKENEW:
            case ByteCodeConstants.CONVERT:
            case ByteCodeConstants.IMPLICITCONVERT:
            case ByteCodeConstants.PREINC:
            case ByteCodeConstants.POSTINC:
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
