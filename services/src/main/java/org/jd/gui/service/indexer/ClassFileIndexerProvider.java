/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.indexer;

import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.core.v1.util.StringConstants;
import org.jd.gui.api.API;
import org.jd.gui.api.model.Container;
import org.jd.gui.api.model.Indexes;
import org.jd.gui.util.ProgressUtil;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.TypePath;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;

import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.regex.Pattern;

import static org.apache.bcel.Const.CONSTANT_Class;
import static org.apache.bcel.Const.CONSTANT_Fieldref;
import static org.apache.bcel.Const.CONSTANT_InterfaceMethodref;
import static org.apache.bcel.Const.CONSTANT_Methodref;
import static org.apache.bcel.Const.CONSTANT_NameAndType;
import static org.apache.bcel.Const.CONSTANT_String;
import static org.objectweb.asm.ClassReader.SKIP_CODE;
import static org.objectweb.asm.ClassReader.SKIP_DEBUG;
import static org.objectweb.asm.ClassReader.SKIP_FRAMES;

/**
 * Unsafe thread implementation of class file indexer.
 */
public class ClassFileIndexerProvider extends AbstractIndexerProvider {
    protected Set<String> typeDeclarationSet = new HashSet<>();
    protected Set<String> constructorDeclarationSet = new HashSet<>();
    protected Set<String> methodDeclarationSet = new HashSet<>();
    protected Set<String> fieldDeclarationSet = new HashSet<>();
    protected Set<String> typeReferenceSet = new HashSet<>();
    protected Set<String> constructorReferenceSet = new HashSet<>();
    protected Set<String> methodReferenceSet = new HashSet<>();
    protected Set<String> fieldReferenceSet = new HashSet<>();
    protected Set<String> stringSet = new HashSet<>();
    protected Set<String> superTypeNameSet = new HashSet<>();
    protected Set<String> descriptorSet = new HashSet<>();

    protected ClassIndexer classIndexer = new ClassIndexer();
    protected SignatureIndexer signatureIndexer = new SignatureIndexer();

    @Override
    public String[] getSelectors() {
        return appendSelectors("*:file:*.class");
    }

    @Override
    public Pattern getPathPattern() {
        if (externalPathPattern == null) {
            return Pattern.compile("^((?!module-info\\.class).)*$");
        }
        return externalPathPattern;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void index(API api, Container.Entry entry, Indexes indexes, DoubleSupplier getProgressFunction, DoubleConsumer setProgressFunction, BooleanSupplier isCancelledFunction) {
        // Cleaning sets...
        typeDeclarationSet.clear();
        constructorDeclarationSet.clear();
        methodDeclarationSet.clear();
        fieldDeclarationSet.clear();
        typeReferenceSet.clear();
        constructorReferenceSet.clear();
        methodReferenceSet.clear();
        fieldReferenceSet.clear();
        stringSet.clear();
        superTypeNameSet.clear();
        descriptorSet.clear();

        try (InputStream inputStream = entry.getInputStream()) {
            // Index field, method, interfaces & super type
            ClassReader classReader = new ClassReader(inputStream);
            classReader.accept(classIndexer, SKIP_CODE | SKIP_DEBUG | SKIP_FRAMES);

            // Index descriptors
            for (String descriptor : descriptorSet) {
                new SignatureReader(descriptor).accept(signatureIndexer);
            }

            // Index references
            char[] buffer = new char[classReader.getMaxStringLength()];

            for (int i = classReader.getItemCount() - 1; i > 0; i--) {
                int startIndex = classReader.getItem(i);

                if (startIndex != 0) {
                    int tag = classReader.readByte(startIndex - 1);

                    switch (tag) {
                    case CONSTANT_Class:
                        String className = classReader.readUTF8(startIndex, buffer);
                        if (className.startsWith("[")) {
                            new SignatureReader(className).acceptType(signatureIndexer);
                        } else {
                            typeReferenceSet.add(className);
                        }
                        break;
                    case CONSTANT_String:
                        String str = classReader.readUTF8(startIndex, buffer);
                        stringSet.add(str);
                        break;
                    case CONSTANT_Fieldref:
                        int nameAndTypeItem = classReader.readUnsignedShort(startIndex + 2);
                        int nameAndTypeIndex = classReader.getItem(nameAndTypeItem);
                        tag = classReader.readByte(nameAndTypeIndex - 1);
                        if (tag == CONSTANT_NameAndType) {
                            String fieldName = classReader.readUTF8(nameAndTypeIndex, buffer);
                            fieldReferenceSet.add(fieldName);
                        }
                        break;
                    case CONSTANT_Methodref, CONSTANT_InterfaceMethodref:
                        nameAndTypeItem = classReader.readUnsignedShort(startIndex + 2);
                        nameAndTypeIndex = classReader.getItem(nameAndTypeItem);
                        tag = classReader.readByte(nameAndTypeIndex - 1);
                        if (tag == CONSTANT_NameAndType) {
                            String methodName = classReader.readUTF8(nameAndTypeIndex, buffer);
                            if (StringConstants.INSTANCE_CONSTRUCTOR.equals(methodName)) {
                                int classItem = classReader.readUnsignedShort(startIndex);
                                int classIndex = classReader.getItem(classItem);
                                className = classReader.readUTF8(classIndex, buffer);
                                constructorReferenceSet.add(className);
                            } else {
                                methodReferenceSet.add(methodName);
                            }
                        }
                        break;
                    }
                }
            }

            String typeName = classIndexer.name;

            // Append sets to indexes
            addToIndexes(indexes, "typeDeclarations", typeDeclarationSet, entry);
            addToIndexes(indexes, "constructorDeclarations", constructorDeclarationSet, entry);
            addToIndexes(indexes, "methodDeclarations", methodDeclarationSet, entry);
            addToIndexes(indexes, "fieldDeclarations", fieldDeclarationSet, entry);
            addToIndexes(indexes, "typeReferences", typeReferenceSet, entry);
            addToIndexes(indexes, "constructorReferences", constructorReferenceSet, entry);
            addToIndexes(indexes, "methodReferences", methodReferenceSet, entry);
            addToIndexes(indexes, "fieldReferences", fieldReferenceSet, entry);
            addToIndexes(indexes, "strings", stringSet, entry);

            // Populate map [super type name : [sub type name]]
            if (!superTypeNameSet.isEmpty()) {
                @SuppressWarnings("rawtypes")
                Map<String, Collection> index = indexes.getIndex("subTypeNames");

                for (String superTypeName : superTypeNameSet) {
                    index.get(superTypeName).add(typeName);
                }
            }

            ProgressUtil.updateProgress(entry, getProgressFunction, setProgressFunction);

        } catch (Exception e) {
            assert ExceptionUtil.printStackTrace(e);
        }
    }

    protected class ClassIndexer extends ClassVisitor {
        private AnnotationIndexer annotationIndexer = new AnnotationIndexer();
        private FieldIndexer fieldIndexer = new FieldIndexer(annotationIndexer);
        private MethodIndexer methodIndexer = new MethodIndexer(annotationIndexer);

        private String name;

        public ClassIndexer() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.name = name;
            typeDeclarationSet.add(name);

            if (superName != null) {
                superTypeNameSet.add(superName);
            }

            if (interfaces != null) {
                Collections.addAll(superTypeNameSet, interfaces);
            }
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            descriptorSet.add(desc);
            return annotationIndexer;
        }

        @Override
        public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
            descriptorSet.add(desc);
            return annotationIndexer;
        }

        @Override
        public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
            fieldDeclarationSet.add(name);
            descriptorSet.add(signature == null ? desc : signature);
            return fieldIndexer;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            if (StringConstants.INSTANCE_CONSTRUCTOR.equals(name)) {
                constructorDeclarationSet.add(this.name);
            } else if (!"<clinit>".equals(name)) {
                methodDeclarationSet.add(name);
            }

            descriptorSet.add(signature == null ? desc : signature);

            if (exceptions != null) {
                Collections.addAll(typeReferenceSet, exceptions);
            }
            return methodIndexer;
        }
    }

    protected class SignatureIndexer extends SignatureVisitor {
        SignatureIndexer() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visitClassType(String name) {
            typeReferenceSet.add(name);
        }
    }

    protected class AnnotationIndexer extends AnnotationVisitor {
        public AnnotationIndexer() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visitEnum(String name, String desc, String value) {
            descriptorSet.add(desc);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String name, String desc) {
            descriptorSet.add(desc);
            return this;
        }
    }

    protected class FieldIndexer extends FieldVisitor {
        private AnnotationIndexer annotationIndexer;

        public FieldIndexer(AnnotationIndexer annotationIndexer) {
            super(Opcodes.ASM9);
            this.annotationIndexer = annotationIndexer;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            descriptorSet.add(desc);
            return annotationIndexer;
        }

        @Override
        public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
            descriptorSet.add(desc);
            return annotationIndexer;
        }
    }

    protected class MethodIndexer extends MethodVisitor {
        private AnnotationIndexer annotationIndexer;

        public MethodIndexer(AnnotationIndexer annotationIndexer) {
            super(Opcodes.ASM9);
            this.annotationIndexer = annotationIndexer;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            descriptorSet.add(desc);
            return annotationIndexer;
        }

        @Override
        public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
            descriptorSet.add(desc);
            return annotationIndexer;
        }

        @Override
        public AnnotationVisitor visitParameterAnnotation(int parameter, String desc, boolean visible) {
            descriptorSet.add(desc);
            return annotationIndexer;
        }
    }
}
