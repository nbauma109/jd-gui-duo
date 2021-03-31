/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.util.decompiler;

import jd.core.printer.Printer;

public abstract class ClassFileSourcePrinter implements Printer {
    protected static final String TAB = "    ";
    protected static final String NEWLINE = "\n";

    protected int maxLineNumber = 0;
    protected int indentationCount;
    protected boolean display;

    protected abstract boolean getRealignmentLineNumber();
    protected abstract boolean isShowPrefixThis();
    protected abstract boolean isUnicodeEscape();

    protected abstract void append(char c);
    protected abstract void append(String s);

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - //

    @Override
	public void print(byte b) { append(String.valueOf(b)); }
    @Override
	public void print(int i) { append(String.valueOf(i)); }

    @Override
	public void print(char c) {
        if (this.display) {
			append(c);
		}
    }

    @Override
	public void print(String s) {
        if (this.display) {
			printEscape(s);
		}
    }

    @Override
	public void printNumeric(String s) { append(s); }

    @Override
	public void printString(String s, String scopeInternalName)  { append(s); }

    @Override
	public void printKeyword(String s) {
        if (this.display) {
			append(s);
		}
    }

    @Override
	public void printJavaWord(String s) { append(s); }

    @Override
	public void printType(String internalName, String name, String scopeInternalName) {
        if (this.display) {
			printEscape(name);
		}
    }

    @Override
	public void printTypeDeclaration(String internalName, String name) {
        printEscape(name);
    }

    @Override
	public void printTypeImport(String internalName, String name) {
        printEscape(name);
    }

    @Override
	public void printField(String internalName, String name, String descriptor, String scopeInternalName) {
        printEscape(name);
    }
    @Override
	public void printFieldDeclaration(String internalName, String name, String descriptor) {
        printEscape(name);
    }

    @Override
	public void printStaticField(String internalName, String name, String descriptor, String scopeInternalName) {
        printEscape(name);
    }
    @Override
	public void printStaticFieldDeclaration(String internalName, String name, String descriptor) {
        printEscape(name);
    }

    @Override
	public void printConstructor(String internalName, String name, String descriptor, String scopeInternalName) {
        printEscape(name);
    }
    @Override
	public void printConstructorDeclaration(String internalName, String name, String descriptor) {
        printEscape(name);
    }

    @Override
	public void printStaticConstructorDeclaration(String internalName, String name) {
        append(name);
    }

    @Override
	public void printMethod(String internalName, String name, String descriptor, String scopeInternalName) {
        printEscape(name);
    }
    @Override
	public void printMethodDeclaration(String internalName, String name, String descriptor) {
        printEscape(name);
    }

    @Override
	public void printStaticMethod(String internalName, String name, String descriptor, String scopeInternalName) {
        printEscape(name);
    }
    @Override
	public void printStaticMethodDeclaration(String internalName, String name, String descriptor) {
        printEscape(name);
    }

    @Override
	public void start(int maxLineNumber, int majorVersion, int minorVersion) {
        this.indentationCount = 0;
        this.display = true;
        this.maxLineNumber = maxLineNumber;
    }

    @Override
	public void end() {}

    @Override
	public void indent() {
        this.indentationCount++;
    }
    @Override
	public void desindent() {
        if (this.indentationCount > 0) {
			this.indentationCount--;
		}
    }

    @Override
	public void startOfLine(int lineNumber) {
        for (int i=0; i<indentationCount; i++) {
			append(TAB);
		}
    }

    @Override
	public void endOfLine() { append(NEWLINE); }

    @Override
	public void extraLine(int count) {
        if (getRealignmentLineNumber()) {
            while (count-- > 0) {
                append(NEWLINE);
            }
        }
    }

    @Override
	public void startOfComment() {}
    @Override
	public void endOfComment() {}

    @Override
	public void startOfJavadoc() {}
    @Override
	public void endOfJavadoc() {}

    @Override
	public void startOfXdoclet() {}
    @Override
	public void endOfXdoclet() {}

    @Override
	public void startOfError() {}
    @Override
	public void endOfError() {}

    @Override
	public void startOfImportStatements() {}
    @Override
	public void endOfImportStatements() {}

    @Override
	public void startOfTypeDeclaration(String internalPath) {}
    @Override
	public void endOfTypeDeclaration() {}

    @Override
	public void startOfAnnotationName() {}
    @Override
	public void endOfAnnotationName() {}

    @Override
	public void startOfOptionalPrefix() {
        if (!isShowPrefixThis()) {
			this.display = false;
		}
    }

    @Override
	public void endOfOptionalPrefix() {
        this.display = true;
    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - //

    @Override
	public void debugStartOfLayoutBlock() {}
    @Override
	public void debugEndOfLayoutBlock() {}

    @Override
	public void debugStartOfSeparatorLayoutBlock() {}
    @Override
	public void debugEndOfSeparatorLayoutBlock(int min, int value, int max) {}

    @Override
	public void debugStartOfStatementsBlockLayoutBlock() {}
    @Override
	public void debugEndOfStatementsBlockLayoutBlock(int min, int value, int max) {}

    @Override
	public void debugStartOfInstructionBlockLayoutBlock() {}
    @Override
	public void debugEndOfInstructionBlockLayoutBlock() {}

    @Override
	public void debugStartOfCommentDeprecatedLayoutBlock() {}
    @Override
	public void debugEndOfCommentDeprecatedLayoutBlock() {}

    @Override
	public void debugMarker(String marker) {}

    @Override
	public void debugStartOfCaseBlockLayoutBlock() {}
    @Override
	public void debugEndOfCaseBlockLayoutBlock() {}

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - //

    protected void printEscape(String s) {
        if (isUnicodeEscape()) {
            int length = s.length();

            for (int i=0; i<length; i++) {
                char c = s.charAt(i);

                if (c == '\t') {
                    append(c);
                } else if (c < 32) {
                    // Write octal format
                    append("\\0");
                    append((char) ('0' + (c >> 3)));
                    append((char) ('0' + (c & 0x7)));
                } else if (c > 127) {
                    // Write octal format
                    append("\\u");

                    int z = (c >> 12);
                    append((char) ((z <= 9) ? ('0' + z) : (('A' - 10) + z)));
                    z = ((c >> 8) & 0xF);
                    append((char) ((z <= 9) ? ('0' + z) : (('A' - 10) + z)));
                    z = ((c >> 4) & 0xF);
                    append((char) ((z <= 9) ? ('0' + z) : (('A' - 10) + z)));
                    z = (c & 0xF);
                    append((char) ((z <= 9) ? ('0' + z) : (('A' - 10) + z)));
                } else {
                    append(c);
                }
            }
        } else {
            append(s);
        }
    }
}
