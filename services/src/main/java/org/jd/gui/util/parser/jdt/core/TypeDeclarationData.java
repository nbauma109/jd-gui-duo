package org.jd.gui.util.parser.jdt.core;

import jd.core.links.DeclarationData;

public class TypeDeclarationData extends DeclarationData {
    private final String superTypeName;

    public TypeDeclarationData(int startPosition, int length, String type, String superTypeName) {
        super(startPosition, length, type, null, null);

        this.superTypeName = superTypeName;
    }

    public String getSuperTypeName() {
        return superTypeName;
    }
}
