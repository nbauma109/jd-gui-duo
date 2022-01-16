/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.util.parser.jdt.core;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.ArrayType;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NameQualifiedType;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.jd.core.v1.util.StringConstants;
import org.jd.gui.api.model.Container;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.bcel.Const.ACC_ANNOTATION;
import static org.apache.bcel.Const.ACC_ENUM;
import static org.apache.bcel.Const.ACC_INTERFACE;

public abstract class AbstractJavaListener extends ASTVisitor {
    protected final Container.Entry entry;
    protected String packageName = "";
    protected final Map<String, String> nameToInternalTypeName = new HashMap<>();
    private final Map<String, String> typeNameCache = new HashMap<>();

    protected AbstractJavaListener(Container.Entry entry) {
        this.entry = entry;
    }

    protected static String nameToString(Name name) {
        if (name instanceof SimpleName) { // to convert to jdk16 pattern matching only when spotbugs #1617 and eclipse #577987 are solved
            SimpleName simpleName = (SimpleName) name;
            return simpleName.getIdentifier();
        }
        if (name instanceof QualifiedName) { // to convert to jdk16 pattern matching only when spotbugs #1617 and eclipse #577987 are solved
            QualifiedName qualifiedName = (QualifiedName) name;
            Name qualifier = qualifiedName.getQualifier();
            String identifier = qualifiedName.getName().getIdentifier();
            return String.join("/", nameToString(qualifier), identifier);
        }
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean visit(PackageDeclaration node) {
        packageName = nameToString(node.getName());
        return true;
    }

    @Override
    public boolean visit(ImportDeclaration node) {
        Name name = node.getName();
        if (name instanceof QualifiedName) { // to convert to jdk16 pattern matching only when spotbugs #1617 and eclipse #577987 are solved
            QualifiedName qualifiedName = (QualifiedName) name;
            String simpleName = qualifiedName.getName().getIdentifier();
            String internalTypeName = nameToString(name);
            nameToInternalTypeName.put(simpleName, internalTypeName);
        }
        return true;
    }

    protected abstract boolean enterTypeDeclaration(AbstractTypeDeclaration node, int flag);

    protected abstract void exitTypeDeclaration();

    @Override
    public boolean visit(TypeDeclaration node) {
        return enterTypeDeclaration(node, node.isInterface() ? ACC_INTERFACE : 0);
    }

    @Override
    public boolean visit(EnumDeclaration node) {
        return enterTypeDeclaration(node, ACC_ENUM);
    }

    @Override
    public boolean visit(AnnotationTypeDeclaration node) {
        return enterTypeDeclaration(node, ACC_ANNOTATION);
    }

    @Override
    public void endVisit(TypeDeclaration node) {
        exitTypeDeclaration();
    }

    @Override
    public void endVisit(EnumDeclaration node) {
        exitTypeDeclaration();
    }

    @Override
    public void endVisit(AnnotationTypeDeclaration node) {
        exitTypeDeclaration();
    }

    protected static String typeToString(Type type) {
        if (type instanceof ArrayType) { // to convert to jdk16 pattern matching only when spotbugs #1617 and eclipse #577987 are solved
            ArrayType arrayType = (ArrayType) type;
            return typeToString(arrayType.getElementType());
        }
        if (type instanceof ParameterizedType) { // to convert to jdk16 pattern matching only when spotbugs #1617 and eclipse #577987 are solved
            ParameterizedType parameterizedType = (ParameterizedType) type;
            return typeToString(parameterizedType.getType());
        }
        if (type instanceof QualifiedType) { // to convert to jdk16 pattern matching only when spotbugs #1617 and eclipse #577987 are solved
            QualifiedType qualifiedType = (QualifiedType) type;
            Type qualifierType = qualifiedType.getQualifier();
            String qualifiedIdentifier = qualifiedType.getName().getIdentifier();
            return String.join("/", typeToString(qualifierType), qualifiedIdentifier);
        }
        if (type instanceof SimpleType) { // to convert to jdk16 pattern matching only when spotbugs #1617 and eclipse #577987 are solved
            SimpleType simpleType = (SimpleType) type;
            return nameToString(simpleType.getName());
        }
        if (type instanceof NameQualifiedType) { // to convert to jdk16 pattern matching only when spotbugs #1617 and eclipse #577987 are solved
            NameQualifiedType nameQualifiedType = (NameQualifiedType) type;
            String qualifiedIdentifier = nameQualifiedType.getName().getIdentifier();
            return String.join("/", nameToString(nameQualifiedType.getQualifier()), qualifiedIdentifier);
        }
        throw new UnsupportedOperationException();
    }

    protected String resolveInternalTypeName(Type type) {
        if (type instanceof ArrayType) { // to convert to jdk16 pattern matching only when spotbugs #1617 and eclipse #577987 are solved
            ArrayType arrayType = (ArrayType) type;
            return resolveInternalTypeName(arrayType.getElementType());
        }
        if (type instanceof ParameterizedType) { // to convert to jdk16 pattern matching only when spotbugs #1617 and eclipse #577987 are solved
            ParameterizedType parameterizedType = (ParameterizedType) type;
            return resolveInternalTypeName(parameterizedType.getType());
        }
        if (type instanceof QualifiedType) { // to convert to jdk16 pattern matching only when spotbugs #1617 and eclipse #577987 are solved
            QualifiedType qualifiedType = (QualifiedType) type;
            Type qualifierType = qualifiedType.getQualifier();
            String qualifiedIdentifier = qualifiedType.getName().getIdentifier();
            return String.join("/", resolveInternalTypeName(qualifierType), qualifiedIdentifier);
        }
        if (type instanceof NameQualifiedType) { // to convert to jdk16 pattern matching only when spotbugs #1617 and eclipse #577987 are solved
            NameQualifiedType nameQualifiedType = (NameQualifiedType) type;
            return nameToString(nameQualifiedType.getName());
        }
        if (type instanceof SimpleType) { // to convert to jdk16 pattern matching only when spotbugs #1617 and eclipse #577987 are solved
            SimpleType simpleType = (SimpleType) type;
            Name simpleTypeName = simpleType.getName();
            if (simpleTypeName instanceof SimpleName) { // to convert to jdk16 pattern matching only when spotbugs #1617 and eclipse #577987 are solved
                SimpleName simpleName = (SimpleName) simpleTypeName;
                String name = simpleName.getIdentifier();
                // Search in cache
                String qualifiedName = typeNameCache.get(name);

                if (qualifiedName != null) {
                    return qualifiedName;
                }

                // Search in imports
                String imp = nameToInternalTypeName.get(name);

                if (imp != null) {
                    // Import found
                    return imp;
                }

                // Search type in same package
                String prefix = name + '.';

                if (entry.getPath().indexOf('/') != -1) {
                    // Not in root package
                    Container.Entry parent = entry.getParent();
                    int packageLength = parent.getPath().length() + 1;

                    for (Container.Entry child : parent.getChildren().values()) {
                        if (!child.isDirectory() && child.getPath().startsWith(prefix, packageLength)) {
                            qualifiedName = packageName + '/' + name;
                            typeNameCache.put(name, qualifiedName);
                            return qualifiedName;
                        }
                    }
                }

                // Search type in root package
                for (Container.Entry child : entry.getContainer().getRoot().getChildren().values()) {
                    if (!child.isDirectory() && child.getPath().startsWith(prefix)) {
                        typeNameCache.put(name, name);
                        return name;
                    }
                }

                // Search type in 'java.lang'
                if (getClass().getClassLoader().getResource("java/lang/" + name + StringConstants.CLASS_FILE_SUFFIX) != null) {
                    qualifiedName = "java/lang/" + name;
                    typeNameCache.put(name, qualifiedName);
                    return qualifiedName;
                }

                // Type not found
                qualifiedName = "*/" + name;
                typeNameCache.put(name, qualifiedName);
                return qualifiedName;

            }
            // Qualified type name -> Nothing to do
            return nameToString(simpleTypeName);
        }
        return StringConstants.JAVA_LANG_OBJECT;
    }

    public PrimitiveType getPrimitiveTypeContext(Type type) {
        if (type instanceof ArrayType) { // to convert to jdk16 pattern matching only when spotbugs #1617 and eclipse #577987 are solved
            ArrayType arrayType = (ArrayType) type;
            return getPrimitiveTypeContext(arrayType.getElementType());
        }
        if (type instanceof PrimitiveType) { // to convert to jdk16 pattern matching only when spotbugs #1617 and eclipse #577987 are solved
            PrimitiveType primitiveType = (PrimitiveType) type;
            return primitiveType;
        }
        return null;
    }

    protected String createDescriptor(Type type, int dimension) {
        if (type == null) {
            return "V";
        }
        dimension += countDimension(type);
        String name = null;

        PrimitiveType primitive = getPrimitiveTypeContext(type);
        if (primitive == null) {
            name = "L" + resolveInternalTypeName(type) + ";";
        } else {
            // Search primitive
            name = switch (primitive.getPrimitiveTypeCode().toString()) {
            case "boolean" -> "Z";
            case "byte" -> "B";
            case "char" -> "C";
            case "double" -> "D";
            case "float" -> "F";
            case "int" -> "I";
            case "long" -> "J";
            case "short" -> "S";
            case "void" -> "V";
            default -> throw new IllegalStateException("UNEXPECTED PRIMITIVE");
            };
        }

        switch (dimension) {
        case 0:
            return name;
        case 1:
            return "[" + name;
        case 2:
            return "[[" + name;
        default:
            return new String(new char[dimension]).replace('\0', '[') + name;
        }
    }

    protected String createParamDescriptors(List<SingleVariableDeclaration> formalParameterList) {
        StringBuilder paramDescriptors = null;

        if (formalParameterList != null) {
            paramDescriptors = new StringBuilder("(");

            for (SingleVariableDeclaration formalParameter : formalParameterList) {
                int dimensionOnParameter = formalParameter.getExtraDimensions();
                paramDescriptors.append(createDescriptor(formalParameter.getType(), dimensionOnParameter));
            }
        }

        return paramDescriptors == null ? "()" : paramDescriptors.append(')').toString();
    }

    protected int countDimension(Type type) {
        // to convert to jdk16 pattern matching only when spotbugs #1617 and eclipse #577987 are solved
        return type instanceof ArrayType ? ((ArrayType) type).getDimensions() : 0;
    }

    protected Type getSuperType(AbstractTypeDeclaration node) {
        if (node instanceof TypeDeclaration) { // to convert to jdk16 pattern matching only when spotbugs #1617 and eclipse #577987 are solved
            TypeDeclaration td = (TypeDeclaration) node;
            Type superclassType = td.getSuperclassType();
            if (superclassType != null) {
                return superclassType;
            }
        }
        return null;
    }
}
