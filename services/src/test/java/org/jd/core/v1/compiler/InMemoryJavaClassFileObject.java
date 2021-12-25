package org.jd.core.v1.compiler;

import java.io.*;
import java.net.URI;

import javax.tools.SimpleJavaFileObject;

public class InMemoryJavaClassFileObject extends SimpleJavaFileObject {
    private ByteArrayOutputStream byteCode;

    public InMemoryJavaClassFileObject(String absClassName) {
        super(URI.create("memory:///" + absClassName.replace('.', '/') + ".class"), Kind.CLASS);
    }

    @Override
    public OutputStream openOutputStream() throws IOException {
        byteCode = new ByteArrayOutputStream();
        return byteCode;
    }

    public byte[] getByteCode() {
        return byteCode.toByteArray();
    }
}
