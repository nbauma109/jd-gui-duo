/*
 * © 2008-2019 Emmanuel Dupuy
 * © 2022-2026 Nicolas Baumann (@nbauma109)
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
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.core.v1.util.StringConstants;
import org.jd.gui.api.API;
import org.jd.gui.api.model.Container;
import org.jd.gui.api.model.Type;
import org.jd.gui.util.parser.jdt.ASTParserFactory;
import org.jd.gui.util.parser.jdt.core.AbstractJavaListener;
import org.jd.util.LRUCache;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.Icon;

import static org.apache.bcel.Const.ACC_ANNOTATION;
import static org.apache.bcel.Const.ACC_FINAL;
import static org.apache.bcel.Const.ACC_INTERFACE;
import static org.apache.bcel.Const.ACC_PRIVATE;
import static org.apache.bcel.Const.ACC_PROTECTED;
import static org.apache.bcel.Const.ACC_PUBLIC;
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
        Listener listener = getListener(api, entry);

        if (listener == null) {
            return Collections.emptyList();
        }
        return listener.getRootTypes();
    }

    @Override
    public Type make(API api, Container.Entry entry, String fragment) {
        Listener listener = getListener(api, entry);

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

    protected Listener getListener(API api, Container.Entry entry) {
        URI key = entry.getUri();

        if (cache.containsKey(key)) {
            return cache.get(key);
        }
        Listener listener;

        try {
            listener = new Listener(entry);
            ASTParserFactory.getInstance().newASTParser(api, entry).createAST(null).accept(listener);
        } catch (IOException e) {
            assert ExceptionUtil.printStackTrace(e);
            listener = null;
        }

        cache.put(key, listener);
        return listener;
    }

    protected static class JavaType implements Type {
        private final int access;
        private final String name;
        private final String superName;
        private final String outerName;

        private final String displayTypeName;
        private final String displayInnerTypeName;
        private final String displayPackageName;

        private final List<Type> innerTypes = new ArrayList<>();
        private final List<Field> fields = new ArrayList<>();
        private final List<Method> methods = new ArrayList<>();

        private final JavaType outerType;

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
            if ("java/lang/Record".equals(superName)) {
                return RECORD_ICONS[accessToIndex(access)];
            }
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
        private final int access;
        private final String name;
        private final String descriptor;
        /** The descriptor with the type variables named as in the source. */
        private final String written;

        public JavaField(int access, String name, String descriptor, String written) {
            this.written = written;
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
            writeSignature(sb, written, written.length(), 0, false);
            return sb.toString();
        }
    }

    protected static class JavaMethod implements Type.Method {
        private final JavaType type;
        private final int access;
        private final String name;
        private final String descriptor;
        /** The descriptor with the type variables named as in the source. */
        private final String written;

        public JavaMethod(JavaType type, int access, String name, String descriptor, String written) {
            this.written = written;
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

            if (constructorName == null) {
                constructorName = type.getDisplayTypeName();
            }

            StringBuilder sb = new StringBuilder();
            // no synthetic parameter for the outer instance in the descriptors of a source: none of the parameters of a constructor is skipped
            writeMethodSignature(sb, type.getFlags(), access, false, constructorName, name, written);
            return sb.toString();
        }
    }

    protected static class Listener extends AbstractJavaListener {

        private String displayPackageName = "";

        private JavaType mainType;
        private JavaType currentType;
        private final List<Type> rootTypes = new ArrayList<>();
        private final Map<String, Type> types = new HashMap<>();

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

            if (node instanceof RecordDeclaration) {
                superQualifiedTypeName = "java/lang/Record";
                access |= ACC_FINAL; // a record is final
            } else if (superType == null) {
                // like the model of a class file: an interface has no super class to show
                superQualifiedTypeName = (access & (ACC_INTERFACE | ACC_ANNOTATION)) == 0 ? StringConstants.JAVA_LANG_OBJECT : null;
            } else {
                superQualifiedTypeName = resolveInternalTypeName(superType);
            }

            access |= node.getModifiers();

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
        public boolean visit(RecordDeclaration node) {
            boolean visited = super.visit(node);
            // the components are private final fields
            for (Object component : node.recordComponents()) {
                SingleVariableDeclaration declaration = (SingleVariableDeclaration) component;
                currentType.getFields().add(new JavaField(ACC_PRIVATE | ACC_FINAL, declaration.getName().getIdentifier(), createComponentDescriptor(declaration),
                        asWritten(() -> createComponentDescriptor(declaration))));
            }
            return visited;
        }

        @Override
        public void endVisit(RecordDeclaration node) {
            // what the compiler adds when the record does not declare it: an accessor per component and the canonical constructor
            for (Object component : node.recordComponents()) {
                SingleVariableDeclaration declaration = (SingleVariableDeclaration) component;
                String name = declaration.getName().getIdentifier();
                String accessor = "()" + createComponentDescriptor(declaration);
                if (!declaresMethod(name, accessor)) {
                    String written = "()" + asWritten(() -> createComponentDescriptor(declaration));
                    currentType.getMethods().add(new JavaMethod(currentType, ACC_PUBLIC, name, accessor, written));
                }
            }
            String canonical = createRecordParamDescriptors(node) + "V";
            if (!declaresMethod(StringConstants.INSTANCE_CONSTRUCTOR, canonical)) {
                int visibility = currentType.getFlags() & (ACC_PUBLIC | ACC_PROTECTED | ACC_PRIVATE);
                String written = asWritten(() -> createRecordParamDescriptors(node)) + "V";
                currentType.getMethods().add(new JavaMethod(currentType, visibility, StringConstants.INSTANCE_CONSTRUCTOR, canonical, written));
            }
            super.endVisit(node);
        }

        private boolean declaresMethod(String name, String descriptor) {
            return currentType.getMethods().stream().anyMatch(method -> method.getName().equals(name) && method.getDescriptor().equals(descriptor));
        }

        @Override
        public boolean visit(Initializer node) {
            currentType.getMethods().add(new JavaMethod(currentType, ACC_STATIC, "<clinit>", "()V", "()V"));
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
                String written = asWritten(() -> createDescriptor(node.getType(), dimensionOnVariable));
                currentType.getFields().add(new JavaField(access, name, descriptor, written));
            }
            return true;
        }

        @Override
        public boolean visit(MethodDeclaration node) {
            int access = node.getModifiers();
            String name = node.isConstructor() ? StringConstants.INSTANCE_CONSTRUCTOR : node.getName().getIdentifier();
            String descriptor = createParamDescriptors(node) + createDescriptor(node.getReturnType2(), 0);
            String written = asWritten(() -> createParamDescriptors(node) + createDescriptor(node.getReturnType2(), 0));

            currentType.getMethods().add(new JavaMethod(currentType, access, name, descriptor, written));
            return true;
        }
    }
}
