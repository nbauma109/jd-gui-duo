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

public class ClassFileConstants
{
    private ClassFileConstants() {
        super();
    }

    // Access flag for Class, Field, Method, Nested class
    public static final short ACC_PUBLIC       = 0x0001; // C F M N
    public static final short ACC_PRIVATE      = 0x0002; //   F M N
    public static final short ACC_PROTECTED    = 0x0004; //   F M N
    public static final short ACC_STATIC       = 0x0008; //   F M N
    public static final short ACC_FINAL        = 0x0010; // C F M N
    public static final short ACC_SYNCHRONIZED = 0x0020; //     M
    public static final short ACC_SUPER        = 0x0020; // C
    public static final short ACC_VOLATILE     = 0x0040; //   F
    public static final short ACC_BRIDGE       = 0x0040; //     M
    public static final short ACC_TRANSIENT    = 0x0080; //   F
    public static final short ACC_VARARGS      = 0x0080; //     M
    public static final short ACC_NATIVE       = 0x0100; //     M
    public static final short ACC_INTERFACE    = 0x0200; // C     N
    public static final short ACC_ABSTRACT     = 0x0400; // C   M N
    public static final short ACC_STRICT       = 0x0800; //     M
    public static final short ACC_SYNTHETIC    = 0x1000; // C F M N
    public static final short ACC_ANNOTATION   = 0x2000; // C     N
    public static final short ACC_ENUM         = 0x4000; // C F   N
}
