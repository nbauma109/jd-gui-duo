package org.jd.gui.util.parser.jdt.core;

import java.util.HashMap;

public class Context {
    protected Context outerContext;

    protected HashMap<String, String> nameToDescriptor = new HashMap<>();

    public Context(Context outerContext) {
        this.outerContext = outerContext;
    }

    /**
     * @param name Parameter or variable name
     * @return Qualified type name
     */
    public String getDescriptor(String name) {
        String descriptor = nameToDescriptor.get(name);

        if ((descriptor == null) && (outerContext != null)) {
            descriptor = outerContext.getDescriptor(name);
        }

        return descriptor;
    }

	public String put(String name, String descriptor) {
		return nameToDescriptor.put(name, descriptor);
	}
}
