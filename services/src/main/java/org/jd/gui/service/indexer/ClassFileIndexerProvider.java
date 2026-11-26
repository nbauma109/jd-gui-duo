/*
 * © 2008-2019 Emmanuel Dupuy
 * © 2022-2026 Nicolas Baumann (@nbauma109)
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
 * Indexes class files using a separate context for each invocation.
 */
public class ClassFileIndexerProvider extends AbstractIndexerProvider {
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

    @Override
    public void index(API api, Container.Entry entry, Indexes indexes, DoubleSupplier getProgressFunction, DoubleConsumer setProgressFunction, BooleanSupplier isCancelledFunction) {
        new IndexingContext().index(entry, indexes, getProgressFunction, setProgressFunction);
    }

    protected static class IndexingContext {
        private final Set<String> typeDeclarationSet = new HashSet<>();
        private final Set<String> constructorDeclarationSet = new HashSet<>();
        private final Set<String> methodDeclarationSet = new HashSet<>();
        private final Set<String> fieldDeclarationSet = new HashSet<>();
        private final Set<String> typeReferenceSet = new HashSet<>();
        private final Set<String> constructorReferenceSet = new HashSet<>();
        private final Set<String> methodReferenceSet = new HashSet<>();
        private final Set<String> fieldReferenceSet = new HashSet<>();
        private final Set<String> stringSet = new HashSet<>();
        private final Set<String> superTypeNameSet = new HashSet<>();
        private final Set<String> descriptorSet = new HashSet<>();

        private final ClassIndexer classIndexer = new ClassIndexer();
        private final SignatureIndexer signatureIndexer = new SignatureIndexer();

        @SuppressWarnings("unchecked")
        public void index(Container.Entry entry, Indexes indexes, DoubleSupplier getProgressFunction, DoubleConsumer setProgressFunction) {
            try (InputStream inputStream = entry.getInputStream()) {
                // Index field, method, interfaces & super type
                ClassReader classReader = new ClassReader(inputStream);
                classReader.accept(classIndexer, SKIP_CODE | SKIP_DEBUG | SKIP_FRAMES);

                // Index descriptors
                for (String descriptor : descriptorSet) {
                    new SignatureReader(descriptor).accept(signatureIndexer);
                }

                indexReferences(classReader);

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

        private void indexReferences(ClassReader classReader) {
            char[] buffer = new char[classReader.getMaxStringLength()];
            for (int i = classReader.getItemCount() - 1; i > 0; i--) {
                int startIndex = classReader.getItem(i);
                if (startIndex != 0) {
                    indexReference(classReader, buffer, startIndex);
                }
            }
        }

        private void indexReference(ClassReader classReader, char[] buffer, int startIndex) {
            int tag = classReader.readByte(startIndex - 1);

            switch (tag) {
            case CONSTANT_Class:
                indexClass(classReader.readUTF8(startIndex, buffer));
                break;
            case CONSTANT_String:
                stringSet.add(classReader.readUTF8(startIndex, buffer));
                break;
            case CONSTANT_Fieldref:
                String fieldName = memberName(classReader, buffer, startIndex);
                if (fieldName != null) {
                    fieldReferenceSet.add(fieldName);
                }
                break;
            case CONSTANT_Methodref, CONSTANT_InterfaceMethodref:
                indexMethodReference(classReader, buffer, startIndex);
                break;
            default:
                // the other constants refer to nothing that is searched
                break;
            }
        }

        private void indexClass(String className) {
            if (className.startsWith("[")) {
                new SignatureReader(className).acceptType(signatureIndexer);
            } else {
                typeReferenceSet.add(className);
            }
        }

        /** The name of the member that a reference to a field or to a method names; null when it has no name and type. */
        private static String memberName(ClassReader classReader, char[] buffer, int startIndex) {
            int nameAndTypeIndex = classReader.getItem(classReader.readUnsignedShort(startIndex + 2));

            if (classReader.readByte(nameAndTypeIndex - 1) != CONSTANT_NameAndType) {
                return null;
            }
            return classReader.readUTF8(nameAndTypeIndex, buffer);
        }

        private void indexMethodReference(ClassReader classReader, char[] buffer, int startIndex) {
            String methodName = memberName(classReader, buffer, startIndex);

            if (StringConstants.INSTANCE_CONSTRUCTOR.equals(methodName)) {
                int classIndex = classReader.getItem(classReader.readUnsignedShort(startIndex));
                constructorReferenceSet.add(classReader.readUTF8(classIndex, buffer));
            } else if (methodName != null) {
                methodReferenceSet.add(methodName);
            }
        }

        protected class ClassIndexer extends ClassVisitor {
            private final AnnotationIndexer annotationIndexer = new AnnotationIndexer();
            private final FieldIndexer fieldIndexer = new FieldIndexer(annotationIndexer);
            private final MethodIndexer methodIndexer = new MethodIndexer(annotationIndexer);

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
            private final AnnotationIndexer annotationIndexer;

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
            private final AnnotationIndexer annotationIndexer;

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
}
