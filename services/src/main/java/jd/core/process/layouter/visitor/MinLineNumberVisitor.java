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

import jd.core.model.instruction.bytecode.ByteCodeConstants;
import jd.core.model.instruction.bytecode.instruction.*;

public class MinLineNumberVisitor
{
    private MinLineNumberVisitor() {
        super();
    }

    public static int visit(Instruction instruction)
    {
        switch (instruction.opcode)
        {
        case ByteCodeConstants.ARRAYLOAD:
            return visit(((ArrayLoadInstruction)instruction).arrayref);
        case Const.AASTORE:
        case ByteCodeConstants.ARRAYSTORE:
            return visit(((ArrayStoreInstruction)instruction).arrayref);
        case ByteCodeConstants.ASSIGNMENT:
            return visit(((AssignmentInstruction)instruction).value1);
        case ByteCodeConstants.BINARYOP:
            return visit(((BinaryOperatorInstruction)instruction).value1);
        case ByteCodeConstants.PREINC:
            {
                IncInstruction ii = (IncInstruction)instruction;

                switch (ii.count)
                {
                case -1:
                case 1:
                    return instruction.lineNumber;
                default:
                    return visit(ii.value);
                }
            }
        case ByteCodeConstants.POSTINC:
            {
                IncInstruction ii = (IncInstruction)instruction;

                switch (ii.count)
                {
                case -1:
                case 1:
                    return visit(ii.value);
                default:
                    return instruction.lineNumber;
                }
            }
        case Const.INSTANCEOF:
            return visit(((InstanceOf)instruction).objectref);
        case Const.INVOKEINTERFACE:
        case Const.INVOKEVIRTUAL:
        case Const.INVOKESPECIAL:
            return visit(((InvokeNoStaticInstruction)instruction).objectref);
        case Const.POP:
            return visit(((Pop)instruction).objectref);
        case Const.PUTFIELD:
            return visit(((PutField)instruction).objectref);
        case ByteCodeConstants.TERNARYOP:
            return visit(((TernaryOperator)instruction).test);
        }

        return instruction.lineNumber;
    }
}
