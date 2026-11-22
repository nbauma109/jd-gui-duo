package org.jd.gui.view.component;

import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.ArrayCreation;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.CreationReference;
import org.eclipse.jdt.core.dom.SuperMethodReference;
import org.eclipse.jdt.core.dom.TypeMethodReference;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionMethodReference;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.NameQualifiedType;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.Type;
import org.jd.core.v1.util.StringConstants;
import org.jd.gui.util.matcher.DescriptorMatcher;
import org.jd.gui.api.model.Container;
import org.jd.gui.util.parser.jdt.core.AbstractJavaListener;
import org.jd.gui.util.parser.jdt.core.TypeDeclarationData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.SortedMap;
import java.util.TreeMap;

import static org.jd.gui.util.Key.key;

import jd.core.links.DeclarationData;
import jd.core.links.HyperlinkData;
import jd.core.links.HyperlinkReferenceData;
import jd.core.links.ReferenceData;
import jd.core.links.StringData;

public class ReferenceListener extends AbstractJavaListener {
    private final StringBuilder sbTypeDeclaration = new StringBuilder();
    private final Map<String, ReferenceData> referencesCache = new HashMap<>();
    private String currentInternalTypeName;

    private final DeclarationListener declarationListener;

    private final List<ReferenceData> references = new ArrayList<>();
    private final List<StringData> strings = new ArrayList<>();
    private SortedMap<Integer, HyperlinkData> hyperlinks = new TreeMap<>();

    public ReferenceListener(Container.Entry entry) {
        super(entry);
        declarationListener = new DeclarationListener(entry);
    }

    public void init() {
        this.nameToInternalTypeName.putAll(declarationListener.getNameToInternalTypeName());
    }

    /** --- Add declarations --- */
    @Override
    public boolean visit(PackageDeclaration node) {
        if (super.visit(node) && !packageName.isEmpty()) {
            sbTypeDeclaration.append(packageName).append('/');
        }
        return true;
    }

    @Override
    public boolean visit(ImportDeclaration node) {
        if (node.isOnDemand()) {
            return true;
        }

        int position = node.getName().getStartPosition();
        String internalTypeName = resolveInternalTypeName(node.getName().resolveBinding() instanceof ITypeBinding typeBinding
                ? typeBinding
                : null);

        if (internalTypeName == null && !node.isStatic()) {
            internalTypeName = resolveInternalTypeName(node.getName());
        }

        if (internalTypeName != null) {
            ReferenceData refData = newReferenceData(internalTypeName, null, null, null);
            addHyperlink(new HyperlinkReferenceData(position, internalTypeName.length(), refData));
        }
        return true;
    }

    @Override
    public boolean enterTypeDeclaration(AbstractTypeDeclaration node, int flag) {
        // Type declaration
        String typeName = node.getName().getIdentifier();
        int length = sbTypeDeclaration.length();

        if (length == 0 || sbTypeDeclaration.charAt(length - 1) == '/') {
            sbTypeDeclaration.append(typeName);
        } else {
            sbTypeDeclaration.append('$').append(typeName);
        }

        currentInternalTypeName = sbTypeDeclaration.toString();
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

        currentInternalTypeName = sbTypeDeclaration.toString();
    }

    /** --- Add references --- */
    @Override
    public boolean visit(SimpleType node) {
        addTypeHyperlink(node.getStartPosition(), node.getName().getLength(), resolveInternalTypeName(node));
        return true;
    }

    @Override
    public boolean visit(QualifiedType node) {
        addTypeHyperlink(node.getStartPosition(), node.getLength(), resolveInternalTypeName(node));
        return false;
    }

    @Override
    public boolean visit(NameQualifiedType node) {
        addTypeHyperlink(node.getStartPosition(), node.getLength(), resolveInternalTypeName(node));
        return false;
    }

    private void addTypeHyperlink(int position, int length, String internalTypeName) {
        ReferenceData refData = newReferenceData(internalTypeName, null, null);
        addHyperlink(new HyperlinkReferenceData(position, length, refData));
    }

    @Override
    public boolean visit(QualifiedName node) {
        ITypeBinding resolvedTypeBinding = node.resolveTypeBinding();
        // only a qualified name that denotes a type (java.util.List, Map.Entry) is a link to that type: for a field (Consts.NAME,
        // values.length) the type binding is the one of its value, which is not what the name refers to
        if (resolvedTypeBinding != null && node.resolveBinding() instanceof ITypeBinding) {
            String internalTypeName = resolveInternalTypeName(resolvedTypeBinding);
            if (internalTypeName != null) {
                Name qualifier = node.getQualifier();
                if (qualifier instanceof SimpleName subjectName) {
                    String qualifierInternalTypeName = nameToInternalTypeName.get(subjectName.getIdentifier());
                    if (qualifierInternalTypeName != null) {
                        ReferenceData qualifierRefData = newReferenceData(qualifierInternalTypeName, null, null);
                        addHyperlink(new HyperlinkReferenceData(subjectName.getStartPosition(), subjectName.getLength(), qualifierRefData));
                    }
                }
                addTypeHyperlink(node.getName().getStartPosition(), node.getName().getLength(), internalTypeName);
                return false;
            }
        }

        SimpleName fieldNameNode = node.getName();
        String fieldName = fieldNameNode.getIdentifier();
        Name qualifier = node.getQualifier();
        if (qualifier instanceof SimpleName qualifierSimpleName) {
            String qualifierDescriptor = null;
            ITypeBinding qualifierType = null;
            IBinding binding = qualifier.resolveBinding();
            if (binding instanceof ITypeBinding typeBinding) {
                qualifierDescriptor = binding.getKey();
                qualifierType = typeBinding;
            }
            IVariableBinding variableBinding = null;
            if (binding instanceof IVariableBinding) {
                variableBinding = (IVariableBinding) binding;
                ITypeBinding typeBinding = qualifier.resolveTypeBinding();
                if (typeBinding != null) {
                    qualifierDescriptor = typeBinding.getKey();
                    qualifierType = typeBinding;
                }
            }
            if (qualifierDescriptor != null) {
                if (qualifierDescriptor.charAt(0) == 'L') {
                    // the erasure: the key of a generic type carries its type parameters (demo/Sample<TT;>)
                    String erasedName = qualifierType == null ? null : qualifierType.getErasure().getBinaryName();
                    String qualifierTypeName = erasedName != null ? erasedName.replace('.', '/') : qualifierDescriptor.substring(1, qualifierDescriptor.length() - 1);
                    ReferenceData refData = newReferenceData(qualifierTypeName, fieldName, "?");
                    if (binding instanceof ITypeBinding || (variableBinding != null && variableBinding.isField())) {
                        ReferenceData qualifierRefData = newReferenceData(qualifierTypeName, null, null);
                        addHyperlink(new HyperlinkReferenceData(qualifier.getStartPosition(), qualifier.getLength(), qualifierRefData));
                    }
                    addHyperlink(new HyperlinkReferenceData(fieldNameNode.getStartPosition(), fieldNameNode.getLength(), refData));
                }
                if (qualifierDescriptor.charAt(0) == '[' && variableBinding != null) {
                    ITypeBinding declaringClass = variableBinding.getDeclaringClass();
                    if (declaringClass != null) {
                        String declaringBinaryName = declaringClass.getErasure().getBinaryName();
                        if (declaringBinaryName != null) {
                            String declaringInternalTypeName = declaringBinaryName.replace('.', '/');
                            String qualifierIdentifier = qualifierSimpleName.getIdentifier();
                            ReferenceData refData = newReferenceData(declaringInternalTypeName, qualifierIdentifier, "?");
                            addHyperlink(new HyperlinkReferenceData(qualifier.getStartPosition(), qualifier.getLength(), refData));
                        }
                    }
                }
            }
        }
        return true;
    }

    @Override
    public boolean visit(ClassInstanceCreation node) {
        Type type = node.getType();
        String internalTypeName = resolveInternalTypeName(type);
        int position = node.getStartPosition();
        // Constructor call -> Add a link to the constructor declaration
        @SuppressWarnings("unchecked")
        List<Expression> expressionList = node.arguments();
        String descriptor = getParametersDescriptor(node.resolveConstructorBinding(), expressionList).append('V').toString();


        ReferenceData refData = newReferenceData(internalTypeName, StringConstants.INSTANCE_CONSTRUCTOR, descriptor);
        addHyperlink(new HyperlinkReferenceData(position, node.getLength(), refData));
        return true;
    }

    /** The parameters of the declaration of the method, erased as the declarations are keyed (List<String> is Ljava/util/List;). */
    private static StringBuilder getErasedParametersDescriptor(IMethodBinding binding) {
        StringBuilder sb = new StringBuilder("(");
        for (ITypeBinding parameterType : binding.getMethodDeclaration().getParameterTypes()) {
            String erased = erasedDescriptor(parameterType);
            sb.append(erased != null ? erased : parameterType.getErasure().getKey());
        }
        return sb.append(')');
    }

    /** The descriptor of the erasure of a type, from the binary name (the erasure of List<String> is the generic type, whose key carries its type parameters), or null. */
    private static String erasedDescriptor(ITypeBinding type) {
        ITypeBinding erasure = type.getErasure();
        if (erasure.isPrimitive()) {
            return erasure.getKey();
        }
        if (erasure.isArray()) {
            String element = erasedDescriptor(erasure.getElementType());
            return element == null ? null : "[".repeat(erasure.getDimensions()) + element;
        }
        boolean describable = (erasure.isClass() || erasure.isInterface() || erasure.isEnum() || erasure.isRecord() || erasure.isAnnotation())
                && !erasure.isAnonymous() && erasure.getBinaryName() != null;
        return describable ? "L" + erasure.getBinaryName().replace('.', '/') + ";" : null;
    }

    private static StringBuilder getParametersDescriptor(ITypeBinding[] argTypes) {
        StringBuilder sb = new StringBuilder("(");
        for (ITypeBinding argType : argTypes) {
            sb.append(argType.getKey());
        }
        sb.append(')');
        return sb;
    }

    /**
     * The parameters of a call: those of its binding or, when the call has none (a type that cannot be resolved, no applicable method...),
     * what its arguments tell: a hint (~) of the type of each one that is known, ? for the others.
     */
    private static StringBuilder getParametersDescriptor(IMethodBinding binding, List<Expression> arguments) {
        if (isResolution(binding, arguments)) {
            return getParametersDescriptor(binding.getParameterTypes());
        }
        StringBuilder sb = new StringBuilder("(");
        if (arguments != null) {
            for (Expression argument : arguments) {
                sb.append(argumentDescriptor(argument));
            }
        }
        return sb.append(')');
    }

    /**
     * Whether the binding is what the call resolves to. With the recovery of the bindings, a call that has no applicable method
     * still gets the compiler's closest match (the first one of that name): its parameters are not the ones of the call.
     */
    private static boolean isResolution(IMethodBinding binding, List<Expression> arguments) {
        if (binding == null || binding.isRecovered()) {
            return false;
        }
        if (arguments == null) {
            return true;
        }
        ITypeBinding[] parameters = binding.getParameterTypes();
        boolean varargs = binding.isVarargs();
        if (varargs ? arguments.size() < parameters.length - 1 : arguments.size() != parameters.length) {
            return false;
        }
        for (int i = 0; i < arguments.size(); i++) {
            ITypeBinding parameter = i < parameters.length ? parameters[i] : parameters[parameters.length - 1];
            if (varargs && i >= parameters.length - 1 && parameter.isArray()
                    && !(arguments.size() == parameters.length && i == parameters.length - 1 && argumentAssignableTo(arguments.get(i), parameter))) {
                parameter = parameter.getComponentType(); // the trailing arguments are the elements of the array
            }
            if (!argumentAssignableTo(arguments.get(i), parameter)) {
                return false;
            }
        }
        return true;
    }

    /** An argument of unknown type (or null) is given the benefit of the doubt. */
    private static boolean argumentAssignableTo(Expression argument, ITypeBinding parameter) {
        ITypeBinding type = argument.resolveTypeBinding();
        return type == null || type.isNullType() || type.isRecovered() || parameter.isRecovered() || type.isAssignmentCompatible(parameter);
    }

    /** A hint of the type of an argument when its erasure can be written as a descriptor (not a capture, an intersection or an anonymous class). */
    private static String argumentDescriptor(Expression argument) {
        ITypeBinding type = argument.resolveTypeBinding();
        if (type == null || type.isNullType() || type.isRecovered()) {
            return "?";
        }
        ITypeBinding erasure = type.getErasure();
        ITypeBinding element = erasure.isArray() ? erasure.getElementType() : erasure;
        boolean describable = element.isPrimitive() || ((element.isClass() || element.isInterface() || element.isEnum() || element.isRecord()
                || element.isAnnotation()) && element.getBinaryName() != null && !element.isAnonymous());
        return describable ? DescriptorMatcher.HINT + erasure.getKey() : "?";
    }

    @Override
    public boolean visit(ArrayCreation node) {
        if (!node.getType().getElementType().isPrimitiveType()) {
            String internalTypeName = resolveInternalTypeName(node.getType());
            int position = node.getStartPosition();
            // New type array -> Add a link to the type declaration
            ReferenceData refData = newReferenceData(internalTypeName, null, null);
            addHyperlink(new HyperlinkReferenceData(position, node.getType().getLength(), refData));
        }
        return true;
    }

    private void createLinkToVariable(Expression exp) {
        if (exp instanceof SimpleName simpleName) {
            IBinding binding = simpleName.resolveBinding();
            if (binding instanceof IVariableBinding variableBinding) {
                createLinkToVariable(simpleName, variableBinding);
            }
        }
    }

    private void createLinkToVariable(SimpleName simpleName, IVariableBinding variableBinding) {
        if (variableBinding != null && variableBinding.isField()) {
            ITypeBinding declaringClass = variableBinding.getDeclaringClass();
            if (declaringClass != null) {
                String binaryName = declaringClass.getErasure().getBinaryName();
                if (binaryName != null) {
                    String varTypeName = binaryName.replace('.', '/');
                    int position = simpleName.getStartPosition();
                    String varName = simpleName.getIdentifier();
                    ReferenceData refData = newReferenceData(varTypeName, varName, "?");
                    addHyperlink(new HyperlinkReferenceData(position, varName.length(), refData));
                }
            }
        }
    }

    /** A field used by its simple name (LIMIT, values): a link to its declaration, unless the name is part of a qualified name or a field access, which link it themselves. */
    @Override
    public boolean visit(SimpleName node) {
        if (!node.isDeclaration() && node.resolveBinding() instanceof IVariableBinding variableBinding
                && node.getLocationInParent() != QualifiedName.NAME_PROPERTY
                && node.getLocationInParent() != FieldAccess.NAME_PROPERTY
                && node.getLocationInParent() != org.eclipse.jdt.core.dom.SuperFieldAccess.NAME_PROPERTY) {
            createLinkToVariable(node, variableBinding);
        }
        return true;
    }

    @Override
    public boolean visit(ArrayAccess node) {
        createLinkToVariable(node.getArray());
        return true;
    }

    @Override
    public boolean visit(FieldAccess node) {
        IVariableBinding fieldBinding = node.resolveFieldBinding();
        if (fieldBinding != null) {
            createLinkToVariable(node.getName(), fieldBinding);
        }
        return true;
    }

    @Override
    public boolean visit(Assignment node) {
        createLinkToVariable(node.getLeftHandSide());
        createLinkToVariable(node.getRightHandSide());
        return true;
    }

    @Override
    public boolean visit(ConstructorInvocation node) {
        @SuppressWarnings("unchecked")
        List<Expression> args = node.arguments();
        String constructorDescriptor = getParametersDescriptor(node.resolveConstructorBinding(), args).append('V').toString();
        ReferenceData refData = newReferenceData(currentInternalTypeName, StringConstants.INSTANCE_CONSTRUCTOR, constructorDescriptor);
        int position = node.getStartPosition();
        addHyperlink(new HyperlinkReferenceData(position, 4 /* this */, refData));
        return true;
    }

    @Override
    public boolean visit(SuperConstructorInvocation node) {
        @SuppressWarnings("unchecked")
        List<Expression> args = node.arguments();
        String constructorDescriptor = getParametersDescriptor(node.resolveConstructorBinding(), args).append('V').toString();
        DeclarationData data = getDeclarations().get(currentInternalTypeName);

        if (data instanceof TypeDeclarationData tdd) {
            String methodTypeName = tdd.getSuperTypeName();
            ReferenceData refData = newReferenceData(methodTypeName, StringConstants.INSTANCE_CONSTRUCTOR, constructorDescriptor);
            int position = node.getStartPosition();
            addHyperlink(new HyperlinkReferenceData(position, 5 /* super */, refData));
        }
        return true;
    }

    @Override
    public boolean visit(MethodInvocation node) {
        IMethodBinding methodBinding = node.resolveMethodBinding();
        if (isResolution(methodBinding, node.arguments())) {
            visitMethodBinding(node.getName(), methodBinding);
        } else {
            Expression receiver = node.getExpression();
            visitCallWithoutBinding(node.getName(), receiver == null ? currentInternalTypeName : internalTypeNameOf(receiver), node.arguments());
        }
        Expression subject = node.getExpression();
        if (subject instanceof SimpleName subjectName) {
            String internalTypeName = nameToInternalTypeName.get(subjectName.getIdentifier());
            if (internalTypeName != null) {
                ReferenceData refData = newReferenceData(internalTypeName, null, null);
                addHyperlink(new HyperlinkReferenceData(subjectName.getStartPosition(), subjectName.getLength(), refData));
            }
        }
        return true;
    }

    @Override
    public boolean visit(SuperMethodInvocation node) {
        IMethodBinding methodBinding = node.resolveMethodBinding();
        if (isResolution(methodBinding, node.arguments())) {
            visitMethodBinding(node.getName(), methodBinding);
        } else if (getDeclarations().get(currentInternalTypeName) instanceof TypeDeclarationData tdd) {
            visitCallWithoutBinding(node.getName(), tdd.getSuperTypeName(), node.arguments());
        }
        return true;
    }

    private static String internalTypeNameOf(Expression expression) {
        return internalTypeNameOf(expression.resolveTypeBinding());
    }

    private static String internalTypeNameOf(ITypeBinding type) {
        String binaryName = type == null ? null : type.getErasure().getBinaryName();
        return binaryName == null ? null : binaryName.replace('.', '/');
    }

    /** As for a constructor without binding: the link is made from the type, the name and what the arguments tell; the return type is unknown. */
    private void visitCallWithoutBinding(SimpleName name, String internalTypeName, List<Expression> arguments) {
        if (internalTypeName != null && !internalTypeName.isEmpty()) {
            String descriptor = getParametersDescriptor(null, arguments).append('?').toString();
            ReferenceData refData = newReferenceData(internalTypeName, name.getIdentifier(), descriptor);
            addHyperlink(new HyperlinkReferenceData(name.getStartPosition(), name.getLength(), refData));
        }
    }

    /**
     * The references to a method (x::m, String::valueOf, Type::m, super::m): the binding when there is one; a reference has no arguments, so
     * without a binding the link is made from the type and the name, to any method of that name.
     */
    @Override
    public boolean visit(ExpressionMethodReference node) {
        return visitMethodReference(node.getName(), node.resolveMethodBinding(), internalTypeNameOf(node.getExpression()));
    }

    @Override
    public boolean visit(TypeMethodReference node) {
        ITypeBinding type = node.getType().resolveBinding();
        return visitMethodReference(node.getName(), node.resolveMethodBinding(), internalTypeNameOf(type));
    }

    @Override
    public boolean visit(SuperMethodReference node) {
        String superTypeName = getDeclarations().get(currentInternalTypeName) instanceof TypeDeclarationData tdd ? tdd.getSuperTypeName() : null;
        return visitMethodReference(node.getName(), node.resolveMethodBinding(), superTypeName);
    }

    private boolean visitMethodReference(SimpleName name, IMethodBinding binding, String fallbackTypeName) {
        if (binding != null && !binding.isRecovered()) {
            return visitMethodBinding(name, binding);
        }
        if (fallbackTypeName != null && !fallbackTypeName.isEmpty()) {
            ReferenceData refData = newReferenceData(fallbackTypeName, name.getIdentifier(), "(*)?");
            addHyperlink(new HyperlinkReferenceData(name.getStartPosition(), name.getLength(), refData));
        }
        return true;
    }

    /** A reference to a constructor (Type::new): the link is on "new", to the constructor of the binding or, without one, to any constructor of the type. */
    @Override
    public boolean visit(CreationReference node) {
        IMethodBinding binding = node.resolveMethodBinding();
        ITypeBinding type = node.getType().resolveBinding();
        String typeName = binding != null && !binding.isRecovered() && binding.getDeclaringClass().getBinaryName() != null
                ? binding.getDeclaringClass().getErasure().getBinaryName().replace('.', '/')
                : resolveInternalTypeName(node.getType());
        if (typeName != null && !typeName.isEmpty() && type != null && !type.isArray()) {
            String descriptor = binding != null && !binding.isRecovered() ? getErasedParametersDescriptor(binding).append('V').toString() : "(*)V";
            ReferenceData refData = newReferenceData(typeName, StringConstants.INSTANCE_CONSTRUCTOR, descriptor);
            int length = "new".length();
            addHyperlink(new HyperlinkReferenceData(node.getStartPosition() + node.getLength() - length, length, refData));
        }
        return true;
    }

    private boolean visitMethodBinding(SimpleName name, IMethodBinding methodBinding) {
        if (methodBinding != null) {
            String methodName = name.getIdentifier();
            ITypeBinding declaringClass = methodBinding.getDeclaringClass();
            String binaryName = declaringClass.getBinaryName();
            if (binaryName != null) {
                String methodTypeName = binaryName.replace('.', '/');
                ITypeBinding[] args = methodBinding.getParameterTypes();
                ITypeBinding returnType = methodBinding.getReturnType();
                String methodDescriptor = args.length > 0 ? getParametersDescriptor(args).append(returnType.getKey()).toString() : "()" + returnType.getKey();
                ReferenceData refData = newReferenceData(methodTypeName, methodName, methodDescriptor);
                int position = name.getStartPosition();
                addHyperlink(new HyperlinkReferenceData(position, methodName.length(), refData));
            }
        }
        return true;
    }

    public ReferenceData newReferenceData(String internalName, String name, String descriptor,
            String scopeInternalName) {
        String key = key(internalName, name, descriptor, scopeInternalName);
        return referencesCache.computeIfAbsent(key, k -> {
            ReferenceData reference = new ReferenceData(internalName, name, descriptor, scopeInternalName);
            references.add(reference);
            return reference;
        });
    }

    public ReferenceData newReferenceData(String internalName, String name, String descriptor) {
        return newReferenceData(internalName, name, descriptor, currentInternalTypeName);
    }

    /** --- Add strings --- */
    @Override
    public boolean visit(StringLiteral node) {
        strings.add(new StringData(node.getStartPosition() + 1, node.getLiteralValue(), currentInternalTypeName));
        return true;
    }

    public List<ReferenceData> getReferences() {
        return references;
    }

    public List<StringData> getStrings() {
        return strings;
    }

    public SortedMap<Integer, HyperlinkData> getHyperlinks() {
        return hyperlinks;
    }

    public void setHyperlinks(SortedMap<Integer, HyperlinkData> hyperlinks) {
        this.hyperlinks = hyperlinks;
    }

    public void clearData() {
        sbTypeDeclaration.setLength(0);
        declarationListener.clearData();
        references.clear();
        strings.clear();
        hyperlinks.clear();
    }

    public void addStringData(StringData stringData) {
        strings.add(stringData);
    }

    public void addHyperlink(HyperlinkReferenceData hyperlinkReferenceData) {
        hyperlinks.put(hyperlinkReferenceData.getStartPosition(), hyperlinkReferenceData);
    }

    public Map<String, DeclarationData> getDeclarations() {
        return declarationListener.getDeclarations();
    }

    public NavigableMap<Integer, DeclarationData> getTypeDeclarations() {
        return declarationListener.getTypeDeclarations();
    }

    public void addTypeDeclaration(int position, String internalName, DeclarationData data) {
        declarationListener.addTypeDeclaration(position, internalName, data);
    }

    public void addDeclaration(String key, DeclarationData declarationData) {
        declarationListener.addDeclaration(key, declarationData);
    }

    public DeclarationListener getDeclarationListener() {
        return declarationListener;
    }
}
