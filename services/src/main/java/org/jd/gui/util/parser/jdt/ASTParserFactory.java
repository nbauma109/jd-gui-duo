package org.jd.gui.util.parser.jdt;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Hashtable;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.jd.core.v1.api.loader.LoaderException;
import org.jd.gui.api.model.Container;
import org.jd.gui.util.decompiler.ContainerLoader;

public class ASTParserFactory {

	private ASTParserFactory(boolean resolveBindings, boolean bindingRecovery, boolean statementRecovery) {
		this.resolveBindings = resolveBindings;
		this.bindingRecovery = bindingRecovery;
		this.statementRecovery = statementRecovery;
	}

	public static ASTParserFactory getInstance() {
		return ASTParserFactoryHolder.INSTANCE;
	}

	public static ASTParserFactory getInstanceWithBindings() {
		return ASTParserFactoryHolder.BINDING_INSTANCE;
	}
	
	private static class ASTParserFactoryHolder {
		private static final ASTParserFactory INSTANCE = new ASTParserFactory(false, false, false);
		private static final ASTParserFactory BINDING_INSTANCE = new ASTParserFactory(true, true, true);
	}

	private boolean resolveBindings;
	private boolean bindingRecovery;
	private boolean statementRecovery;

	public ASTParser newASTParser(Container.Entry containerEntry, ASTVisitor listener) throws LoaderException {
		URI jarURI = containerEntry.getContainer().getRoot().getParent().getUri();
		char[] source = ContainerLoader.loadEntry(containerEntry, StandardCharsets.UTF_8);
		String unitName = containerEntry.getPath();
		return newASTParser(source, unitName, jarURI, listener);
	}

	public ASTParser newASTParser(char[] source, String unitName, URI jarURI, ASTVisitor listener) {
		ASTParser parser = ASTParser.newParser(AST.JLS8);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(source);
		parser.setResolveBindings(resolveBindings);
		parser.setBindingsRecovery(bindingRecovery);
		parser.setStatementsRecovery(statementRecovery);
		if (unitName.endsWith(".java")) {
			String[] sourcepathEntries = new String[] { jarURI.getPath() };
			String[] encodings = new String[] { StandardCharsets.UTF_8.name() };
			parser.setEnvironment(null, sourcepathEntries, encodings, false);
			parser.setUnitName(unitName);
		}
		if (unitName.endsWith(".class")) {
			String[] classpathEntries = new String[] { jarURI.getPath() };
			parser.setEnvironment(classpathEntries, null, null, false);
			parser.setUnitName(unitName.replace(".class", ".java"));
		}
		Hashtable<String, String> options = JavaCore.getOptions();
		options.put(JavaCore.COMPILER_COMPLIANCE, JavaCore.VERSION_1_8);
		options.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_1_8);
		options.put(JavaCore.CORE_ENCODING, StandardCharsets.UTF_8.name());
		parser.setCompilerOptions(options);

		parser.createAST(null).accept(listener);
		return parser;
	}

	public void setResolveBindings(boolean resolveBindings) {
		this.resolveBindings = resolveBindings;
	}

	public void setBindingRecovery(boolean bindingRecovery) {
		this.bindingRecovery = bindingRecovery;
	}

	public void setStatementRecovery(boolean statementRecovery) {
		this.statementRecovery = statementRecovery;
	}
}
