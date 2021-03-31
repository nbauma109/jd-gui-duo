package org.jd.gui.util.parser.jdt.core;

public class ReferenceData {
	
	private String typeName;
    /**
     * Field or method name or null for type
     */
    private String name;
    /**
     * Field or method descriptor or null for type
     */
    private String descriptor;
    /**
     * Internal type name containing reference or null for "import" statement.
     * Used to high light items matching with URI like "file://dir1/dir2/file?highlightPattern=hello&highlightFlags=drtcmfs&highlightScope=type".
     */
    private String owner;
    /**
     * "Enabled" flag for link of reference
     */
    private boolean enabled = false;

    public ReferenceData(String typeName, String name, String descriptor, String owner) {
        this.setTypeName(typeName);
        this.name = name;
        this.descriptor = descriptor;
        this.owner = owner;
    }

    public boolean isAType() { return getName() == null; }
    public boolean isAField() { return (getDescriptor() != null) && getDescriptor().charAt(0) != '('; }
    public boolean isAMethod() { return (getDescriptor() != null) && getDescriptor().charAt(0) == '('; }
    public boolean isAConstructor() { return "<init>".equals(getName()); }

	public void setTypeName(String typeName) {
		this.typeName = typeName;
	}
	
	public String getTypeName() {
		return typeName;
	}

	public String getName() {
		return name;
	}

	public String getDescriptor() {
		return descriptor;
	}

	public String getOwner() {
		return owner;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

}