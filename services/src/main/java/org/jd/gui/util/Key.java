package org.jd.gui.util;

public class Key {
	
	public static String key(CharSequence internalName, CharSequence name, CharSequence descriptor) {
		return String.join("-", internalName, name, descriptor);
	}

	public static String key(CharSequence internalName, CharSequence name, CharSequence descriptor, CharSequence scopeInternalName) {
		return String.join("-", internalName, name, descriptor, scopeInternalName);
	}
}
