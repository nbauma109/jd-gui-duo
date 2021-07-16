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
package jd.core.model.classfile.attribute;

import org.apache.bcel.Const;
import org.apache.bcel.classfile.CodeException;

import java.util.List;
import java.util.Map.Entry;

public class AttributeCode extends Attribute
{
    //private int maxStack;
    //private int maxLocals;
    public final byte[] code;
    public final List<Entry<Integer, CodeException>> exceptionTable;
    public final Attribute[] attributes;

    public AttributeCode(byte tag,
                         int attributeNameIndex,
                         byte[] code,
                         List<Entry<Integer, CodeException>> exceptionTable,
                         Attribute[] attributes)
    {
        super(tag, attributeNameIndex);
        //this.maxStack = maxStack;
        //this.maxLocals = maxLocals;
        this.code = code;
        this.exceptionTable = exceptionTable;
        this.attributes = attributes;
    }

    public AttributeNumberTable getAttributeLineNumberTable()
    {
        if (this.attributes != null)
            for (int i=this.attributes.length-1; i>=0; --i)
                if (this.attributes[i].tag == Const.ATTR_LINE_NUMBER_TABLE)
                    return (AttributeNumberTable)this.attributes[i];

        return null;
    }

    public AttributeLocalVariableTable getAttributeLocalVariableTable()
    {
        if (this.attributes != null)
            for (int i=this.attributes.length-1; i>=0; --i)
                if (this.attributes[i].tag == Const.ATTR_LOCAL_VARIABLE_TABLE)
                    return (AttributeLocalVariableTable)this.attributes[i];

        return null;
    }

    public AttributeLocalVariableTable getAttributeLocalVariableTypeTable()
    {
        if (this.attributes != null)
            for (int i=this.attributes.length-1; i>=0; --i)
                if (this.attributes[i].tag == Const.ATTR_LOCAL_VARIABLE_TYPE_TABLE)
                    return (AttributeLocalVariableTable)this.attributes[i];

        return null;
    }
}
