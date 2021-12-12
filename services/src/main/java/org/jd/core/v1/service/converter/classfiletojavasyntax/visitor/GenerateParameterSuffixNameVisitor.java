/*
 * Copyright (c) 2008, 2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.core.v1.service.converter.classfiletojavasyntax.visitor;

import org.jd.core.v1.model.javasyntax.type.*;

import static org.jd.core.v1.model.javasyntax.type.PrimitiveType.*;

public class GenerateParameterSuffixNameVisitor extends AbstractNopTypeArgumentVisitor {
    protected String suffix;

    public String getSuffix() {
        return suffix;
    }

    @Override
    public void visit(PrimitiveType type) {
        suffix = switch (type.getJavaPrimitiveFlags()) {
		case FLAG_BYTE -> "Byte";
		case FLAG_CHAR -> "Char";
		case FLAG_DOUBLE -> "Double";
		case FLAG_FLOAT -> "Float";
		case FLAG_INT -> "Int";
		case FLAG_LONG -> "Long";
		case FLAG_SHORT -> "Short";
		case FLAG_BOOLEAN -> "Boolean";
		default -> throw new IllegalStateException();
		};
    }

    @Override
    public void visit(ObjectType type) {
        suffix = type.getName();
    }

    @Override
    public void visit(InnerObjectType type) {
        suffix = type.getName();
    }

    @Override
    public void visit(GenericType type) {
        suffix = type.getName();
    }
}
