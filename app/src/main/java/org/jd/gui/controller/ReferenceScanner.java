/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */
package org.jd.gui.controller;

import org.jd.core.v1.util.StringConstants;
import org.jd.gui.view.SearchInConstantPoolsView;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.TypePath;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

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

        /** Type references that only occur in a generic signature (e.g. {@code List<Needle>}) are indexed too. */
        private boolean matchesSignature(String signature, boolean fieldType) {
            if (signature == null || (flags & SearchInConstantPoolsView.SEARCH_TYPE) == 0) {
                return false;
            }
            boolean[] found = new boolean[1];
            SignatureVisitor visitor = new SignatureVisitor(Opcodes.ASM9) {
                @Override
                public void visitClassType(String name) {
                    found[0] |= matches(simpleName(name));
                }

                @Override
                public void visitInnerClassType(String name) {
                    found[0] |= matches(name);
                }
            };
            SignatureReader reader = new SignatureReader(signature);
            if (fieldType) {
                reader.acceptType(visitor);
            } else {
                reader.accept(visitor);
            }
            return found[0];
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
            if (constant instanceof ConstantDynamic dynamic) {
                return matchesDynamic(dynamic);
            }
            return constant instanceof Handle handle && matchesHandle(handle);
        }

        /** A dynamic constant references its bootstrap method and the constants passed to it. */
        private boolean matchesDynamic(ConstantDynamic dynamic) {
            return matchesHandle(dynamic.getBootstrapMethod())
                    || IntStream.range(0, dynamic.getBootstrapMethodArgumentCount()).anyMatch(i -> matchesConstant(dynamic.getBootstrapMethodArgument(i)));
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            String key = name + descriptor;
            if (matchesType(Type.getType(descriptor)) || matchesSignature(signature, true) || matchesConstant(value)) {
                fields.add(key);
            }
            return new FieldVisitor(Opcodes.ASM9) {
                @Override
                public AnnotationVisitor visitAnnotation(String annotationDescriptor, boolean visible) {
                    return annotation(fields, key, annotationDescriptor);
                }

                @Override
                public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String annotationDescriptor, boolean visible) {
                    return annotation(fields, key, annotationDescriptor);
                }
            };
        }

        /** Types used by annotations are indexed too: the owning member holds the reference. */
        private AnnotationVisitor annotation(Set<String> target, String key, String annotationDescriptor) {
            if (matchesType(Type.getType(annotationDescriptor))) {
                target.add(key);
            }
            return new AnnotationMatcher(target, key);
        }

        /** Looks for types in nested annotations, enum constants and class values of an annotation. */
        private final class AnnotationMatcher extends AnnotationVisitor {
            private final Set<String> target;
            private final String key;

            AnnotationMatcher(Set<String> target, String key) {
                super(Opcodes.ASM9);
                this.target = target;
                this.key = key;
            }

            @Override
            public void visit(String name, Object value) {
                if (value instanceof Type type && matchesType(type)) {
                    target.add(key);
                }
            }

            @Override
            public void visitEnum(String name, String enumDescriptor, String value) {
                annotation(target, key, enumDescriptor);
            }

            @Override
            public AnnotationVisitor visitAnnotation(String name, String annotationDescriptor) {
                return annotation(target, key, annotationDescriptor);
            }

            @Override
            public AnnotationVisitor visitArray(String name) {
                return this;
            }
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            String key = name + descriptor;
            if (matchesType(Type.getMethodType(descriptor)) || matchesSignature(signature, false) || matchesAnyType(exceptions)) {
                methods.add(key);
            }
            return new BodyVisitor(key);
        }

        private boolean matchesAnyType(String[] internalNames) {
            return internalNames != null && Arrays.stream(internalNames).anyMatch(this::matchesType);
        }

        /** Records the method as soon as one of its instructions holds a matching reference. */
        private final class BodyVisitor extends MethodVisitor {
            private final String key;

            BodyVisitor(String key) {
                super(Opcodes.ASM9);
                this.key = key;
            }

            private void hitIf(boolean matching) {
                if (matching) {
                    methods.add(key);
                }
            }

            @Override
            public AnnotationVisitor visitAnnotation(String annotationDescriptor, boolean visible) {
                return annotation(methods, key, annotationDescriptor);
            }

            @Override
            public AnnotationVisitor visitParameterAnnotation(int parameter, String annotationDescriptor, boolean visible) {
                return annotation(methods, key, annotationDescriptor);
            }

            @Override
            public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String annotationDescriptor, boolean visible) {
                return annotation(methods, key, annotationDescriptor);
            }

            @Override
            public AnnotationVisitor visitAnnotationDefault() {
                return new AnnotationMatcher(methods, key);
            }

            @Override
            public void visitTypeInsn(int opcode, String type) {
                hitIf(matchesType(type));
            }

            @Override
            public void visitFieldInsn(int opcode, String owner, String fieldName, String fieldDescriptor) {
                hitIf(matchesType(owner) || matchesField(fieldName) || matchesType(Type.getType(fieldDescriptor)));
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String methodName, String methodDescriptor, boolean isInterface) {
                hitIf(matchesType(owner) || matchesMethod(owner, methodName));
            }

            @Override
            public void visitInvokeDynamicInsn(String indyName, String indyDescriptor, Handle bootstrap, Object... arguments) {
                hitIf(matchesHandle(bootstrap) || Arrays.stream(arguments).anyMatch(Visitor.this::matchesConstant));
            }

            @Override
            public void visitLdcInsn(Object value) {
                hitIf(matchesConstant(value));
            }

            @Override
            public void visitMultiANewArrayInsn(String arrayDescriptor, int dimensions) {
                hitIf(matchesType(Type.getType(arrayDescriptor)));
            }

            @Override
            public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
                hitIf(matchesType(type));
            }
        }

        private static String simpleName(String internalName) {
            return internalName.substring(Math.max(internalName.lastIndexOf('/'), internalName.lastIndexOf('$')) + 1);
        }
    }
}
