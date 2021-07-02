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
package jd.core.process.writer;

import org.jd.core.v1.api.loader.Loader;
import org.jd.core.v1.model.classfile.constant.*;
import org.jd.core.v1.util.StringConstants;

import jd.core.model.classfile.ClassFile;
import jd.core.model.classfile.ConstantPool;
import jd.core.model.reference.ReferenceMap;
import jd.core.printer.Printer;
import jd.core.util.StringUtil;

public class ConstantValueWriter
{
    private ConstantValueWriter() {
        super();
    }

    public static void Write(
        Loader loader, Printer printer, ReferenceMap referenceMap,
        ClassFile classFile, Constant cv)
    {
        Write(loader, printer, referenceMap, classFile, cv, (byte)0);
    }

    public static void Write(
        Loader loader, Printer printer, ReferenceMap referenceMap,
        ClassFile classFile, Constant cv, byte constantIntegerType)
    {
        ConstantPool constants = classFile.getConstantPool();

        switch (cv.getClass().getSimpleName())
        {
          case "ConstantDouble":
            {
                double d = ((ConstantDouble)cv).getValue();

                if (d == Double.POSITIVE_INFINITY)
                {
                    Write(
                        loader, printer, referenceMap, classFile,
                        StringConstants.JAVA_LANG_DOUBLE, "POSITIVE_INFINITY", "D");
                }
                else if (d == Double.NEGATIVE_INFINITY)
                {
                    Write(
                        loader, printer, referenceMap, classFile,
                        StringConstants.JAVA_LANG_DOUBLE, "NEGATIVE_INFINITY", "D");
                }
                else if (Double.isNaN(d))
                {
                    Write(
                        loader, printer, referenceMap, classFile,
                        StringConstants.JAVA_LANG_DOUBLE, "NaN", "D");
                }
                else if (d == Double.MAX_VALUE)
                {
                    Write(
                        loader, printer, referenceMap, classFile,
                        StringConstants.JAVA_LANG_DOUBLE, StringConstants.MAX_VALUE, "D");
                }
                /* else if (d == Double.MIN_NORMAL)
                {
                    Write(
                        loader, printer, referenceMap, classFile,
                        StringConstants.JAVA_LANG_DOUBLE, "MIN_NORMAL", "D");
                } */
                else if (d == Double.MIN_VALUE)
                {
                    Write(
                        loader, printer, referenceMap, classFile,
                        StringConstants.JAVA_LANG_DOUBLE, StringConstants.MIN_VALUE, "D");
                }
                else
                {
                    // TODO Conversion de la valeur en constante ?
                    String value = String.valueOf(d);
                    if (value.indexOf('.') == -1)
                        value += ".0";
                    printer.printNumeric(value + 'D');
                }
            }
            break;
          case "ConstantFloat":
            {
                float value = ((ConstantFloat)cv).getValue();

                if (value == Float.POSITIVE_INFINITY)
                {
                    Write(
                        loader, printer, referenceMap, classFile,
                        StringConstants.JAVA_LANG_FLOAT, "POSITIVE_INFINITY", "F");
                }
                else if (value == Float.NEGATIVE_INFINITY)
                {
                    Write(
                        loader, printer, referenceMap, classFile,
                        StringConstants.JAVA_LANG_FLOAT, "NEGATIVE_INFINITY", "F");
                }
                else if (Float.isNaN(value))
                {
                    Write(
                        loader, printer, referenceMap, classFile,
                        StringConstants.JAVA_LANG_FLOAT, "NaN", "F");
                }
                else if (value == Float.MAX_VALUE)
                {
                    Write(
                        loader, printer, referenceMap, classFile,
                        StringConstants.JAVA_LANG_FLOAT, StringConstants.MAX_VALUE, "F");
                }
                /* else if (value == Float.MIN_NORMAL)
                {
                    Write(
                        loader, printer, referenceMap, classFile,
                        StringConstants.JAVA_LANG_FLOAT, "MIN_NORMAL", "F");
                } */
                else if (value == Float.MIN_VALUE)
                {
                    Write(
                        loader, printer, referenceMap, classFile,
                        StringConstants.JAVA_LANG_FLOAT, StringConstants.MIN_VALUE, "F");
                }
                else
                {
                    // TODO Conversion de la valeur en constante ?
                    String s = String.valueOf(value);
                    if (s.indexOf('.') == -1)
                        s += ".0";
                    printer.printNumeric(s + 'F');
                }
            }
            break;
          case "ConstantInteger":
            {
                int value = ((ConstantInteger)cv).getValue();

                switch (constantIntegerType)
                {
                case 'Z':
                    {
                        printer.printKeyword((value == 0) ? "false" : "true");
                    }
                    break;
                case 'C':
                    {
                        String escapedString = StringUtil.EscapeCharAndAppendApostrophe((char)value);
                        String scopeInternalName = classFile.getThisClassName();
                        printer.printString(escapedString, scopeInternalName);
                    }
                    break;
                default:
                    {
                        if (value == Integer.MIN_VALUE)
                        {
                            Write(
                                loader, printer, referenceMap, classFile,
                                StringConstants.JAVA_LANG_INTEGER, StringConstants.MIN_VALUE, "I");
                        }
                        else if (value == Integer.MAX_VALUE)
                        {
                            Write(
                                loader, printer, referenceMap, classFile,
                                StringConstants.JAVA_LANG_INTEGER, StringConstants.MAX_VALUE, "I");
                        }
                        else
                        {
                            printer.printNumeric(String.valueOf(value));
                        }
                    }
                }
            }
            break;
          case "ConstantLong":
            {
                long value = ((ConstantLong)cv).getValue();

                if (value == Long.MIN_VALUE)
                {
                    Write(
                        loader, printer, referenceMap, classFile,
                        StringConstants.JAVA_LANG_LONG, StringConstants.MIN_VALUE, "J");
                }
                else if (value == Long.MAX_VALUE)
                {
                    Write(
                        loader, printer, referenceMap, classFile,
                        StringConstants.JAVA_LANG_LONG, StringConstants.MAX_VALUE, "J");
                }
                else
                {
                    printer.printNumeric(String.valueOf(value) + 'L');
                }
            }
            break;
          case "ConstantString":
            {
                String s = constants.getConstantUtf8(
                    ((ConstantString)cv).getStringIndex());
                String escapedString =
                    StringUtil.EscapeStringAndAppendQuotationMark(s);
                String scopeInternalName = classFile.getThisClassName();
                printer.printString(escapedString, scopeInternalName);
            }
            break;
        }
    }

    private static void Write(
        Loader loader, Printer printer, ReferenceMap referenceMap,
        ClassFile classFile, String internalTypeName,
        String name, String descriptor)
    {
        String className = SignatureWriter.InternalClassNameToClassName(
            loader, referenceMap, classFile, internalTypeName);
        String scopeInternalName = classFile.getThisClassName();
        printer.printType(internalTypeName, className, scopeInternalName);
        printer.print('.');
        printer.printStaticField(internalTypeName, name, descriptor, scopeInternalName);
    }

    public static void WriteHexa(
        Loader loader, Printer printer, ReferenceMap referenceMap,
        ClassFile classFile, Constant cv)
    {
        switch (cv.getClass().getSimpleName())
        {
        case "ConstantInteger":
            printer.printNumeric(
                "0x" + Integer.toHexString( ((ConstantInteger)cv).getValue() ).toUpperCase());
            break;
        case "ConstantLong":
            printer.printNumeric(
                "0x" + Long.toHexString( ((ConstantLong)cv).getValue() ).toUpperCase());
            break;
        default:
            Write(loader, printer, referenceMap, classFile, cv, (byte)0);
        }
    }
}
