package org.jd.gui.view.component;

import org.eclipse.jdt.core.dom.*;
import org.jd.core.v1.util.StringConstants;
import org.jd.gui.api.model.Container;
import org.jd.gui.util.parser.jdt.core.*;

import java.util.*;

import static org.jd.gui.util.Key.key;

public class ReferenceListener extends AbstractJavaListener {
    protected StringBuilder sbTypeDeclaration = new StringBuilder();
    protected Map<String, ReferenceData> referencesCache = new HashMap<>();
    protected String currentInternalTypeName;
    protected Context currentContext;

    protected DeclarationListener declarationListener;

    protected final List<ReferenceData> references = new ArrayList<>();
    protected final List<StringData> strings = new ArrayList<>();
    protected SortedMap<Integer, HyperlinkData> hyperlinks = new TreeMap<>();

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
        currentContext = new Context(currentContext);
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

    protected void enterFormalParameters(MethodDeclaration node) {
        @SuppressWarnings("unchecked")
		List<SingleVariableDeclaration> formalParameters = node.parameters();
        int dimensionOnParameter;
        String descriptor;
        String name;
        for (SingleVariableDeclaration formalParameter : formalParameters) {
            dimensionOnParameter = formalParameter.getExtraDimensions();
            descriptor = createDescriptor(formalParameter.getType(), dimensionOnParameter);
            name = formalParameter.getName().getIdentifier();
            currentContext.put(name, descriptor);
        }
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
        if (currentContext != null) {
            SimpleName fieldNameNode = node.getName();
            Name qualifier = node.getQualifier();
            if (qualifier instanceof SimpleName) {
                SimpleName qualifierName = (SimpleName) qualifier;
                String qualifierIdentifier = qualifierName.getIdentifier();
                String qualifierDescriptor = currentContext.getDescriptor(qualifierIdentifier);
                if (qualifierDescriptor != null && qualifierDescriptor.charAt(0) == 'L') {
                    // Qualifier is a local variable or a method parameter
                    // Qualified name is a field
                    String qualifierTypeName = qualifierDescriptor.substring(1, qualifierDescriptor.length() - 1);
                    String fieldName = fieldNameNode.getIdentifier();
                    ReferenceData refData = newReferenceData(qualifierTypeName, fieldName, "?");
                    int position = fieldNameNode.getStartPosition();
                    int length = fieldNameNode.getLength();
                    addHyperlink(new HyperlinkReferenceData(position, length, refData));
                }
            }
        }
        return true;
    }

    @Override
    public boolean visit(VariableDeclarationStatement node) {
        @SuppressWarnings("unchecked")
		List<VariableDeclarationFragment> fragments = node.fragments();
        int dimensionOnVariable;
        String descriptor;
        String name;
        for (VariableDeclarationFragment fragment : fragments) {
            dimensionOnVariable = fragment.getExtraDimensions();
            descriptor = createDescriptor(node.getType(), dimensionOnVariable);
            name = fragment.getName().getIdentifier();

            currentContext.put(name, descriptor);
        }
        return true;
    }

    @Override
    public boolean visit(SingleVariableDeclaration node) {
        int dimensionOnVariable = node.getExtraDimensions();
        String descriptor = createDescriptor(node.getType(), dimensionOnVariable);
        String name = node.getName().getIdentifier();

        currentContext.put(name, descriptor);
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
        String descriptor = (expressionList != null) ? getParametersDescriptor(expressionList).append('V').toString()
                : "()V";

        ReferenceData refData = newReferenceData(internalTypeName, StringConstants.INSTANCE_CONSTRUCTOR, descriptor);
        addHyperlink(new HyperlinkReferenceData(position, node.getLength(), refData));
        return true;
    }

    private static StringBuilder getParametersDescriptor(List<Expression> expressionList) {
        StringBuilder sb = new StringBuilder("(");
        for (@SuppressWarnings("unused") Expression exp : expressionList) {
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

    @Override
    public boolean visit(FieldAccess node) {
        IVariableBinding fieldBinding = node.resolveFieldBinding();
        if (fieldBinding != null) {
            String fieldName = node.getName().getIdentifier();
            ITypeBinding fieldDeclaringClass = fieldBinding.getDeclaringClass();
            if (fieldDeclaringClass != null) {
                String binaryName = fieldDeclaringClass.getBinaryName();
                if (binaryName != null) {
                    String fieldTypeName = binaryName.replace('.', '/');
                    int position = node.getName().getStartPosition();
                    ReferenceData refData = newReferenceData(fieldTypeName, fieldName, "?");
                    addHyperlink(new HyperlinkReferenceData(position, fieldName.length(), refData));
                }
            }
        }
        return true;
    }

    @Override
    public boolean visit(ConstructorInvocation node) {
        @SuppressWarnings("unchecked")
		List<Expression> args = node.arguments();
        String methodDescriptor = (args != null) ? getParametersDescriptor(args).append('?').toString() : "()?";
        ReferenceData refData = newReferenceData(currentInternalTypeName, StringConstants.INSTANCE_CONSTRUCTOR, methodDescriptor);
        int position = node.getStartPosition();
        addHyperlink(new HyperlinkReferenceData(position, 4 /* this */, refData));
        return true;
    }

    @Override
    public boolean visit(SuperConstructorInvocation node) {
        @SuppressWarnings("unchecked")
		List<Expression> args = node.arguments();
        String methodDescriptor = (args != null) ? getParametersDescriptor(args).append('?').toString() : "()?";
        DeclarationData data = getDeclarations().get(currentInternalTypeName);

        if (data instanceof TypeDeclarationData) {
            String methodTypeName = ((TypeDeclarationData) data).getSuperTypeName();
            ReferenceData refData = newReferenceData(methodTypeName, StringConstants.INSTANCE_CONSTRUCTOR, methodDescriptor);
            int position = node.getStartPosition();
            addHyperlink(new HyperlinkReferenceData(position, 5 /* super */, refData));
        }
        return true;
    }

    @Override
    public boolean visit(MethodInvocation node) {
        IMethodBinding methodBinding = node.resolveMethodBinding();
        if (methodBinding != null) {
            String methodName = node.getName().getIdentifier();
            ITypeBinding declaringClass = methodBinding.getDeclaringClass();
            String binaryName = declaringClass.getBinaryName();
            if (binaryName != null) {
                String methodTypeName = binaryName.replace('.', '/');
                @SuppressWarnings("unchecked")
				List<Expression> args = node.arguments();
                String methodDescriptor = !args.isEmpty() ? getParametersDescriptor(args).append('?').toString()
                        : "()?";
                ReferenceData refData = newReferenceData(methodTypeName, methodName, methodDescriptor);
                int position = node.getName().getStartPosition();
                addHyperlink(new HyperlinkReferenceData(position, methodName.length(), refData));
            }
        }
        Expression subject = node.getExpression();
        if (subject instanceof SimpleName) {
            SimpleName subjectName = (SimpleName) subject;
            int position = subjectName.getStartPosition();
            String internalTypeName = nameToInternalTypeName.get(subjectName.getIdentifier());
            if (internalTypeName != null) {
                ReferenceData refData = newReferenceData(internalTypeName, null, null);
                addHyperlink(new HyperlinkReferenceData(position, subjectName.getLength(), refData));
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
        strings.add(new StringData(node.getStartPosition(), node.getLiteralValue(), currentInternalTypeName));
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