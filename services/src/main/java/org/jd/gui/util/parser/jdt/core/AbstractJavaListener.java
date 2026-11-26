/*
 * © 2008-2019 Emmanuel Dupuy
 * © 2021-2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.util.parser.jdt.core;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jdt.core.dom.TypeParameter;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.ArrayType;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NameQualifiedType;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.jd.core.v1.util.StringConstants;
import org.jd.gui.api.model.Container;

import java.util.Set;
import java.util.function.Supplier;
import java.util.HashSet;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Objects;
import java.util.List;
import java.util.Map;

import static org.apache.bcel.Const.ACC_ANNOTATION;
import static org.apache.bcel.Const.ACC_ENUM;
import static org.apache.bcel.Const.ACC_INTERFACE;

public abstract class AbstractJavaListener extends ASTVisitor {
    protected final Container.Entry entry;
    protected String packageName = "";
    private boolean typeVariablesAsWritten;
    protected final Map<String, String> nameToInternalTypeName = new HashMap<>();
    private final Map<String, String> typeNameCache = new HashMap<>();

    protected AbstractJavaListener(Container.Entry entry) {
        this.entry = entry;
    }

    protected static String nameToString(Name name) {
        if (name instanceof SimpleName simpleName) {
            return simpleName.getIdentifier();
        }
        if (name instanceof QualifiedName qualifiedName) {
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
        if (node.isOnDemand()) {
            return true;
        }

        Name name = node.getName();
        if (name instanceof QualifiedName qualifiedName) {
            String simpleName = qualifiedName.getName().getIdentifier();
            String internalTypeName = resolveInternalTypeName(name);
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
    public boolean visit(RecordDeclaration node) {
        return enterTypeDeclaration(node, 0);
    }

    @Override
    public void endVisit(RecordDeclaration node) {
        exitTypeDeclaration();
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

    /**
     * Converts a JDT type node into a source-style slash-separated name.
     * Nested source types remain slash-separated here and are converted later by
     * {@link #resolveQualifiedTypeName(String)}.
     *
     * @param type the JDT type node, for example {@code SimpleType("String")} or
     *        {@code QualifiedType("Evaluator.Id")}
     * @return a source-style slash-separated type name, for example {@code "String"},
     *         {@code "Evaluator/Id"}, or {@code "org/jsoup/select/Evaluator/Id"}
     */
    protected static String typeToString(Type type) {
        if (type instanceof ArrayType arrayType) {
            return typeToString(arrayType.getElementType());
        }
        if (type instanceof ParameterizedType parameterizedType) {
            return typeToString(parameterizedType.getType());
        }
        if (type instanceof QualifiedType qualifiedType) {
            Type qualifierType = qualifiedType.getQualifier();
            String qualifiedIdentifier = qualifiedType.getName().getIdentifier();
            return String.join("/", typeToString(qualifierType), qualifiedIdentifier);
        }
        if (type instanceof SimpleType simpleType) {
            return nameToString(simpleType.getName());
        }
        if (type instanceof NameQualifiedType nameQualifiedType) {
            String qualifiedIdentifier = nameQualifiedType.getName().getIdentifier();
            return String.join("/", nameToString(nameQualifiedType.getQualifier()), qualifiedIdentifier);
        }
        throw new UnsupportedOperationException();
    }

    /**
     * Resolves a JDT name to an internal JVM type name.
     *
     * @param name the JDT name, for example {@code "Integer"} or {@code "Evaluator.Id"}
     * @return the internal JVM type name, for example {@code "java/lang/Integer"} or
     *         {@code "org/jsoup/select/Evaluator$Id"}
     */
    protected String resolveInternalTypeName(Name name) {
        IBinding binding = name.resolveBinding();
        String internalTypeName = binding instanceof ITypeBinding typeBinding
                ? resolveInternalTypeName(typeBinding)
                : null;
        return internalTypeName != null ? internalTypeName : resolveQualifiedTypeName(nameToString(name));
    }

    /**
     * Resolves a JDT type node to an internal JVM type name.
     *
     * @param type the JDT type node, for example {@code SimpleType("Validate")},
     *        {@code QualifiedType("Evaluator.Id")}, or {@code ParameterizedType("List<Element>")}
     * @return the internal JVM type name, for example {@code "org/jsoup/helper/Validate"},
     *         {@code "org/jsoup/select/Evaluator$Id"}, or {@code "java/util/List"}
     */
    protected String resolveInternalTypeName(Type type) {
        String internalTypeName = resolveInternalTypeName(type.resolveBinding());
        if (internalTypeName != null) {
            return internalTypeName;
        }

        if (type instanceof ArrayType arrayType) {
            return resolveInternalTypeName(arrayType.getElementType());
        }
        if (type instanceof ParameterizedType parameterizedType) {
            return resolveInternalTypeName(parameterizedType.getType());
        }
        if (type instanceof QualifiedType qualifiedType) {
            return resolveQualifiedTypeName(typeToString(qualifiedType));
        }
        if (type instanceof NameQualifiedType nameQualifiedType) {
            return resolveQualifiedTypeName(typeToString(nameQualifiedType));
        }
        if (type instanceof SimpleType simpleType) {
            Name simpleTypeName = simpleType.getName();
            if (simpleTypeName instanceof SimpleName simpleName) {
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
            return resolveQualifiedTypeName(nameToString(simpleTypeName));
        }
        return StringConstants.JAVA_LANG_OBJECT;
    }

    /**
     * Resolves a JDT binding to an internal JVM type name.
     *
     * @param typeBinding the JDT binding to resolve
     * @return the internal JVM type name, for example
     *         {@code "org/jsoup/select/Evaluator$Id"} from binary name
     *         {@code "org.jsoup.select.Evaluator$Id"}, or
     *         {@code "java/util/Map$Entry"} from binding key {@code "Ljava/util/Map$Entry;"};
     *         {@code null} if the binding cannot be resolved
     */
    protected String resolveInternalTypeName(ITypeBinding typeBinding) {
        if (typeBinding == null) {
            return null;
        }

        ITypeBinding candidate = typeBinding.getErasure();

        while (candidate != null && candidate.isArray()) {
            candidate = candidate.getElementType();
        }

        if (candidate == null) {
            return null;
        }

        String binaryName = candidate.getBinaryName();
        if (binaryName != null) {
            return binaryName.replace('.', '/');
        }

        String key = candidate.getKey();
        if (key != null && key.length() > 2 && key.charAt(0) == 'L' && key.charAt(key.length() - 1) == ';') {
            return key.substring(1, key.length() - 1);
        }

        return null;
    }

    /**
     * Converts a source-style slash-separated type name into an internal JVM type name.
     *
     * @param sourceTypeName the source-style slash-separated type name, for example
     *        {@code "Evaluator/Id"} or {@code "org/jsoup/select/Evaluator/Id"}
     * @return the internal JVM type name, for example
     *         {@code "org/jsoup/select/Evaluator$Id"} or {@code "java/util/Map$Entry"}
     */
    String resolveQualifiedTypeName(String sourceTypeName) {
        String cachedTypeName = typeNameCache.get(sourceTypeName);
        if (cachedTypeName != null) {
            return cachedTypeName;
        }

        String[] segments = sourceTypeName.split("/");
        String knownTypeName = nameToInternalTypeName.get(segments[0]);

        if (knownTypeName != null) {
            String resolvedTypeName = appendNestedTypeSuffix(knownTypeName, segments, 1);
            typeNameCache.put(sourceTypeName, resolvedTypeName);
            return resolvedTypeName;
        }

        String resolvedTypeName = null;
        if (!packageName.isEmpty()) {
            resolvedTypeName = resolveQualifiedTypeName(segments, packageName);
        }
        if (resolvedTypeName == null) {
            resolvedTypeName = resolveQualifiedTypeName(segments, null);
        }
        if (resolvedTypeName == null) {
            resolvedTypeName = sourceTypeName;
        }

        typeNameCache.put(sourceTypeName, resolvedTypeName);
        return resolvedTypeName;
    }

    /**
     * Resolves a source-style slash-separated type path against an optional package prefix.
     *
     * @param segments the source-style type path split on {@code '/'}, for example
     *        {@code ["Evaluator", "Id"]} or
     *        {@code ["org", "jsoup", "select", "Evaluator", "Id"]}
     * @param packagePrefix the current package in internal form, for example
     *        {@code "org/jsoup/select"}, or {@code null} to resolve from the root
     * @return the internal JVM type name if a matching top-level type is found, for example
     *         {@code "org/jsoup/select/Evaluator$Id"}; {@code null} otherwise
     */
    private String resolveQualifiedTypeName(String[] segments, String packagePrefix) {
        for (int typeSegmentIndex = segments.length - 1; typeSegmentIndex >= 0; typeSegmentIndex--) {
            String topLevelTypeName = StringUtils.join(segments, '/', 0, typeSegmentIndex + 1);
            if (packagePrefix != null) {
                topLevelTypeName = packagePrefix + '/' + topLevelTypeName;
            }

            if (containsTopLevelType(topLevelTypeName)) {
                return appendNestedTypeSuffix(topLevelTypeName, segments, typeSegmentIndex + 1);
            }
        }

        return null;
    }

    private static String appendNestedTypeSuffix(String topLevelTypeName, String[] segments, int startIndex) {
        if (startIndex >= segments.length) {
            return topLevelTypeName;
        }

        StringBuilder sb = new StringBuilder(topLevelTypeName);
        for (int i = startIndex; i < segments.length; i++) {
            sb.append('$').append(segments[i]);
        }
        return sb.toString();
    }

    private boolean containsTopLevelType(String internalTypeName) {
        return containsEntryPath(internalTypeName + ".java")
            || containsEntryPath(internalTypeName + StringConstants.CLASS_FILE_SUFFIX);
    }

    private boolean containsEntryPath(String path) {
        Container container = entry.getContainer();
        if (container == null) {
            return false;
        }

        Container.Entry root = container.getRoot();
        return root != null && containsEntryPath(root, path);
    }

    private boolean containsEntryPath(Container.Entry parentEntry, String path) {
        for (Container.Entry child : parentEntry.getChildren().values()) {
            String childPath = child.getPath();

            if (path.equals(childPath)) {
                return true;
            }
            if (child.isDirectory() && path.startsWith(childPath + '/')
                    && containsEntryPath(child, path)) {
                return true;
            }
        }

        return false;
    }

    public PrimitiveType getPrimitiveTypeContext(Type type) {
        if (type instanceof ArrayType arrayType) {
            return getPrimitiveTypeContext(arrayType.getElementType());
        }
        if (type instanceof PrimitiveType pt) {
            return pt;
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
            Type elementType = type instanceof ArrayType arrayType ? arrayType.getElementType() : type;
            String erasure = typeVariablesAsWritten ? null : erasureOfTypeVariable(elementType);
            name = "L" + (erasure != null ? erasure : resolveInternalTypeName(type)) + ";";
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

        return switch (dimension) {
        case 0 -> name;
        case 1 -> "[" + name;
        case 2 -> "[[" + name;
        default -> new String(new char[dimension]).replace('\0', '[') + name;
        };
    }

    /** The descriptor that is built with the type variables named as in the source, to show a member as it is written. */
    protected String asWritten(Supplier<String> descriptor) {
        typeVariablesAsWritten = true;
        try {
            return descriptor.get();
        } finally {
            typeVariablesAsWritten = false;
        }
    }

    /**
     * In a descriptor, as in a class file and in the binding of a call, a type variable stands for its erasure: its first bound, or
     * Object when it has none. Null when the type is not a type variable. This is read from the source: a pass may have no bindings.
     */
    private String erasureOfTypeVariable(Type type) {
        TypeParameter parameter = typeParameterNamedBy(type);

        if (parameter == null) {
            return null;
        }

        Set<TypeParameter> seen = new HashSet<>();

        while (seen.add(parameter) && !parameter.typeBounds().isEmpty()) {
            Type bound = (Type) parameter.typeBounds().get(0);
            TypeParameter boundParameter = typeParameterNamedBy(bound);

            if (boundParameter == null) {
                return resolveInternalTypeName(bound);
            }
            parameter = boundParameter;
        }

        return "java/lang/Object";
    }

    /** The type parameter of an enclosing method or type that a type names, unless a member type of that name hides it. */
    private static TypeParameter typeParameterNamedBy(Type type) {
        if (!(type instanceof SimpleType simpleType) || !(simpleType.getName() instanceof SimpleName simpleName)) {
            return null;
        }

        String identifier = simpleName.getIdentifier();
        // the types between the use and the parameter, which may have a member type of that name
        List<AbstractTypeDeclaration> within = new ArrayList<>();

        for (ASTNode node = type.getParent(); node != null; node = node.getParent()) {
            if (node instanceof AbstractTypeDeclaration declaration) {
                within.add(declaration);
            }
            for (Object parameter : typeParametersOf(node)) {
                if (((TypeParameter) parameter).getName().getIdentifier().equals(identifier)) {
                    Set<AbstractTypeDeclaration> seen = new HashSet<>();
                    boolean hidden = within.stream().anyMatch(declaration -> hasMemberType(declaration, identifier, false, seen));
                    return hidden ? null : (TypeParameter) parameter;
                }
            }
        }

        return null;
    }

    /**
     * Whether a type declares a member type of that name, or inherits one from a super type that is declared in the same source: a
     * private member type is not inherited.
     */
    private static boolean hasMemberType(AbstractTypeDeclaration declaration, String identifier, boolean inherited,
            Set<AbstractTypeDeclaration> seen) {
        if (!seen.add(declaration)) {
            return false;
        }
        for (Object member : declaration.bodyDeclarations()) {
            if (member instanceof AbstractTypeDeclaration type && type.getName().getIdentifier().equals(identifier)
                    && !(inherited && Modifier.isPrivate(type.getModifiers()))) {
                return true;
            }
        }
        return superTypesOf(declaration).stream()
                .map(superType -> declarationNamed(declaration.getRoot(), superType))
                .anyMatch(superType -> superType != null && hasMemberType(superType, identifier, true, seen));
    }

    /** The simple names of the super class and of the interfaces of a type. */
    private static List<String> superTypesOf(AbstractTypeDeclaration declaration) {
        List<Object> superTypes = new ArrayList<>();

        if (declaration instanceof TypeDeclaration type) {
            superTypes.add(type.getSuperclassType());
            superTypes.addAll(type.superInterfaceTypes());
        } else if (declaration instanceof EnumDeclaration enumDeclaration) {
            superTypes.addAll(enumDeclaration.superInterfaceTypes());
        } else if (declaration instanceof RecordDeclaration recordDeclaration) {
            superTypes.addAll(recordDeclaration.superInterfaceTypes());
        }

        return superTypes.stream().filter(Objects::nonNull).map(superType -> {
            String name = typeToString((Type) superType);
            return name.substring(name.lastIndexOf('/') + 1);
        }).toList();
    }

    /** The type that a source declares with that simple name; null when it declares none. */
    private static AbstractTypeDeclaration declarationNamed(ASTNode source, String name) {
        AbstractTypeDeclaration[] found = new AbstractTypeDeclaration[1];

        source.accept(new ASTVisitor() {
            @Override
            public void preVisit(ASTNode node) {
                if (found[0] == null && node instanceof AbstractTypeDeclaration declaration && declaration.getName().getIdentifier().equals(name)) {
                    found[0] = declaration;
                }
            }
        });

        return found[0];
    }

    private static List<?> typeParametersOf(ASTNode node) {
        if (node instanceof MethodDeclaration method) {
            return method.typeParameters();
        }
        if (node instanceof TypeDeclaration type) {
            return type.typeParameters();
        }
        if (node instanceof RecordDeclaration recordDeclaration) {
            return recordDeclaration.typeParameters();
        }
        return List.of();
    }

    protected String createParamDescriptors(List<SingleVariableDeclaration> formalParameterList) {
        StringBuilder paramDescriptors = null;

        if (formalParameterList != null) {
            paramDescriptors = new StringBuilder("(");

            for (SingleVariableDeclaration formalParameter : formalParameterList) {
                // the "..." of a variable arity parameter is one more array dimension in the descriptor (String... is [Ljava/lang/String;)
                int dimensionOnParameter = formalParameter.getExtraDimensions() + (formalParameter.isVarargs() ? 1 : 0);
                paramDescriptors.append(createDescriptor(formalParameter.getType(), dimensionOnParameter));
            }
        }

        return paramDescriptors == null ? "()" : paramDescriptors.append(')').toString();
    }

    /** The parameters of a method; a compact constructor (R { ... }) has those of the components of its record. */
    protected String createParamDescriptors(MethodDeclaration node) {
        if (node.isCompactConstructor() && node.getParent() instanceof RecordDeclaration recordDeclaration) {
            return createRecordParamDescriptors(recordDeclaration);
        }
        @SuppressWarnings("unchecked")
        List<SingleVariableDeclaration> parameters = node.parameters();
        return createParamDescriptors(parameters);
    }

    /** The parameters of the canonical constructor of a record: its components. */
    protected String createRecordParamDescriptors(RecordDeclaration recordDeclaration) {
        StringBuilder descriptors = new StringBuilder("(");
        for (Object component : recordDeclaration.recordComponents()) {
            descriptors.append(createComponentDescriptor((SingleVariableDeclaration) component));
        }
        return descriptors.append(')').toString();
    }

    protected String createComponentDescriptor(SingleVariableDeclaration component) {
        return createDescriptor(component.getType(), component.getExtraDimensions() + (component.isVarargs() ? 1 : 0));
    }

    protected int countDimension(Type type) {
        return type instanceof ArrayType at ? at.getDimensions() : 0;
    }

    protected Type getSuperType(AbstractTypeDeclaration node) {
        if (node instanceof TypeDeclaration td) {
            Type superclassType = td.getSuperclassType();
            if (superclassType != null) {
                return superclassType;
            }
        }
        return null;
    }
}
