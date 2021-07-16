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
package jd.core.process.analyzer.classfile.reconstructor;

import org.apache.bcel.Const;

import java.util.List;

import jd.core.model.instruction.bytecode.ByteCodeConstants;
import jd.core.model.instruction.bytecode.instruction.*;
import jd.core.process.analyzer.util.ReconstructorUtil;

/*
 * Recontruction des post-incrementations depuis le motif :
 * DupStore( i )
 * ...
 * {?Store | PutField | PutStatic}( DupLoad +/- 1 )
 * ...
 * ???( DupLoad )
 */
public class PostIncReconstructor
{
    private PostIncReconstructor() {
        super();
    }

    public static void reconstruct(List<Instruction> list)
    {
        int length = list.size();

        for (int dupStoreIndex=0; dupStoreIndex<length; dupStoreIndex++)
        {
            if (list.get(dupStoreIndex).opcode != ByteCodeConstants.DUPSTORE)
                continue;

            // DupStore trouvé
            DupStore dupstore = (DupStore)list.get(dupStoreIndex);

            int xstorePutfieldPutstaticIndex = dupStoreIndex;

            while (++xstorePutfieldPutstaticIndex < length)
            {
                Instruction i = list.get(xstorePutfieldPutstaticIndex);
                BinaryOperatorInstruction boi = null;

                switch (i.opcode)
                {
                case Const.ASTORE:
                    if ((dupstore.objectref.opcode == Const.ALOAD) &&
                        (((IndexInstruction)i).index == ((IndexInstruction)dupstore.objectref).index))
                    {
                        i = ((StoreInstruction)i).valueref;
                        if ((i.opcode == ByteCodeConstants.CONVERT) || (i.opcode == ByteCodeConstants.IMPLICITCONVERT))
                            i = ((ConvertInstruction)i).value;
                        if (i.opcode == ByteCodeConstants.BINARYOP)
                            boi = (BinaryOperatorInstruction)i;
                    }
                    break;
                case Const.ISTORE:
                    if ((dupstore.objectref.opcode == Const.ILOAD) &&
                        (((IndexInstruction)i).index == ((IndexInstruction)dupstore.objectref).index))
                    {
                        i = ((StoreInstruction)i).valueref;
                        if ((i.opcode == ByteCodeConstants.CONVERT) || (i.opcode == ByteCodeConstants.IMPLICITCONVERT))
                            i = ((ConvertInstruction)i).value;
                        if (i.opcode == ByteCodeConstants.BINARYOP)
                            boi = (BinaryOperatorInstruction)i;
                    }
                    break;
                case ByteCodeConstants.STORE:
                    if ((dupstore.objectref.opcode == ByteCodeConstants.LOAD) &&
                        (((IndexInstruction)i).index == ((IndexInstruction)dupstore.objectref).index))
                    {
                        i = ((StoreInstruction)i).valueref;
                        if ((i.opcode == ByteCodeConstants.CONVERT) || (i.opcode == ByteCodeConstants.IMPLICITCONVERT))
                            i = ((ConvertInstruction)i).value;
                        if (i.opcode == ByteCodeConstants.BINARYOP)
                            boi = (BinaryOperatorInstruction)i;
                    }
                    break;
                case Const.PUTFIELD:
                    if ((dupstore.objectref.opcode == Const.GETFIELD) &&
                        (((IndexInstruction)i).index == ((IndexInstruction)dupstore.objectref).index))
                    {
                        i = ((PutField)i).valueref;
                        if ((i.opcode == ByteCodeConstants.CONVERT) || (i.opcode == ByteCodeConstants.IMPLICITCONVERT))
                            i = ((ConvertInstruction)i).value;
                        if (i.opcode == ByteCodeConstants.BINARYOP)
                            boi = (BinaryOperatorInstruction)i;
                    }
                    break;
                case Const.PUTSTATIC:
                    if ((dupstore.objectref.opcode == Const.GETSTATIC) &&
                        (((IndexInstruction)i).index == ((IndexInstruction)dupstore.objectref).index))
                    {
                        i = ((PutStatic)i).valueref;
                        if ((i.opcode == ByteCodeConstants.CONVERT) || (i.opcode == ByteCodeConstants.IMPLICITCONVERT))
                            i = ((ConvertInstruction)i).value;
                        if (i.opcode == ByteCodeConstants.BINARYOP)
                            boi = (BinaryOperatorInstruction)i;
                    }
                    break;
                }

                if ((boi == null) ||
                    (boi.value1.opcode != ByteCodeConstants.DUPLOAD) ||
                    (boi.value1.offset != dupstore.offset) ||
                    ((boi.value2.opcode != ByteCodeConstants.ICONST) &&
                     (boi.value2.opcode != ByteCodeConstants.LCONST) &&
                     (boi.value2.opcode != ByteCodeConstants.DCONST) &&
                     (boi.value2.opcode != ByteCodeConstants.FCONST)))
                    continue;

                ConstInstruction ci = (ConstInstruction)boi.value2;

                if (ci.value != 1)
                    continue;

                int value;

                if (boi.operator.equals("+"))
                    value = 1;
                else if (boi.operator.equals("-"))
                    value = -1;
                else
                    continue;

                Instruction inc = new IncInstruction(
                    ByteCodeConstants.POSTINC, boi.offset, boi.lineNumber,
                    dupstore.objectref, value);

                ReconstructorUtil.replaceDupLoad(
                        list, xstorePutfieldPutstaticIndex+1, dupstore, inc);

                list.remove(xstorePutfieldPutstaticIndex);
                list.remove(dupStoreIndex);
                dupStoreIndex--;
                length = list.size();
                break;
            }
        }
    }
}
