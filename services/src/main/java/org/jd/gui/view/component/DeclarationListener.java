package org.jd.gui.view.component;

import static org.jd.gui.util.Key.key;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.jd.gui.api.model.Container;
import org.jd.gui.util.parser.jdt.core.AbstractJavaListener;
import org.jd.gui.util.parser.jdt.core.DeclarationData;
import org.jd.gui.util.parser.jdt.core.TypeDeclarationData;

public class DeclarationListener extends AbstractJavaListener {
	protected StringBuilder sbTypeDeclaration = new StringBuilder();
	protected String currentInternalTypeName;

	protected Map<String, DeclarationData> declarations = new HashMap<>();
	protected TreeMap<Integer, DeclarationData> typeDeclarations = new TreeMap<>();

	public DeclarationListener(Container.Entry entry) {
		super(entry);
	}

	public HashMap<String, String> getNameToInternalTypeName() {
		return super.nameToInternalTypeName;
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
	protected boolean enterTypeDeclaration(AbstractTypeDeclaration node, int flag) {
		// Type declaration
		String typeName = node.getName().getIdentifier();
		int position = node.getName().getStartPosition();
		int length = sbTypeDeclaration.length();

		if ((length == 0) || (sbTypeDeclaration.charAt(length - 1) == '/')) {
			sbTypeDeclaration.append(typeName);
		} else {
			sbTypeDeclaration.append('$').append(typeName);
		}

		currentInternalTypeName = sbTypeDeclaration.toString();
		nameToInternalTypeName.put(typeName, currentInternalTypeName);

		// Super type reference
		Type superType = getSuperType(node);
		String superInternalTypeName = (superType != null) ? resolveInternalTypeName(superType) : null;
		TypeDeclarationData data = new TypeDeclarationData(position, typeName.length(), currentInternalTypeName,
				superInternalTypeName);

		declarations.put(currentInternalTypeName, data);
		typeDeclarations.put(position, data);
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

	@Override
	public boolean visit(Initializer node) {
		int position = node.getStartPosition();
		String key = key(currentInternalTypeName, "<clinit>", "()V");
		DeclarationData data = new TypeDeclarationData(position, 6, currentInternalTypeName, "()V");
		declarations.put(key, data);
		return true;
	}
	
	@Override
	public boolean visit(FieldDeclaration node) {
		List<VariableDeclarationFragment> fragments = node.fragments();
		for (VariableDeclarationFragment fragment : fragments) {
			String name = fragment.getName().getIdentifier();
			int dimensionOnVariable = fragment.getExtraDimensions();
			int position = fragment.getName().getStartPosition();
			String descriptor = createDescriptor(node.getType(), dimensionOnVariable);
			String key = key(currentInternalTypeName, name, descriptor);
			DeclarationData data = new DeclarationData(position, name.length(), currentInternalTypeName, name, descriptor);
			declarations.put(key, data);
		}
		return true;
	}

	@Override
	public boolean visit(MethodDeclaration node) {
		SimpleName nodeName = node.getName();
		String name = nodeName.getIdentifier();
        String paramDescriptors = createParamDescriptors(node.parameters());
        String returnDescriptor = createDescriptor(node.getReturnType2(), 0);
        String descriptor = paramDescriptors + returnDescriptor;
		int position = nodeName.getStartPosition();
		String key = key(currentInternalTypeName, node.isConstructor() ? "<init>" : name, descriptor);
		DeclarationData data = new DeclarationData(position, nodeName.getLength(), currentInternalTypeName, name, descriptor);
		declarations.put(key, data);
		return true;
	}

	public Map<String, DeclarationData> getDeclarations() {
		return declarations;
	}

	public void setDeclarations(Map<String, DeclarationData> declarations) {
		this.declarations = declarations;
	}

	public TreeMap<Integer, DeclarationData> getTypeDeclarations() {
		return typeDeclarations;
	}

	public void setTypeDeclarations(TreeMap<Integer, DeclarationData> typeDeclarations) {
		this.typeDeclarations = typeDeclarations;
	}

	public void clearData() {
		declarations.clear();
		typeDeclarations.clear();
	}

	public void addTypeDeclaration(int position, String internalName, DeclarationData data) {
		typeDeclarations.put(position, data);
		declarations.put(internalName, data);
	}

	public void addDeclaration(String key, DeclarationData declarationData) {
		declarations.put(key, declarationData);
	}
}