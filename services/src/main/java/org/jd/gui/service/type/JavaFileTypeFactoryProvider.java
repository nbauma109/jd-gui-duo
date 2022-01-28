/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.type;

import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.jd.core.v1.api.loader.LoaderException;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.core.v1.util.StringConstants;
import org.jd.gui.api.API;
import org.jd.gui.api.model.Container;
import org.jd.gui.api.model.Type;
import org.jd.gui.service.type.AbstractTypeFactoryProvider;
import org.jd.gui.util.parser.jdt.ASTParserFactory;
import org.jd.gui.util.parser.jdt.core.AbstractJavaListener;
import org.jd.util.LRUCache;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.Icon;

import static org.apache.bcel.Const.ACC_INTERFACE;
import static org.apache.bcel.Const.ACC_STATIC;

public class JavaFileTypeFactoryProvider extends AbstractTypeFactoryProvider {

    // Create cache
    protected LRUCache<URI, Listener> cache = new LRUCache<>();

    @Override
    public String[] getSelectors() {
        return appendSelectors("*:file:*.java");
    }

    @Override
    public Collection<Type> make(API api, Container.Entry entry) {
        Listener listener = getListener(entry);

        if (listener == null) {
            return Collections.emptyList();
        }
        return listener.getRootTypes();
    }

    @Override
    public Type make(API api, Container.Entry entry, String fragment) {
        Listener listener = getListener(entry);

        if (listener == null) {
            return null;
        }
        if (fragment != null && !fragment.isEmpty()) {
            // Search type name in fragment. URI format : see jd.gui.api.feature.UriOpener
            int index = fragment.indexOf('-');

            if (index != -1) {
                // Keep type name only
                fragment = fragment.substring(0, index);
            }

            return listener.getType(fragment);
        }
        return listener.getMainType();
    }

    protected Listener getListener(Container.Entry entry) {
        URI key = entry.getUri();

        if (cache.containsKey(key)) {
            return cache.get(key);
        }
        Listener listener;

        try {
            listener = new Listener(entry);
            ASTParserFactory.getInstance().newASTParser(entry, listener);
        } catch (LoaderException e) {
            assert ExceptionUtil.printStackTrace(e);
            listener = null;
        }

        cache.put(key, listener);
        return listener;
    }

    protected static class JavaType implements Type {
        private int access;
        private String name;
        private String superName;
        private String outerName;

        private String displayTypeName;
        private String displayInnerTypeName;
        private String displayPackageName;

        private List<Type> innerTypes = new ArrayList<>();
        private List<Field> fields = new ArrayList<>();
        private List<Method> methods = new ArrayList<>();

        private JavaType outerType;

        public JavaType(int access, String name, String superName, String outerName, String displayTypeName,
                String displayInnerTypeName, String displayPackageName, JavaType outerType) {

            this.access = access;
            this.name = name;
            this.superName = superName;
            this.outerName = outerName;
            this.displayTypeName = displayTypeName;
            this.displayInnerTypeName = displayInnerTypeName;
            this.displayPackageName = displayPackageName;
            this.outerType = outerType;
        }

        @Override
        public int getFlags() {
            return access;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getSuperName() {
            return superName;
        }

        @Override
        public String getOuterName() {
            return outerName;
        }

        @Override
        public String getDisplayTypeName() {
            return displayTypeName;
        }

        @Override
        public String getDisplayInnerTypeName() {
            return displayInnerTypeName;
        }

        @Override
        public String getDisplayPackageName() {
            return displayPackageName;
        }

        @Override
        public Icon getIcon() {
            return getTypeIcon(access);
        }

        public JavaType getOuterType() {
            return outerType;
        }

        @Override
        public Collection<Type> getInnerTypes() {
            return innerTypes;
        }

        @Override
        public Collection<Field> getFields() {
            return fields;
        }

        @Override
        public Collection<Method> getMethods() {
            return methods;
        }
    }

    protected static class JavaField implements Type.Field {
        private int access;
        private String name;
        private String descriptor;

        public JavaField(int access, String name, String descriptor) {
            this.access = access;
            this.name = name;
            this.descriptor = descriptor;
        }

        @Override
        public int getFlags() {
            return access;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDescriptor() {
            return descriptor;
        }

        @Override
        public Icon getIcon() {
            return getFieldIcon(access);
        }

        @Override
        public String getDisplayName() {
            StringBuilder sb = new StringBuilder();
            sb.append(name).append(" : ");
            writeSignature(sb, descriptor, descriptor.length(), 0, false);
            return sb.toString();
        }
    }

    protected static class JavaMethod implements Type.Method {
        private JavaType type;
        private int access;
        private String name;
        private String descriptor;

        public JavaMethod(JavaType type, int access, String name, String descriptor) {
            this.type = type;
            this.access = access;
            this.name = name;
            this.descriptor = descriptor;
        }

        @Override
        public int getFlags() {
            return access;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDescriptor() {
            return descriptor;
        }

        @Override
        public Icon getIcon() {
            return getMethodIcon(access);
        }

        @Override
        public String getDisplayName() {
            String constructorName = type.getDisplayInnerTypeName();
            boolean isInnerClass = constructorName != null;

            if (constructorName == null) {
                constructorName = type.getDisplayTypeName();
            }

            StringBuilder sb = new StringBuilder();
            writeMethodSignature(sb, access, access, isInnerClass, constructorName, name, descriptor);
            return sb.toString();
        }
    }

    protected static class Listener extends AbstractJavaListener {

        private String displayPackageName = "";

        private JavaType mainType;
        private JavaType currentType;
        private List<Type> rootTypes = new ArrayList<>();
        private Map<String, Type> types = new HashMap<>();

        public Listener(Container.Entry entry) {
            super(entry);
        }

        public Type getMainType() {
            return mainType;
        }

        public Type getType(String typeName) {
            return types.get(typeName);
        }

        public List<Type> getRootTypes() {
            return rootTypes;
        }

        // --- AST Listener --- //

        @Override
        public boolean visit(PackageDeclaration node) {
            if (super.visit(node)) {
                displayPackageName = node.getName().getFullyQualifiedName();
            }
            return true;
        }

        @Override
        protected boolean enterTypeDeclaration(AbstractTypeDeclaration node, int access) {
            String name = node.getName().getIdentifier();

            org.eclipse.jdt.core.dom.Type superType = getSuperType(node);
            String superQualifiedTypeName;

            if (superType == null) {
                superQualifiedTypeName = (access & ACC_INTERFACE) == 0 ? StringConstants.JAVA_LANG_OBJECT : "";
            } else {
                superQualifiedTypeName = resolveInternalTypeName(superType);
            }

            access += node.getModifiers();

            if (currentType == null) {
                String internalTypeName = packageName.isEmpty() ? name : packageName + "/" + name;
                String outerName = null;
                String displayTypeName = name;
                String displayInnerTypeName = null;

                currentType = new JavaType(access, internalTypeName, superQualifiedTypeName, outerName, displayTypeName,
                        displayInnerTypeName, displayPackageName, null);
                types.put(internalTypeName, currentType);
                rootTypes.add(currentType);
                nameToInternalTypeName.put(name, internalTypeName);

                if (mainType == null) {
                    mainType = currentType;
                } else {
                    // Multi class definitions in the same file
                    String path = entry.getPath();
                    int index = path.lastIndexOf('/') + 1;

                    if (path.startsWith(name + '.', index)) {
                        // Select the correct root type
                        mainType = currentType;
                    }
                }
            } else {
                String internalTypeName = currentType.getName() + '$' + name;
                String outerName = currentType.getName();
                String displayTypeName = currentType.getDisplayTypeName() + '.' + name;
                String displayInnerTypeName = name;
                JavaType subType = new JavaType(access, internalTypeName, superQualifiedTypeName, outerName,
                        displayTypeName, displayInnerTypeName, displayPackageName, currentType);

                currentType.getInnerTypes().add(subType);
                currentType = subType;
                types.put(internalTypeName, currentType);
                nameToInternalTypeName.put(name, internalTypeName);
            }
            return true;
        }

        @Override
        protected void exitTypeDeclaration() {
            currentType = currentType.getOuterType();
        }

        @Override
        public boolean visit(Initializer node) {
            currentType.getMethods().add(new JavaMethod(currentType, ACC_STATIC, "<clinit>", "()V"));
            return true;
        }

        @Override
        public boolean visit(FieldDeclaration node) {
            int access = node.getModifiers();
            @SuppressWarnings("unchecked")
            List<VariableDeclarationFragment> fragments = node.fragments();
            for (VariableDeclarationFragment fragment : fragments) {
                String name = fragment.getName().getIdentifier();
                int dimensionOnVariable = fragment.getExtraDimensions();
                String descriptor = createDescriptor(node.getType(), dimensionOnVariable);
                currentType.getFields().add(new JavaField(access, name, descriptor));
            }
            return true;
        }

        @Override
        public boolean visit(MethodDeclaration node) {
            int access = node.getModifiers();
            String name = node.isConstructor() ? StringConstants.INSTANCE_CONSTRUCTOR : node.getName().getIdentifier();
            @SuppressWarnings("unchecked")
            String paramDescriptors = createParamDescriptors(node.parameters());
            String returnDescriptor = createDescriptor(node.getReturnType2(), 0);
            String descriptor = paramDescriptors + returnDescriptor;

            currentType.getMethods().add(new JavaMethod(currentType, access, name, descriptor));
            return true;
        }
    }
}
