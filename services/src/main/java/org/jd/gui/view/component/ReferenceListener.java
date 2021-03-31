package org.jd.gui.view.component;

import static org.jd.gui.util.Key.key;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.ArrayCreation;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.jd.gui.api.model.Container;
import org.jd.gui.util.parser.jdt.core.AbstractJavaListener;
import org.jd.gui.util.parser.jdt.core.Context;
import org.jd.gui.util.parser.jdt.core.DeclarationData;
import org.jd.gui.util.parser.jdt.core.HyperlinkData;
import org.jd.gui.util.parser.jdt.core.HyperlinkReferenceData;
import org.jd.gui.util.parser.jdt.core.ReferenceData;
import org.jd.gui.util.parser.jdt.core.StringData;
import org.jd.gui.util.parser.jdt.core.TypeDeclarationData;

public class ReferenceListener extends AbstractJavaListener {
	protected StringBuilder sbTypeDeclaration = new StringBuilder();
	protected HashMap<String, ReferenceData> referencesCache = new HashMap<>();
	protected String currentInternalTypeName;
	protected Context currentContext = null;

	protected DeclarationListener declarationListener;

	protected List<ReferenceData> references = new ArrayList<>();
	protected List<StringData> strings = new ArrayList<>();
	protected TreeMap<Integer, HyperlinkData> hyperlinks = new TreeMap<>();

	public ReferenceListener(Container.Entry entry) {
		super(entry);
		declarationListener = new DeclarationListener(entry);
	}

	public void init() {
		this.nameToInternalTypeName.putAll(declarationListener.getNameToInternalTypeName());
	}

	// --- Add declarations --- //
	@Override
	public boolean visit(PackageDeclaration node) {
		if (super.visit(node)) {
			if (!packageName.isEmpty()) {
				sbTypeDeclaration.append(packageName).append('/');
			}
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

		if ((length == 0) || (sbTypeDeclaration.charAt(length - 1) == '/')) {
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
		List<SingleVariableDeclaration> formalParameters = node.parameters();
		for (SingleVariableDeclaration formalParameter : formalParameters) {
			int dimensionOnParameter = formalParameter.getExtraDimensions();
			String descriptor = createDescriptor(formalParameter.getType(), dimensionOnParameter);
			String name = formalParameter.getName().getIdentifier();
			currentContext.put(name, descriptor);
		}
	}

	// --- Add references --- //
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
		List<VariableDeclarationFragment> fragments = node.fragments();
		for (VariableDeclarationFragment fragment : fragments) {
			int dimensionOnVariable = fragment.getExtraDimensions();
			String descriptor = createDescriptor(node.getType(), dimensionOnVariable);
			String name = fragment.getName().getIdentifier();

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
		List<Expression> expressionList = node.arguments();
		String descriptor = (expressionList != null) ? getParametersDescriptor(expressionList).append('V').toString()
				: "()V";

		ReferenceData refData = newReferenceData(internalTypeName, "<init>", descriptor);
		addHyperlink(new HyperlinkReferenceData(position, node.getLength(), refData));
		return true;
	}

	private StringBuilder getParametersDescriptor(List<Expression> expressionList) {
		StringBuilder sb = new StringBuilder("(");
		for (Expression exp : expressionList) {
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
		List<Expression> args = node.arguments();
		String methodDescriptor = (args != null) ? getParametersDescriptor(args).append('?').toString() : "()?";
		ReferenceData refData = newReferenceData(currentInternalTypeName, "<init>", methodDescriptor);
		int position = node.getStartPosition();
		addHyperlink(new HyperlinkReferenceData(position, 4 /* this */, refData));
		return true;
	}

	@Override
	public boolean visit(SuperConstructorInvocation node) {
		List<Expression> args = node.arguments();
		String methodDescriptor = (args != null) ? getParametersDescriptor(args).append('?').toString() : "()?";
		DeclarationData data = getDeclarations().get(currentInternalTypeName);

		if (data instanceof TypeDeclarationData) {
			String methodTypeName = ((TypeDeclarationData) data).getSuperTypeName();
			ReferenceData refData = newReferenceData(methodTypeName, "<init>", methodDescriptor);
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
		ReferenceData reference = referencesCache.get(key);

		if (reference == null) {
			reference = new ReferenceData(internalName, name, descriptor, scopeInternalName);
			referencesCache.put(key, reference);
			references.add(reference);
		}

		return reference;
	}

	public ReferenceData newReferenceData(String internalName, String name, String descriptor) {
		return newReferenceData(internalName, name, descriptor, currentInternalTypeName);
	}

	// --- Add strings --- //
	@Override
	public boolean visit(StringLiteral node) {
		strings.add(new StringData(node.getStartPosition(), node.getLiteralValue(), currentInternalTypeName));
		return true;
	}

	public List<ReferenceData> getReferences() {
		return references;
	}

	public void setReferences(List<ReferenceData> references) {
		this.references = references;
	}

	public List<StringData> getStrings() {
		return strings;
	}

	public void setStrings(List<StringData> strings) {
		this.strings = strings;
	}

	public TreeMap<Integer, HyperlinkData> getHyperlinks() {
		return hyperlinks;
	}

	public void setHyperlinks(TreeMap<Integer, HyperlinkData> hyperlinks) {
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

	public void setDeclarations(Map<String, DeclarationData> declarations) {
		declarationListener.setDeclarations(declarations);
	}

	public TreeMap<Integer, DeclarationData> getTypeDeclarations() {
		return declarationListener.getTypeDeclarations();
	}

	public void setTypeDeclarations(TreeMap<Integer, DeclarationData> typeDeclarations) {
		declarationListener.setTypeDeclarations(typeDeclarations);
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