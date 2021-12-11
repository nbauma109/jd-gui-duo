/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jdv1.gui.service.indexer;

import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.core.v1.util.StringConstants;
import org.jd.gui.api.API;
import org.jd.gui.api.model.Container;
import org.jd.gui.api.model.Indexes;
import org.jd.gui.service.indexer.AbstractIndexerProvider;
import org.objectweb.asm.*;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;

import java.io.InputStream;
import java.util.*;
import java.util.regex.Pattern;

import static org.apache.bcel.Const.*;
import static org.objectweb.asm.ClassReader.*;

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
	public void index(API api, Container.Entry entry, Indexes indexes) {
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
		} catch (Exception e) {
			assert ExceptionUtil.printStackTrace(e);
		}
	}

	protected class ClassIndexer extends ClassVisitor {
		protected AnnotationIndexer annotationIndexer = new AnnotationIndexer();
		protected FieldIndexer fieldIndexer = new FieldIndexer(annotationIndexer);
		protected MethodIndexer methodIndexer = new MethodIndexer(annotationIndexer);

		protected String name;

		public ClassIndexer() {
			super(Opcodes.ASM7);
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
			super(Opcodes.ASM7);
		}

		@Override
		public void visitClassType(String name) {
			typeReferenceSet.add(name);
		}
	}

	protected class AnnotationIndexer extends AnnotationVisitor {
		public AnnotationIndexer() {
			super(Opcodes.ASM7);
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
		protected AnnotationIndexer annotationIndexer;

		public FieldIndexer(AnnotationIndexer annotationIndexer) {
			super(Opcodes.ASM7);
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
		protected AnnotationIndexer annotationIndexer;

		public MethodIndexer(AnnotationIndexer annotationIndexer) {
			super(Opcodes.ASM7);
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
