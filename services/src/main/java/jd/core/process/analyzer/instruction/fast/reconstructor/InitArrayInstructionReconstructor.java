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
package jd.core.process.analyzer.instruction.fast.reconstructor;

import org.apache.bcel.Const;

import java.util.ArrayList;
import java.util.List;

import jd.core.model.instruction.bytecode.ByteCodeConstants;
import jd.core.model.instruction.bytecode.instruction.*;
import jd.core.process.analyzer.util.ReconstructorUtil;

/*
 * Recontruction des initialisation de tableaux depuis le motif :
 * DupStore0 ( NewArray | ANewArray ... )
 * ?AStore ( DupLoad0, index=0, value )
 * DupStore1 ( DupLoad0 )
 * ?AStore ( DupLoad1, index=1, value )
 * DupStore2 ( DupLoad1 )
 * ?AStore ( DupLoad2, index=2, value )
 * ...
 * ???( DupLoadN )
 *
 * Cette operation doit être executee avant 'AssignmentInstructionReconstructor'.
 */
public class InitArrayInstructionReconstructor
{
    private InitArrayInstructionReconstructor() {
        super();
    }

    public static void reconstruct(List<Instruction> list)
    {
        for (int index=list.size()-1; index>=0; --index)
        {
            Instruction i = list.get(index);

            if (i.opcode != ByteCodeConstants.DUPSTORE) {
				continue;
			}

            DupStore dupStore = (DupStore)i;
            int opcode = dupStore.objectref.opcode;

            if (opcode != Const.NEWARRAY &&
                opcode != Const.ANEWARRAY) {
				continue;
			}

            reconstructAInstruction(list, index, dupStore);
        }
    }

    private static void reconstructAInstruction(
        List<Instruction> list, int index, DupStore dupStore)
    {
        // 1er DupStore trouvé
        final int length = list.size();
        int firstDupStoreIndex = index;
        DupStore lastDupStore = dupStore;
        ArrayStoreInstruction lastAsi = null;
        int arrayIndex = 0;
        List<Instruction> values = new ArrayList<>();

        while (++index < length)
        {
            Instruction i = list.get(index);

            // Recherche de ?AStore ( DupLoad, index, value )
            if (i.opcode != Const.AASTORE &&
                i.opcode != ByteCodeConstants.ARRAYSTORE) {
				break;
			}

            ArrayStoreInstruction asi = (ArrayStoreInstruction)i;

            if (asi.arrayref.opcode != ByteCodeConstants.DUPLOAD ||
                asi.arrayref.offset != lastDupStore.offset) {
				break;
			}

            lastAsi = asi;

            // Si les premieres cases d'un tableau ont pour valeur 0, elles
            // ne sont pas initialisee ! La boucle suivante reconstruit
            // l'initialisation des valeurs 0.
            int indexOfArrayStoreInstruction = getArrayIndex(asi.indexref);
            while (indexOfArrayStoreInstruction > arrayIndex)
            {
                values.add(new IConst(
                    ByteCodeConstants.ICONST, asi.offset, asi.lineNumber, 0));
                arrayIndex++;
            }

            values.add(asi.valueref);
            arrayIndex++;

            // Recherche de DupStoreM( DupLoadN )
            if (++index >= length) {
				break;
			}

            i = list.get(index);

            if (i.opcode != ByteCodeConstants.DUPSTORE) {
				break;
			}

            DupStore nextDupStore = (DupStore)i;

            if (nextDupStore.objectref.opcode != ByteCodeConstants.DUPLOAD ||
                nextDupStore.objectref.offset != lastDupStore.offset) {
				break;
			}

            lastDupStore = nextDupStore;
        }

        if (lastAsi != null)
        {
            // Instanciation d'une instruction InitArrayInstruction
            InitArrayInstruction iai = new InitArrayInstruction(
                ByteCodeConstants.NEWANDINITARRAY, lastAsi.offset,
                dupStore.lineNumber, dupStore.objectref, values);

            // Recherche de l'instruction 'DupLoad' suivante
            Instruction parent = ReconstructorUtil.replaceDupLoad(
                list, index, lastDupStore, iai);

            if (parent != null) {
				switch (parent.opcode)
                {
                case Const.AASTORE:
                    iai.opcode = ByteCodeConstants.INITARRAY;
                }
			}
            // Retrait des instructions de la liste
            while (firstDupStoreIndex < index) {
				list.remove(--index);
			}

            // Initialisation des types de constantes entieres
            if (iai.newArray.opcode == Const.NEWARRAY)
            {
                NewArray na = (NewArray)iai.newArray;
                switch (na.type)
                {
                case Const.T_BOOLEAN:
                    setContantTypes("Z", iai.values);
                    break;
                case Const.T_CHAR:
                    setContantTypes("C", iai.values);
                    break;
                case Const.T_BYTE:
                    setContantTypes("B", iai.values);
                    break;
                case Const.T_SHORT:
                    setContantTypes("S", iai.values);
                    break;
                case Const.T_INT:
                    setContantTypes("I", iai.values);
                    break;
                }
            }
        }
    }

    private static void setContantTypes(
        String signature, List<Instruction> values)
    {
        final int length = values.size();

        for (int i=0; i<length; i++)
        {
            Instruction value = values.get(i);
            switch (value.opcode)
            {
            case Const.BIPUSH:
            case ByteCodeConstants.ICONST:
            case Const.SIPUSH:
                ((IConst)value).setReturnedSignature(signature);
                break;
            }
        }
    }

    private static int getArrayIndex(Instruction i)
    {
        switch (i.opcode)
        {
        case ByteCodeConstants.ICONST:
            return ((IConst)i).value;
        case Const.BIPUSH:
            return ((BIPush)i).value;
        case Const.SIPUSH:
            return ((SIPush)i).value;
        default:
            return -1;
        }
    }
}
