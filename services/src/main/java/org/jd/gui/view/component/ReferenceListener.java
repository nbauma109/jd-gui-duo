package org.jd.gui.view.component;

import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.ArrayCreation;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
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
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.Type;
import org.jd.core.v1.util.StringConstants;
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
        int position = node.getName().getStartPosition();
        String internalTypeName = nameToString(node.getName());
        ReferenceData refData = newReferenceData(internalTypeName, null, null, null);
        addHyperlink(new HyperlinkReferenceData(position, internalTypeName.length(), refData));
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
        Name name = node.getName();
        String internalTypeName = resolveInternalTypeName(node);
        int position = node.getStartPosition();
        ReferenceData refData = newReferenceData(internalTypeName, null, null);
        addHyperlink(new HyperlinkReferenceData(position, name.getLength(), refData));
        return true;
    }

    @Override
    public boolean visit(QualifiedName node) {
        SimpleName fieldNameNode = node.getName();
        String fieldName = fieldNameNode.getIdentifier();
        Name qualifier = node.getQualifier();
        if (qualifier instanceof SimpleName qualifierSimpleName) {
            String qualifierDescriptor = null;
            IBinding binding = qualifier.resolveBinding();
            if (binding instanceof ITypeBinding) {
                qualifierDescriptor = binding.getKey();
            }
            IVariableBinding variableBinding = null;
            if (binding instanceof IVariableBinding) {
                variableBinding = (IVariableBinding) binding;
                ITypeBinding typeBinding = qualifier.resolveTypeBinding();
                if (typeBinding != null) {
                    qualifierDescriptor = typeBinding.getKey();
                }
            }
            if (qualifierDescriptor != null) {
                if (qualifierDescriptor.charAt(0) == 'L') {
                    String qualifierTypeName = qualifierDescriptor.substring(1, qualifierDescriptor.length() - 1);
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
                        String declaringBinaryName = declaringClass.getBinaryName();
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
        String descriptor;
        if (expressionList != null) {
            IMethodBinding constructorBinding = node.resolveConstructorBinding();
            if (constructorBinding != null) {
                ITypeBinding[] parameterTypes = constructorBinding.getParameterTypes();
                descriptor = getParametersDescriptor(parameterTypes).append('V').toString();
            } else {
                descriptor = getParametersDescriptor(expressionList.size()).append('V').toString();
            }
        } else {
            descriptor = "()V";
        }


        ReferenceData refData = newReferenceData(internalTypeName, StringConstants.INSTANCE_CONSTRUCTOR, descriptor);
        addHyperlink(new HyperlinkReferenceData(position, node.getLength(), refData));
        return true;
    }

    private static StringBuilder getParametersDescriptor(ITypeBinding[] argTypes) {
        StringBuilder sb = new StringBuilder("(");
        for (ITypeBinding argType : argTypes) {
            sb.append(argType.getKey());
        }
        sb.append(')');
        return sb;
    }

    private static StringBuilder getParametersDescriptor(int paramCount) {
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < paramCount; i++) {
            sb.append('?');
        }
        sb.append(')');
        return sb;
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
                String binaryName = declaringClass.getBinaryName();
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
        String constructorDescriptor;
        if (args != null) {
            IMethodBinding constructorBinding = node.resolveConstructorBinding();
            if (constructorBinding != null) {
                ITypeBinding[] parameterTypes = constructorBinding.getParameterTypes();
                constructorDescriptor = getParametersDescriptor(parameterTypes).append('V').toString();
            } else {
                constructorDescriptor = getParametersDescriptor(args.size()).append('V').toString();
            }
        } else {
            constructorDescriptor = "()V";
        }
        ReferenceData refData = newReferenceData(currentInternalTypeName, StringConstants.INSTANCE_CONSTRUCTOR, constructorDescriptor);
        int position = node.getStartPosition();
        addHyperlink(new HyperlinkReferenceData(position, 4 /* this */, refData));
        return true;
    }

    @Override
    public boolean visit(SuperConstructorInvocation node) {
        @SuppressWarnings("unchecked")
        List<Expression> args = node.arguments();
        String constructorDescriptor;
        if (args != null) {
            IMethodBinding constructorBinding = node.resolveConstructorBinding();
            if (constructorBinding != null) {
                ITypeBinding[] parameterTypes = constructorBinding.getParameterTypes();
                constructorDescriptor = getParametersDescriptor(parameterTypes).append('V').toString();
            } else {
                constructorDescriptor = getParametersDescriptor(args.size()).append('V').toString();
            }
        } else {
            constructorDescriptor = "()V";
        }
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
        visitMethodBinding(node.getName(), node.resolveMethodBinding());
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
    public boolean visit(ExpressionMethodReference node) {
        return visitMethodBinding(node.getName(), node.resolveMethodBinding());
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
