package org.jd.gui.util.parser.jdt.core;

public class DeclarationData {
	
    private int startPosition;
    private int endPosition;
    private String typeName;
    /**
     * Field or method name or null for type
     */
    private String name;
    private String descriptor;

    public DeclarationData(int startPosition, int length, String typeName, String name, String descriptor) {
        this.startPosition = startPosition;
        this.endPosition = startPosition + length;
        this.typeName = typeName;
        this.name = name;
        this.descriptor = descriptor;
    }

    public boolean isAType() { return getName() == null; }
    public boolean isAField() { return (descriptor != null) && descriptor.charAt(0) != '('; }
    public boolean isAMethod() { return (descriptor != null) && descriptor.charAt(0) == '('; }
    public boolean isAConstructor() { return "<init>".equals(getName()); }

	public int getStartPosition() {
		return startPosition;
	}

	public int getEndPosition() {
		return endPosition;
	}

	public String getTypeName() {
		return typeName;
	}

	public String getName() {
		return name;
	}
}