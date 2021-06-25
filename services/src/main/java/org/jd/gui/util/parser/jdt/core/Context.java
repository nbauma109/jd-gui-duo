package org.jd.gui.util.parser.jdt.core;

import java.util.HashMap;
import java.util.Map;

public class Context {
    protected Context outerContext;

    protected Map<String, String> nameToDescriptor = new HashMap<>();

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
