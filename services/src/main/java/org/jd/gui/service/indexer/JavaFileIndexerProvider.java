/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */
package org.jd.gui.service.indexer;

import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.api.API;
import org.jd.gui.api.model.Container;
import org.jd.gui.api.model.Indexes;
import org.jd.gui.util.ProgressUtil;
import org.jd.gui.util.parser.jdt.ASTParserFactory;
import org.jd.gui.util.parser.jdt.core.AbstractJavaListener;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/** Unsafe thread implementation of java file indexer. */
public class JavaFileIndexerProvider extends AbstractIndexerProvider {

    @Override
    public String[] getSelectors() {
        return appendSelectors("*:file:*.java");
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void index(API api, Container.Entry entry, Indexes indexes, DoubleSupplier getProgressFunction, DoubleConsumer setProgressFunction, BooleanSupplier isCancelledFunction) {
        try {
            Listener listener = new Listener(entry);
            ASTParserFactory.getInstance().newASTParser(entry).createAST(null).accept(listener);

            // Append sets to indexes
            addToIndexes(indexes, "typeDeclarations", listener.getTypeDeclarationSet(), entry);
            addToIndexes(indexes, "constructorDeclarations", listener.getConstructorDeclarationSet(), entry);
            addToIndexes(indexes, "methodDeclarations", listener.getMethodDeclarationSet(), entry);
            addToIndexes(indexes, "fieldDeclarations", listener.getFieldDeclarationSet(), entry);
            addToIndexes(indexes, "typeReferences", listener.getTypeReferenceSet(), entry);
            addToIndexes(indexes, "constructorReferences", listener.getConstructorReferenceSet(), entry);
            addToIndexes(indexes, "methodReferences", listener.getMethodReferenceSet(), entry);
            addToIndexes(indexes, "fieldReferences", listener.getFieldReferenceSet(), entry);
            addToIndexes(indexes, "strings", listener.getStringSet(), entry);

            // Populate map [super type name : [sub type name]]
            Map<String, Collection> index = indexes.getIndex("subTypeNames");

            String typeName;
            for (Map.Entry<String, Set<String>> e : listener.getSuperTypeNamesMap().entrySet()) {
                typeName = e.getKey();

                for (String superTypeName : e.getValue()) {
                    index.get(superTypeName).add(typeName);
                }
            }
            ProgressUtil.updateProgress(entry, getProgressFunction, setProgressFunction);
        } catch (IOException e) {
            assert ExceptionUtil.printStackTrace(e);
        }
    }

    protected static class Listener extends AbstractJavaListener {
        private Set<String> typeDeclarationSet = new HashSet<>();
        private Set<String> constructorDeclarationSet = new HashSet<>();
        private Set<String> methodDeclarationSet = new HashSet<>();
        private Set<String> fieldDeclarationSet = new HashSet<>();
        private Set<String> typeReferenceSet = new HashSet<>();
        private Set<String> constructorReferenceSet = new HashSet<>();
        private Set<String> methodReferenceSet = new HashSet<>();
        private Set<String> fieldReferenceSet = new HashSet<>();
        private Set<String> stringSet = new HashSet<>();
        private Map<String, Set<String>> superTypeNamesMap = new HashMap<>();

        private StringBuilder sbTypeDeclaration = new StringBuilder();

        public Listener(Container.Entry entry) {
            super(entry);
        }

        public Set<String> getTypeDeclarationSet() {
            return typeDeclarationSet;
        }

        public Set<String> getConstructorDeclarationSet() {
            return constructorDeclarationSet;
        }

        public Set<String> getMethodDeclarationSet() {
            return methodDeclarationSet;
        }

        public Set<String> getFieldDeclarationSet() {
            return fieldDeclarationSet;
        }

        public Set<String> getTypeReferenceSet() {
            return typeReferenceSet;
        }

        public Set<String> getConstructorReferenceSet() {
            return constructorReferenceSet;
        }

        public Set<String> getMethodReferenceSet() {
            return methodReferenceSet;
        }

        public Set<String> getFieldReferenceSet() {
            return fieldReferenceSet;
        }

        public Set<String> getStringSet() {
            return stringSet;
        }

        public Map<String, Set<String>> getSuperTypeNamesMap() {
            return superTypeNamesMap;
        }

        // --- AST Listener --- //

        @Override
        public boolean visit(PackageDeclaration node) {
            if (super.visit(node) && !packageName.isEmpty()) {
                sbTypeDeclaration.append(packageName).append('/');
            }
            return true;
        }

        @Override
        protected boolean enterTypeDeclaration(AbstractTypeDeclaration node, int flag) {
            // Add type declaration
            String typeName = node.getName().getIdentifier();
            int length = sbTypeDeclaration.length();

            if (length == 0 || sbTypeDeclaration.charAt(length - 1) == '/') {
                sbTypeDeclaration.append(typeName);
            } else {
                sbTypeDeclaration.append('$').append(typeName);
            }

            String internalTypeName = sbTypeDeclaration.toString();
            typeDeclarationSet.add(internalTypeName);
            nameToInternalTypeName.put(typeName, internalTypeName);

            Set<String> superInternalTypeNameSet = new HashSet<>();

            // Add super type reference
            Type superTypeIdentifier = getSuperType(node);
            if (superTypeIdentifier != null) {
                String superQualifiedTypeName = resolveInternalTypeName(superTypeIdentifier);
                if (superQualifiedTypeName.charAt(0) != '*') {
                    superInternalTypeNameSet.add(superQualifiedTypeName);
                }
            }
            if (node instanceof TypeDeclaration typeDeclaration) {
                @SuppressWarnings("unchecked")
                List<Type> superInterfaces = typeDeclaration.superInterfaceTypes();
                String superQualifiedInterfaceName;
                for (Type superInterface : superInterfaces) {
                    superQualifiedInterfaceName = resolveInternalTypeName(superInterface);
                    if (superQualifiedInterfaceName.charAt(0) != '*') {
                        superInternalTypeNameSet.add(superQualifiedInterfaceName);
                    }
                }
            }
            if (!superInternalTypeNameSet.isEmpty()) {
                superTypeNamesMap.put(internalTypeName, superInternalTypeNameSet);
            }
            return true;
        }

        @Override
        public void exitTypeDeclaration() {
            int index = sbTypeDeclaration.lastIndexOf("$");

            if (index == -1) {
                index = sbTypeDeclaration.lastIndexOf("/") + 1;
            }

            if (index == -1) {
                sbTypeDeclaration.setLength(0);
            } else {
                sbTypeDeclaration.setLength(index);
            }
        }

        @Override
        public boolean visit(SimpleType node) {
            // Add type reference
            String internalTypeName = resolveInternalTypeName(node);

            if (internalTypeName.charAt(0) != '*') {
                typeReferenceSet.add(internalTypeName);
            }
            return true;
        }

        @Override
        public boolean visit(FieldDeclaration node) {
            @SuppressWarnings("unchecked")
            List<VariableDeclarationFragment> fragments = node.fragments();
            for (VariableDeclarationFragment fragment : fragments) {
                fieldDeclarationSet.add(fragment.getName().getIdentifier());
            }
            return true;
        }

        @Override
        public boolean visit(MethodDeclaration node) {
            if (node.isConstructor()) {
                constructorDeclarationSet.add(node.getName().getIdentifier());
            } else {
                methodDeclarationSet.add(node.getName().getIdentifier());
            }
            return true;
        }

        @Override
        public boolean visit(ClassInstanceCreation node) {
            Type type = node.getType();
            String typeString = typeToString(type);
            constructorReferenceSet.add(typeString);
            return true;
        }

        @Override
        public boolean visit(FieldAccess node) {
            fieldReferenceSet.add(node.getName().getIdentifier());
            return true;
        }

        @Override
        public boolean visit(MethodInvocation node) {
            methodReferenceSet.add(node.getName().getIdentifier());
            return true;
        }

        @Override
        public boolean visit(StringLiteral node) {
            stringSet.add(node.getLiteralValue());
            return true;
        }
    }
}
