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
package jd.core.process.layouter.visitor;

import org.apache.bcel.Const;

import java.util.List;

import jd.core.model.instruction.bytecode.ByteCodeConstants;
import jd.core.model.instruction.bytecode.instruction.*;
import jd.core.model.instruction.fast.FastConstants;
import jd.core.model.instruction.fast.instruction.FastDeclaration;

public class MaxLineNumberVisitor
{
    private MaxLineNumberVisitor() {
        super();
    }

    public static int visit(Instruction instruction)
    {
        int maxLineNumber = instruction.lineNumber;

        switch (instruction.opcode)
        {
        case ByteCodeConstants.ARRAYLOAD:
            maxLineNumber = visit(((ArrayLoadInstruction)instruction).indexref);
            break;
        case Const.AASTORE:
        case ByteCodeConstants.ARRAYSTORE:
            maxLineNumber = visit(((ArrayStoreInstruction)instruction).valueref);
            break;
        case ByteCodeConstants.ASSERT:
            {
                AssertInstruction ai = (AssertInstruction)instruction;
                maxLineNumber = visit((ai.msg == null) ? ai.test : ai.msg);
            }
            break;
        case Const.ATHROW:
            maxLineNumber = visit(((AThrow)instruction).value);
            break;
        case ByteCodeConstants.UNARYOP:
            maxLineNumber = visit(((UnaryOperatorInstruction)instruction).value);
            break;
        case ByteCodeConstants.BINARYOP:
        case ByteCodeConstants.ASSIGNMENT:
            maxLineNumber = visit(((BinaryOperatorInstruction)instruction).value2);
            break;
        case Const.CHECKCAST:
            maxLineNumber = visit(((CheckCast)instruction).objectref);
            break;
        case ByteCodeConstants.STORE:
        case Const.ASTORE:
        case Const.ISTORE:
            maxLineNumber = visit(((StoreInstruction)instruction).valueref);
            break;
        case ByteCodeConstants.DUPSTORE:
            maxLineNumber = visit(((DupStore)instruction).objectref);
            break;
        case ByteCodeConstants.CONVERT:
        case ByteCodeConstants.IMPLICITCONVERT:
            maxLineNumber = visit(((ConvertInstruction)instruction).value);
            break;
        case FastConstants.DECLARE:
            {
                FastDeclaration fd = (FastDeclaration)instruction;
                if (fd.instruction != null)
                    maxLineNumber = visit(fd.instruction);
            }
            break;
        case ByteCodeConstants.IFCMP:
            maxLineNumber = visit(((IfCmp)instruction).value2);
            break;
        case ByteCodeConstants.IF:
        case ByteCodeConstants.IFXNULL:
            maxLineNumber = visit(((IfInstruction)instruction).value);
            break;
        case ByteCodeConstants.COMPLEXIF:
            {
                List<Instruction> branchList =
                    ((ComplexConditionalBranchInstruction)instruction).instructions;
                maxLineNumber = visit(branchList.get(branchList.size()-1));
            }
            break;
        case Const.INSTANCEOF:
            maxLineNumber = visit(((InstanceOf)instruction).objectref);
            break;
        case Const.INVOKEINTERFACE:
        case Const.INVOKESPECIAL:
        case Const.INVOKEVIRTUAL:
        case Const.INVOKESTATIC:
            {
                List<Instruction> list = ((InvokeInstruction)instruction).args;
                int length = list.size();

                if (length == 0)
                {
                    maxLineNumber = instruction.lineNumber;
                }
                else
                {
                    // Correction pour un tres curieux bug : les numeros de
                    // ligne des parametres ne sont pas toujours en ordre croissant
                    maxLineNumber = visit(list.get(0));

                    for (int i=length-1; i>0; i--)
                    {
                        int lineNumber = visit(list.get(i));
                        if (maxLineNumber < lineNumber)
                            maxLineNumber = lineNumber;
                    }
                }
            }
            break;
        case ByteCodeConstants.INVOKENEW:
        case FastConstants.ENUMVALUE:
            {
                List<Instruction> list = ((InvokeNew)instruction).args;
                int length = list.size();

                if (length == 0)
                {
                    maxLineNumber = instruction.lineNumber;
                }
                else
                {
                    // Correction pour un tres curieux bug : les numeros de
                    // ligne des parametres ne sont pas toujours en ordre croissant
                    maxLineNumber = visit(list.get(0));

                    for (int i=length-1; i>0; i--)
                    {
                        int lineNumber = visit(list.get(i));
                        if (maxLineNumber < lineNumber)
                            maxLineNumber = lineNumber;
                    }
                }
            }
            break;
        case Const.LOOKUPSWITCH:
            maxLineNumber = visit(((LookupSwitch)instruction).key);
            break;
        case Const.MONITORENTER:
            maxLineNumber = visit(((MonitorEnter)instruction).objectref);
            break;
        case Const.MONITOREXIT:
            maxLineNumber = visit(((MonitorExit)instruction).objectref);
            break;
        case Const.MULTIANEWARRAY:
            {
                Instruction[] dimensions = ((MultiANewArray)instruction).dimensions;
                int length = dimensions.length;
                if (length > 0)
                    maxLineNumber = visit(dimensions[length-1]);
            }
            break;
        case Const.NEWARRAY:
            maxLineNumber = visit(((NewArray)instruction).dimension);
            break;
        case Const.ANEWARRAY:
            maxLineNumber = visit(((ANewArray)instruction).dimension);
            break;
        case Const.POP:
            maxLineNumber = visit(((Pop)instruction).objectref);
            break;
        case Const.PUTFIELD:
            maxLineNumber = visit(((PutField)instruction).valueref);
            break;
        case Const.PUTSTATIC:
            maxLineNumber = visit(((PutStatic)instruction).valueref);
            break;
        case ByteCodeConstants.XRETURN:
            maxLineNumber = visit(((ReturnInstruction)instruction).valueref);
            break;
        case Const.TABLESWITCH:
            maxLineNumber = visit(((TableSwitch)instruction).key);
            break;
        case ByteCodeConstants.TERNARYOPSTORE:
            maxLineNumber = visit(((TernaryOpStore)instruction).objectref);
            break;
        case ByteCodeConstants.PREINC:
        case ByteCodeConstants.POSTINC:
            IncInstruction ii = (IncInstruction)instruction;
            maxLineNumber = visit(ii.value);
            break;
        case ByteCodeConstants.INITARRAY:
        case ByteCodeConstants.NEWANDINITARRAY:
            {
                InitArrayInstruction iai = (InitArrayInstruction)instruction;
                int length = iai.values.size();
                if (length > 0)
                    maxLineNumber = visit(iai.values.get(length-1));
            }
            break;
        case ByteCodeConstants.TERNARYOP:
            maxLineNumber = visit(((TernaryOperator)instruction).value2);
            break;
        }

        // Autre curieux bug : les constantes finales passees en parametres
        // peuvent avoir un numero de ligne plus petit que le numero de ligne
        // de l'instruction INVOKE*
        if (maxLineNumber < instruction.lineNumber)
        {
            return instruction.lineNumber;
        }
        return maxLineNumber;
    }
}
