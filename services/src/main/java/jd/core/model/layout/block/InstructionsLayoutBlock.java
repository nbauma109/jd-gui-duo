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
package jd.core.model.layout.block;

import java.util.List;

import jd.core.model.classfile.ClassFile;
import jd.core.model.classfile.Method;
import jd.core.model.instruction.bytecode.instruction.Instruction;

public class InstructionsLayoutBlock extends LayoutBlock
{
    private final ClassFile classFile;
    private Method method;
    private final List<Instruction> instructions;
    private int firstIndex;
    private int lastIndex;
    private int firstOffset;
    private int lastOffset;

    public InstructionsLayoutBlock(
        int firstLineNumber, int lastLineNumber,
        int minimalLineCount, int maximalLineCount, int preferedLineCount,
        ClassFile classFile,
        Method method,
        List<Instruction> instructions,
        int firstIndex, int lastIndex,
        int firstOffset, int lastOffset)
    {
        super(
            LayoutBlockConstants.INSTRUCTIONS,
            firstLineNumber, lastLineNumber,
            minimalLineCount, maximalLineCount, preferedLineCount);
        this.classFile = classFile;
        this.setMethod(method);
        this.instructions = instructions;
        this.setFirstIndex(firstIndex);
        this.setLastIndex(lastIndex);
        this.setFirstOffset(firstOffset);
        this.setLastOffset(lastOffset);
    }

	public ClassFile getClassFile() {
		return classFile;
	}

	public Method getMethod() {
		return method;
	}

	public void setMethod(Method method) {
		this.method = method;
	}

	public int getFirstOffset() {
		return firstOffset;
	}

	public void setFirstOffset(int firstOffset) {
		this.firstOffset = firstOffset;
	}

	public int getLastOffset() {
		return lastOffset;
	}

	public void setLastOffset(int lastOffset) {
		this.lastOffset = lastOffset;
	}

	public int getFirstIndex() {
		return firstIndex;
	}

	public void setFirstIndex(int firstIndex) {
		this.firstIndex = firstIndex;
	}

	public int getLastIndex() {
		return lastIndex;
	}

	public void setLastIndex(int lastIndex) {
		this.lastIndex = lastIndex;
	}

	public List<Instruction> getInstructions() {
		return instructions;
	}
}
