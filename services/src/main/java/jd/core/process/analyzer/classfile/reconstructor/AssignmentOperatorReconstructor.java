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
import jd.core.process.analyzer.classfile.visitor.CompareInstructionVisitor;

/*
 * Recontruction des operateurs d'assignation depuis les motifs :
 * 1) Operation sur les attributs de classes:
 *    PutStatic(BinaryOperator(GetStatic(), ...))
 * 2) Operation sur les attributs d'instance:
 *    PutField(objectref, BinaryOperator(GetField(objectref), ...))
 * 3) Operation sur les variables locales:
 *    Store(BinaryOperator(Load(), ...))
 * 4) Operation sur les variables locales:
 *    IStore(BinaryOperator(ILoad(), ...))
 * 5) Operation sur des tableaux:
 *    ArrayStore(arrayref, indexref,
 *               BinaryOperator(ArrayLoad(arrayref, indexref), ...))
 */
public class AssignmentOperatorReconstructor
{
    private AssignmentOperatorReconstructor() {
        super();
    }

    public static void reconstruct(List<Instruction> list)
    {
        int index = list.size();

        while (index-- > 0)
        {
            Instruction i = list.get(index);

            switch (i.opcode)
            {
            case Const.PUTSTATIC:
                if (((PutStatic)i).valueref.opcode ==
                		ByteCodeConstants.BINARYOP)
                    reconstructPutStaticOperator(list, index, i);
                break;
            case Const.PUTFIELD:
                if (((PutField)i).valueref.opcode ==
                		ByteCodeConstants.BINARYOP)
                    index = reconstructPutFieldOperator(list, index, i);
                break;
            case Const.ISTORE:
                if (((StoreInstruction)i).valueref.opcode ==
                		ByteCodeConstants.BINARYOP)
                {
                    BinaryOperatorInstruction boi = (BinaryOperatorInstruction)
                        ((StoreInstruction)i).valueref;
                    if (boi.value1.opcode == Const.ILOAD)
                        reconstructStoreOperator(list, index, i, boi);
                }
                break;
            case ByteCodeConstants.STORE:
                if (((StoreInstruction)i).valueref.opcode ==
                		ByteCodeConstants.BINARYOP)
                {
                    BinaryOperatorInstruction boi = (BinaryOperatorInstruction)
                        ((StoreInstruction)i).valueref;
                    if (boi.value1.opcode == ByteCodeConstants.LOAD)
                        reconstructStoreOperator(list, index, i, boi);
                }
                break;
            case ByteCodeConstants.ARRAYSTORE:
                if (((ArrayStoreInstruction)i).valueref.opcode ==
                		ByteCodeConstants.BINARYOP)
                    index = reconstructArrayOperator(list, index, i);
                break;
            }
        }
    }

    /*
     * PutStatic(BinaryOperator(GetStatic(), ...))
     */
    private static void reconstructPutStaticOperator(
        List<Instruction> list, int index, Instruction i)
    {
        PutStatic putStatic = (PutStatic)i;
        BinaryOperatorInstruction boi =
            (BinaryOperatorInstruction)putStatic.valueref;

        if (boi.value1.opcode != Const.GETSTATIC)
            return;

        GetStatic getStatic = (GetStatic)boi.value1;

        if ((putStatic.lineNumber != getStatic.lineNumber) ||
            (putStatic.index != getStatic.index))
            return;

        String newOperator = boi.operator + "=";

        list.set(index, new AssignmentInstruction(
            ByteCodeConstants.ASSIGNMENT, putStatic.offset,
            getStatic.lineNumber, boi.getPriority(), newOperator,
            getStatic, boi.value2));

    }

    /*
     * PutField(objectref, BinaryOperator(GetField(objectref), ...))
     */
    private static int reconstructPutFieldOperator(
        List<Instruction> list, int index, Instruction i)
    {
        PutField putField = (PutField)i;
        BinaryOperatorInstruction boi =
            (BinaryOperatorInstruction)putField.valueref;

        if (boi.value1.opcode != Const.GETFIELD)
            return index;

        GetField getField = (GetField)boi.value1;
        CompareInstructionVisitor visitor = new CompareInstructionVisitor();

        if ((putField.lineNumber != getField.lineNumber) ||
            (putField.index != getField.index) ||
            !visitor.visit(putField.objectref, getField.objectref))
            return index;

        if (putField.objectref.opcode == ByteCodeConstants.DUPLOAD)
        {
            // Remove DupStore & DupLoad
            DupLoad dupLoad = (DupLoad)getField.objectref;
            index = deleteDupStoreInstruction(list, index, dupLoad);
            getField.objectref = dupLoad.dupStore.objectref;
        }

        String newOperator = boi.operator + "=";

        list.set(index, new AssignmentInstruction(
            ByteCodeConstants.ASSIGNMENT, putField.offset,
            getField.lineNumber, boi.getPriority(), newOperator,
            getField, boi.value2));

        return index;
    }

    /*
     * StoreInstruction(BinaryOperator(LoadInstruction(), ...))
     */
    private static void reconstructStoreOperator(
        List<Instruction> list, int index,
        Instruction i, BinaryOperatorInstruction boi)
    {
        StoreInstruction si = (StoreInstruction)i;
        LoadInstruction li = (LoadInstruction)boi.value1;

        if ((si.lineNumber != li.lineNumber) || (si.index != li.index))
            return;

        String newOperator = boi.operator + "=";

        list.set(index, new AssignmentInstruction(
            ByteCodeConstants.ASSIGNMENT, si.offset,
            li.lineNumber, boi.getPriority(), newOperator,
            li, boi.value2));

    }

    /*
     * ArrayStore(arrayref, indexref,
     *            BinaryOperator(ArrayLoad(arrayref, indexref), ...))
     */
    private static int reconstructArrayOperator(
        List<Instruction> list, int index, Instruction i)
    {
        ArrayStoreInstruction asi = (ArrayStoreInstruction)i;
        BinaryOperatorInstruction boi = (BinaryOperatorInstruction)asi.valueref;

        if (boi.value1.opcode != ByteCodeConstants.ARRAYLOAD)
            return index;

        ArrayLoadInstruction ali = (ArrayLoadInstruction)boi.value1;
        CompareInstructionVisitor visitor = new CompareInstructionVisitor();

        if ((asi.lineNumber != ali.lineNumber) ||
            !visitor.visit(asi.arrayref, ali.arrayref) ||
            !visitor.visit(asi.indexref, ali.indexref))
            return index;

        if (asi.arrayref.opcode == ByteCodeConstants.DUPLOAD)
        {
            // Remove DupStore & DupLoad
            DupLoad dupLoad = (DupLoad)ali.arrayref;
            index = deleteDupStoreInstruction(list, index, dupLoad);
            ali.arrayref = dupLoad.dupStore.objectref;
        }

        if (asi.indexref.opcode == ByteCodeConstants.DUPLOAD)
        {
            // Remove DupStore & DupLoad
            DupLoad dupLoad = (DupLoad)ali.indexref;
            index = deleteDupStoreInstruction(list, index, dupLoad);
            ali.indexref = dupLoad.dupStore.objectref;
        }

        String newOperator = boi.operator + "=";

        list.set(index, new AssignmentInstruction(
            ByteCodeConstants.ASSIGNMENT, asi.offset,
            ali.lineNumber, boi.getPriority(), newOperator,
            ali, boi.value2));

        return index;
    }

    private static int deleteDupStoreInstruction(
        List<Instruction> list, int index, DupLoad dupLoad)
    {
        int indexTmp = index;

        while (indexTmp-- > 0)
        {
            Instruction i = list.get(indexTmp);

            if (dupLoad.dupStore == i)
            {
                list.remove(indexTmp);
                return --index;
            }
        }

        return index;
    }
}
