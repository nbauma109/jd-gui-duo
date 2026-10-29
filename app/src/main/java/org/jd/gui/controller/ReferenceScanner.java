/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */
package org.jd.gui.controller;

import org.jd.core.v1.util.StringConstants;
import org.jd.gui.view.SearchInConstantPoolsView;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Finds which methods and fields of a class file hold a reference matching a search, using the
 * same rules as the constant pool indexes: types and constructors match on the type simple name,
 * methods and fields on their name, strings on the string constant.
 */
final class ReferenceScanner {

    /** Members (as {@code name + descriptor}) that contain at least one matching reference. */
    record Result(Set<String> methods, Set<String> fields) {
        static final Result EMPTY = new Result(Set.of(), Set.of());
    }

    private ReferenceScanner() {
    }

    static Result scan(InputStream inputStream, Pattern pattern, int flags) throws IOException {
        Visitor visitor = new Visitor(pattern, flags);
        new ClassReader(inputStream).accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return new Result(visitor.methods, visitor.fields);
    }

    private static final class Visitor extends ClassVisitor {
        private final Pattern pattern;
        private final int flags;
        private final Set<String> methods = new HashSet<>();
        private final Set<String> fields = new HashSet<>();

        Visitor(Pattern pattern, int flags) {
            super(Opcodes.ASM9);
            this.pattern = pattern;
            this.flags = flags;
        }

        private boolean matches(String text) {
            return pattern.matcher(text).matches();
        }

        private boolean matchesType(String internalName) {
            if (internalName == null || (flags & SearchInConstantPoolsView.SEARCH_TYPE) == 0) {
                return false;
            }
            if (internalName.startsWith("[")) {
                return matchesType(Type.getType(internalName));
            }
            return matches(simpleName(internalName));
        }

        private boolean matchesType(Type type) {
            if ((flags & SearchInConstantPoolsView.SEARCH_TYPE) == 0) {
                return false;
            }
            if (type.getSort() == Type.METHOD) {
                for (Type argument : type.getArgumentTypes()) {
                    if (matchesType(argument)) {
                        return true;
                    }
                }
                return matchesType(type.getReturnType());
            }
            Type element = type.getSort() == Type.ARRAY ? type.getElementType() : type;
            return element.getSort() == Type.OBJECT && matches(simpleName(element.getInternalName()));
        }

        private boolean matchesMethod(String owner, String name) {
            if (StringConstants.INSTANCE_CONSTRUCTOR.equals(name)) {
                return (flags & SearchInConstantPoolsView.SEARCH_CONSTRUCTOR) != 0 && matches(simpleName(owner));
            }
            return (flags & SearchInConstantPoolsView.SEARCH_METHOD) != 0 && matches(name);
        }

        private boolean matchesField(String name) {
            return (flags & SearchInConstantPoolsView.SEARCH_FIELD) != 0 && matches(name);
        }

        private boolean matchesHandle(Handle handle) {
            boolean isField = handle.getTag() <= Opcodes.H_PUTSTATIC;
            return matchesType(handle.getOwner()) || (isField ? matchesField(handle.getName()) : matchesMethod(handle.getOwner(), handle.getName()));
        }

        private boolean matchesConstant(Object constant) {
            if (constant instanceof String string) {
                return (flags & SearchInConstantPoolsView.SEARCH_STRING) != 0 && matches(string);
            }
            if (constant instanceof Type type) {
                return matchesType(type);
            }
            return constant instanceof Handle handle && matchesHandle(handle);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            if (matchesType(Type.getType(descriptor)) || matchesConstant(value)) {
                fields.add(name + descriptor);
            }
            return null;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            String key = name + descriptor;
            if (matchesType(Type.getMethodType(descriptor))) {
                methods.add(key);
            }
            if (exceptions != null) {
                for (String exception : exceptions) {
                    if (matchesType(exception)) {
                        methods.add(key);
                    }
                }
            }
            return new MethodVisitor(Opcodes.ASM9) {
                private void hit() {
                    methods.add(key);
                }

                @Override
                public void visitTypeInsn(int opcode, String type) {
                    if (matchesType(type)) {
                        hit();
                    }
                }

                @Override
                public void visitFieldInsn(int opcode, String owner, String fieldName, String fieldDescriptor) {
                    if (matchesType(owner) || matchesField(fieldName) || matchesType(Type.getType(fieldDescriptor))) {
                        hit();
                    }
                }

                @Override
                public void visitMethodInsn(int opcode, String owner, String methodName, String methodDescriptor, boolean isInterface) {
                    if (matchesType(owner) || matchesMethod(owner, methodName)) {
                        hit();
                    }
                }

                @Override
                public void visitInvokeDynamicInsn(String indyName, String indyDescriptor, Handle bootstrap, Object... arguments) {
                    for (Object argument : arguments) {
                        if (matchesConstant(argument)) {
                            hit();
                        }
                    }
                }

                @Override
                public void visitLdcInsn(Object value) {
                    if (matchesConstant(value)) {
                        hit();
                    }
                }

                @Override
                public void visitMultiANewArrayInsn(String arrayDescriptor, int dimensions) {
                    if (matchesType(Type.getType(arrayDescriptor))) {
                        hit();
                    }
                }

                @Override
                public void visitTryCatchBlock(org.objectweb.asm.Label start, org.objectweb.asm.Label end, org.objectweb.asm.Label handler, String type) {
                    if (matchesType(type)) {
                        hit();
                    }
                }
            };
        }

        private static String simpleName(String internalName) {
            return internalName.substring(Math.max(internalName.lastIndexOf('/'), internalName.lastIndexOf('$')) + 1);
        }
    }
}
