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
package jd.core.model.classfile.constant;

/**
 * @See https://docs.oracle.com/javase/specs/jvms/se16/html/jvms-4.html
 */
public class ConstantConstant
{
    private ConstantConstant() {
        super();
    }

    public static final byte CONSTANT_UNKNOWN            = 0;
    public static final byte CONSTANT_UTF8               = 1;
    public static final byte CONSTANT_INTEGER            = 3;
    public static final byte CONSTANT_FLOAT              = 4;
    public static final byte CONSTANT_LONG               = 5;
    public static final byte CONSTANT_DOUBLE             = 6;
    public static final byte CONSTANT_CLASS              = 7;
    public static final byte CONSTANT_STRING             = 8;
    public static final byte CONSTANT_FIELDREF           = 9;
    public static final byte CONSTANT_METHODREF          = 10;
    public static final byte CONSTANT_INTERFACEMETHODREF = 11;
    public static final byte CONSTANT_NAMEANDTYPE        = 12;
}
