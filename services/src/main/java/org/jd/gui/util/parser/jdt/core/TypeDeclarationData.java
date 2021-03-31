package org.jd.gui.util.parser.jdt.core;

public class TypeDeclarationData extends DeclarationData {
    protected String superTypeName;

    public TypeDeclarationData(int startPosition, int length, String type, String superTypeName) {
        super(startPosition, length, type, null, null);

        this.superTypeName = superTypeName;
    }

	public String getSuperTypeName() {
		return superTypeName;
	}
}