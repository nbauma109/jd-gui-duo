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
package jd.core.model.classfile;

public class LocalVariable
    implements Comparable<LocalVariable>
{
	public int startPc;
    public int length;
    public int nameIndex;
    public int signatureIndex;
    public final int index;
    public boolean exceptionOrReturnAddress;
    // Champ de bits utilisé pour determiner le type de la variable (byte, char,
    // short, int).
    public int typesBitField;
    // Champs utilisé lors de la génération des déclarations de variables
    // locales (FastDeclarationAnalyzer.Analyze).
    public boolean declarationFlag = false;

    public boolean finalFlag = false;

    private boolean toBeRemoved;

    public LocalVariable(
            int startPc, int length, int nameIndex, int signatureIndex,
            int index)
    {
        this(startPc, length, nameIndex, signatureIndex, index, false, 0);
    }

    public LocalVariable(
            int startPc, int length, int nameIndex, int signatureIndex,
            int index, int typesBitSet)
    {
        this(startPc, length, nameIndex, signatureIndex, index, false,
             typesBitSet);
    }

    public LocalVariable(
            int startPc, int length, int nameIndex, int signatureIndex,
            int index, boolean exception)
    {
        this(startPc, length, nameIndex, signatureIndex, index, exception, 0);
    }

    protected LocalVariable(
        int startPc, int length, int nameIndex, int signatureIndex,
        int index, boolean exceptionOrReturnAddress, int typesBitField)
    {
        this.startPc = startPc;
        this.length = length;
        this.nameIndex = nameIndex;
        this.signatureIndex = signatureIndex;
        this.index = index;
        this.exceptionOrReturnAddress = exceptionOrReturnAddress;
        this.declarationFlag = exceptionOrReturnAddress;
        this.typesBitField = typesBitField;
    }

    public void updateRange(int offset)
    {
        if (offset < this.startPc)
        {
            this.length += this.startPc - offset;
            this.startPc = offset;
        }

        if (offset >= this.startPc+this.length)
        {
            this.length = offset - this.startPc + 1;
        }
    }

    public void updateSignatureIndex(int signatureIndex)
    {
        this.signatureIndex = signatureIndex;
    }

    @Override
    public String toString()
    {
        return
            "LocalVariable{startPc=" + startPc +
            ", length=" + length +
            ", nameIndex=" + nameIndex +
            ", signatureIndex=" + signatureIndex +
            ", index=" + index +
            "}";
    }

    @Override
    public int compareTo(LocalVariable other)
    {
        if (other == null) {
			return -1;
		}

        if (this.nameIndex != other.nameIndex) {
			return this.nameIndex - other.nameIndex;
		}

        if (this.length != other.length) {
			return this.length - other.length;
		}

        if (this.startPc != other.startPc) {
			return this.startPc - other.startPc;
		}

        return this.index - other.index;
    }

	public boolean isToBeRemoved() {
		return toBeRemoved;
	}

	public void setToBeRemoved(boolean toBeRemoved) {
		this.toBeRemoved = toBeRemoved;
	}
}
