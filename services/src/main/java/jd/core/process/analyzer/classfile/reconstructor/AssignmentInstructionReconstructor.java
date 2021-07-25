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
import jd.core.model.instruction.bytecode.instruction.attribute.ValuerefAttribute;
import jd.core.process.analyzer.classfile.visitor.CompareInstructionVisitor;
import jd.core.process.analyzer.classfile.visitor.ReplaceDupLoadVisitor;
import jd.core.process.analyzer.classfile.visitor.SearchDupLoadInstructionVisitor;

/*
 * Recontruction des affectations multiples depuis le motif :
 * DupStore( ??? )
 * ...
 * {?Store | PutField | PutStatic}( DupLoad )
 * ...
 * ???( DupLoad )
 * Deux types de reconstruction :
 *  - a = b = c;
 *  - b = c; ...; a = b;
 */
public class AssignmentInstructionReconstructor
{
    private AssignmentInstructionReconstructor() {
        super();
    }

    public static void reconstruct(List<Instruction> list)
    {
        for (int dupStoreIndex=0; dupStoreIndex<list.size(); dupStoreIndex++)
        {
            if (list.get(dupStoreIndex).opcode != ByteCodeConstants.DUPSTORE) {
				continue;
			}

            // DupStore trouvé
            DupStore dupStore = (DupStore)list.get(dupStoreIndex);

            int length = list.size();

            // Ne pas prendre en compte les instructions DupStore suivie par une
            // instruction AASTORE ou ARRAYSTORE dont l'attribut arrayref pointe
            // vers l'instruction DupStore : ce cas est traité par
            // 'InitArrayInstructionReconstructor'.
            if (dupStoreIndex+1 < length)
            {
                Instruction i = list.get(dupStoreIndex+1);

                // Recherche de ?AStore ( DupLoad, index, value )
                if (i.opcode == Const.AASTORE ||
                    i.opcode == ByteCodeConstants.ARRAYSTORE)
                {
                    i = ((ArrayStoreInstruction)i).arrayref;
                    if (i.opcode == ByteCodeConstants.DUPLOAD &&
                        ((DupLoad)i).dupStore == dupStore) {
						continue;
					}
                }
            }

            int xstorePutfieldPutstaticIndex = dupStoreIndex;

            while (++xstorePutfieldPutstaticIndex < length)
            {
                Instruction xstorePutfieldPutstatic =
                    list.get(xstorePutfieldPutstaticIndex);
                Instruction dupload1 = null;

                switch (xstorePutfieldPutstatic.opcode)
                {
                case Const.ASTORE:
                case Const.ISTORE:
                case ByteCodeConstants.STORE:
                case Const.PUTFIELD:
                case Const.PUTSTATIC:
                case Const.AASTORE:
                case ByteCodeConstants.ARRAYSTORE:
                    {
                        Instruction i =
                            ((ValuerefAttribute)xstorePutfieldPutstatic).getValueref();
                        if (i.opcode == ByteCodeConstants.DUPLOAD &&
                            ((DupLoad)i).dupStore == dupStore)
                        {
                            // 1er DupLoad trouvé
                            dupload1 = i;
                        }
                    }
                    break;
                case Const.DSTORE:
                case Const.FSTORE:
                case Const.LSTORE:
                    new RuntimeException("Instruction inattendue")
                                .printStackTrace();
                }

                if (dupload1 == null) {
					continue;
				}

                // Recherche du 2eme DupLoad
                Instruction dupload2 = null;
                int dupload2Index = xstorePutfieldPutstaticIndex;

                while (++dupload2Index < length)
                {
                    dupload2 = SearchDupLoadInstructionVisitor.visit(
                        list.get(dupload2Index), dupStore);
                    if (dupload2 != null) {
						break;
					}
                }

                if (dupload2 == null) {
					continue;
				}

                if (dupload1.lineNumber == dupload2.lineNumber)
                {
                    // Assignation multiple sur une seule ligne : a = b = c;
                    Instruction newInstruction = createAssignmentInstruction(
                        xstorePutfieldPutstatic, dupStore);

                    // Remplacement du 2eme DupLoad
                    ReplaceDupLoadVisitor visitor =
                        new ReplaceDupLoadVisitor(dupStore, newInstruction);
                    visitor.visit(list.get(dupload2Index));

                    // Mise a jour de toutes les instructions TernaryOpStore
                    // pointant vers cette instruction d'assignation.
                    // Explication:
                    //	ternaryOp2ndValueOffset est initialisée avec l'offset de
                    //  la derniere instruction poussant une valeur sur la pile.
                    //  Dans le cas d'une instruction d'assignation contenue
                    //  dans un operateur ternaire, ternaryOp2ndValueOffset est
                    //  initialise sur l'offset de dupstore. Il faut reajuster
                    //  ternaryOp2ndValueOffset a l'offset de AssignmentInstruction,
                    //  pour que TernaryOpReconstructor fonctionne.
                    int j = dupStoreIndex;

                    while (j-- > 0)
                    {
                        if (list.get(j).opcode == ByteCodeConstants.TERNARYOPSTORE)
                        {
                            // TernaryOpStore trouvé
                            TernaryOpStore tos = (TernaryOpStore)list.get(j);
                            if (tos.ternaryOp2ndValueOffset == dupStore.offset)
                            {
                                tos.ternaryOp2ndValueOffset = newInstruction.offset;
                                break;
                            }
                        }
                    }

                    list.remove(xstorePutfieldPutstaticIndex);
                    list.remove(dupStoreIndex);
                    dupStoreIndex--;
                    length -= 2;
                }
                else
                {
                    // Assignation multiple sur deux lignes : b = c; a = b;

                    // Create new instruction
                    // {?Load | GetField | GetStatic | AALoad | ARRAYLoad }
                    Instruction newInstruction =
                        createInstruction(xstorePutfieldPutstatic);

                    if (newInstruction != null)
                    {
                        // Remplacement du 1er DupLoad
                        ReplaceDupLoadVisitor visitor =
                            new ReplaceDupLoadVisitor(dupStore, dupStore.objectref);
                        visitor.visit(xstorePutfieldPutstatic);

                        // Remplacement du 2eme DupLoad
                        visitor.init(dupStore, newInstruction);
                        visitor.visit(list.get(dupload2Index));

                        list.remove(dupStoreIndex);
                        dupStoreIndex--;
                        length--;
                    }
                }
            }
        }
    }

    private static Instruction createInstruction(
        Instruction xstorePutfieldPutstatic)
    {
        switch (xstorePutfieldPutstatic.opcode)
        {
        case Const.ASTORE:
            return new ALoad(
                Const.ALOAD,
                xstorePutfieldPutstatic.offset,
                xstorePutfieldPutstatic.lineNumber,
                ((AStore)xstorePutfieldPutstatic).index);
        case Const.ISTORE:
            return new ILoad(
                Const.ILOAD,
                xstorePutfieldPutstatic.offset,
                xstorePutfieldPutstatic.lineNumber,
                ((IStore)xstorePutfieldPutstatic).index);
        case ByteCodeConstants.STORE:
            return new LoadInstruction(
                ByteCodeConstants.LOAD,
                xstorePutfieldPutstatic.offset,
                xstorePutfieldPutstatic.lineNumber,
                ((StoreInstruction)xstorePutfieldPutstatic).index,
                xstorePutfieldPutstatic.getReturnedSignature(null, null));
        case Const.PUTFIELD:
            return new GetField(
                Const.GETFIELD,
                xstorePutfieldPutstatic.offset,
                xstorePutfieldPutstatic.lineNumber,
                ((PutField)xstorePutfieldPutstatic).index,
                ((PutField)xstorePutfieldPutstatic).objectref);
        case Const.PUTSTATIC:
            return new GetStatic(
                Const.GETSTATIC,
                xstorePutfieldPutstatic.offset,
                xstorePutfieldPutstatic.lineNumber,
                ((PutStatic)xstorePutfieldPutstatic).index);
        case Const.AASTORE:
            return new AALoad(
                ByteCodeConstants.ARRAYLOAD,
                xstorePutfieldPutstatic.offset,
                xstorePutfieldPutstatic.lineNumber,
                ((AAStore)xstorePutfieldPutstatic).arrayref,
                ((AAStore)xstorePutfieldPutstatic).indexref);
        case ByteCodeConstants.ARRAYSTORE:
            return new ArrayLoadInstruction(
                ByteCodeConstants.ARRAYLOAD,
                xstorePutfieldPutstatic.offset,
                xstorePutfieldPutstatic.lineNumber,
                ((ArrayStoreInstruction)xstorePutfieldPutstatic).arrayref,
                ((ArrayStoreInstruction)xstorePutfieldPutstatic).indexref,
                ((ArrayStoreInstruction)xstorePutfieldPutstatic).signature);
        default:
            return null;
        }
    }

    private static Instruction createAssignmentInstruction(
        Instruction xstorePutfieldPutstatic, DupStore dupStore)
    {
        if (dupStore.objectref.opcode == ByteCodeConstants.BINARYOP)
        {
            // Reconstruction de "a = b += c"
            Instruction value1 =
                ((BinaryOperatorInstruction)dupStore.objectref).value1;

            if (xstorePutfieldPutstatic.lineNumber == value1.lineNumber)
            {
                switch (xstorePutfieldPutstatic.opcode)
                {
                case Const.ASTORE:
                    if (value1.opcode == Const.ALOAD &&
                        ((StoreInstruction)xstorePutfieldPutstatic).index ==
                            ((LoadInstruction)value1).index) {
						return createBinaryOperatorAssignmentInstruction(
                            xstorePutfieldPutstatic, dupStore);
					}
                    break;
                case Const.ISTORE:
                    if (value1.opcode == Const.ILOAD &&
                        ((StoreInstruction)xstorePutfieldPutstatic).index ==
                            ((LoadInstruction)value1).index) {
						return createBinaryOperatorAssignmentInstruction(
                            xstorePutfieldPutstatic, dupStore);
					}
                    break;
                case ByteCodeConstants.STORE:
                    if (value1.opcode == ByteCodeConstants.LOAD &&
                        ((StoreInstruction)xstorePutfieldPutstatic).index ==
                            ((LoadInstruction)value1).index) {
						return createBinaryOperatorAssignmentInstruction(
                            xstorePutfieldPutstatic, dupStore);
					}
                    break;
                case Const.PUTFIELD:
                    if (value1.opcode == Const.GETFIELD &&
                        ((PutField)xstorePutfieldPutstatic).index ==
                            ((GetField)value1).index)
                    {
                        CompareInstructionVisitor visitor =
                            new CompareInstructionVisitor();

                        if (visitor.visit(
                                ((PutField)xstorePutfieldPutstatic).objectref,
                                ((GetField)value1).objectref)) {
							return createBinaryOperatorAssignmentInstruction(
                                xstorePutfieldPutstatic, dupStore);
						}
                    }
                    break;
                case Const.PUTSTATIC:
                    if (value1.opcode == Const.GETFIELD &&
                        ((PutStatic)xstorePutfieldPutstatic).index ==
                            ((GetStatic)value1).index) {
						return createBinaryOperatorAssignmentInstruction(
                            xstorePutfieldPutstatic, dupStore);
					}
                    break;
                case Const.AASTORE:
                    if (value1.opcode == Const.AALOAD)
                    {
                        ArrayStoreInstruction aas =
                            (ArrayStoreInstruction)xstorePutfieldPutstatic;
                        ArrayLoadInstruction aal =
                            (ArrayLoadInstruction)value1;
                        CompareInstructionVisitor visitor =
                            new CompareInstructionVisitor();

                        if (visitor.visit(
                                aas.arrayref, aal.arrayref) &&
                            visitor.visit(
                                aas.indexref, aal.indexref)) {
							return createBinaryOperatorAssignmentInstruction(
                                    xstorePutfieldPutstatic, dupStore);
						}
                    }
                    break;
                case ByteCodeConstants.ARRAYSTORE:
                    if (value1.opcode == ByteCodeConstants.ARRAYLOAD)
                    {
                        ArrayStoreInstruction aas =
                            (ArrayStoreInstruction)xstorePutfieldPutstatic;
                        ArrayLoadInstruction aal =
                            (ArrayLoadInstruction)value1;
                        CompareInstructionVisitor visitor =
                            new CompareInstructionVisitor();

                        if (visitor.visit(
                                aas.arrayref, aal.arrayref) &&
                            visitor.visit(
                                aas.indexref, aal.indexref)) {
							return createBinaryOperatorAssignmentInstruction(
                                    xstorePutfieldPutstatic, dupStore);
						}
                    }
                    break;
                case Const.DSTORE:
                case Const.FSTORE:
                case Const.LSTORE:
                    new RuntimeException("Unexpected instruction")
                                .printStackTrace();
                }
            }
        }

        Instruction newInstruction = createInstruction(xstorePutfieldPutstatic);
        return new AssignmentInstruction(
            ByteCodeConstants.ASSIGNMENT, xstorePutfieldPutstatic.offset,
            dupStore.lineNumber, 14, "=",
            newInstruction, dupStore.objectref);
    }

    private static AssignmentInstruction createBinaryOperatorAssignmentInstruction(
            Instruction xstorePutfieldPutstatic, DupStore dupstore)
    {
        BinaryOperatorInstruction boi =
            (BinaryOperatorInstruction)dupstore.objectref;

        String newOperator = boi.operator + "=";

        return new AssignmentInstruction(
                ByteCodeConstants.ASSIGNMENT, xstorePutfieldPutstatic.offset,
                dupstore.lineNumber, boi.getPriority(), newOperator,
                createInstruction(xstorePutfieldPutstatic), boi.value2);
    }
}
