package org.jd.gui.util;

public final class Key {

    private Key() {
    }

    public static String key(CharSequence internalName, CharSequence name, CharSequence descriptor) {
        return String.join("-", internalName, name, descriptor);
    }

    public static String key(CharSequence internalName, CharSequence name, CharSequence descriptor, CharSequence scopeInternalName) {
        return String.join("-", internalName, name, descriptor, scopeInternalName);
    }
}
