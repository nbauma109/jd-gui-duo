/**
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
 */
package jd.core.model.instruction.fast.instruction;

import java.util.Objects;

import jd.core.model.classfile.ConstantPool;
import jd.core.model.classfile.LocalVariable;
import jd.core.model.classfile.LocalVariables;
import jd.core.model.instruction.bytecode.instruction.Instruction;

/** List & while(true). */
public class FastDeclaration extends Instruction
{
    public final LocalVariable lv;
    public Instruction instruction;

    public FastDeclaration(
        int opcode, int offset, int lineNumber,
        LocalVariable lv, Instruction instruction)
    {
        super(opcode, offset, lineNumber);
        this.lv = lv;
        this.instruction = instruction;
    }

    @Override
    public String getReturnedSignature(
            ConstantPool constants, LocalVariables localVariables)
    {
        return null;
    }

	@Override
	public int hashCode() {
		return Objects.hash(lv.index, lv.nameIndex, lv.signatureIndex);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		FastDeclaration other = (FastDeclaration) obj;
		return lv.index == other.lv.index 
            && lv.nameIndex == other.lv.nameIndex
            && lv.signatureIndex == other.lv.signatureIndex;
	}
}
