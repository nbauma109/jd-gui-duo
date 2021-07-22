package org.jd.gui.util.parser.jdt;

import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.JavaCore;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.*;
import org.jd.core.v1.api.loader.LoaderException;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.core.v1.util.StringConstants;
import org.jd.gui.api.model.Container;
import org.jd.gui.util.decompiler.ContainerLoader;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.jar.*;

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

    private final boolean resolveBindings;
    private final boolean bindingRecovery;
    private final boolean statementRecovery;

    public ASTParser newASTParser(Container.Entry containerEntry, ASTVisitor listener) throws LoaderException {
        URI jarURI = containerEntry.getContainer().getRoot().getParent().getUri();
        char[] source = ContainerLoader.loadEntry(containerEntry, StandardCharsets.UTF_8);
        String unitName = containerEntry.getPath();
        return newASTParser(source, unitName, jarURI, listener);
    }

    public ASTParser newASTParser(char[] source, String unitName, URI jarURI, ASTVisitor listener) {
        ASTParser parser = ASTParser.newParser(AST.JLS_Latest);
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
        if (unitName.endsWith(StringConstants.CLASS_FILE_SUFFIX)) {
            String[] classpathEntries = new String[] { jarURI.getPath() };
            parser.setEnvironment(classpathEntries, null, null, false);
            parser.setUnitName(unitName.replace(StringConstants.CLASS_FILE_SUFFIX, ".java"));
        }

        Map<String, String> options = getDefaultOptions();
        File file = new File(jarURI);
        if (file.isFile() && file.getName().endsWith(".jar")) {
			try (JarFile jarFile = new JarFile(file)) {
	            Manifest manifest = jarFile.getManifest();
	            if (manifest != null) {
	                Attributes mainAttributes = manifest.getMainAttributes();
	                if (mainAttributes != null) {
	                    String jdkVersion = mainAttributes.getValue("Build-Jdk");
	                    if (jdkVersion != null) {
	                        String majorVersion = resolveJDKVersion(jdkVersion);
	                        options.put(JavaCore.COMPILER_COMPLIANCE, majorVersion);
	                        options.put(JavaCore.COMPILER_SOURCE, majorVersion);
	                    }
	                }
	            }
	        } catch (IOException e) {
	            assert ExceptionUtil.printStackTrace(e);
	        }
        }
        parser.setCompilerOptions(options);

        parser.createAST(null).accept(listener);
        return parser;
    }

    protected Map<String, String> getDefaultOptions() {
        Map<String, String> options = JavaCore.getOptions();
        options.put(JavaCore.CORE_ENCODING, StandardCharsets.UTF_8.name());
        options.put(JavaCore.COMPILER_COMPLIANCE, JavaCore.VERSION_1_8);
        options.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_1_8);
        return options;
    }

    private static final String resolveJDKVersion(String longVersion) {
        if (longVersion.startsWith(JavaCore.VERSION_15)) {
            return JavaCore.VERSION_15;
        }
        if (longVersion.startsWith(JavaCore.VERSION_14)) {
            return JavaCore.VERSION_14;
        }
        if (longVersion.startsWith(JavaCore.VERSION_13)) {
            return JavaCore.VERSION_13;
        }
        if (longVersion.startsWith(JavaCore.VERSION_12)) {
            return JavaCore.VERSION_12;
        }
        if (longVersion.startsWith(JavaCore.VERSION_11)) {
            return JavaCore.VERSION_11;
        }
        if (longVersion.startsWith(JavaCore.VERSION_10)) {
            return JavaCore.VERSION_10;
        }
        if (longVersion.startsWith(JavaCore.VERSION_9)) {
            return JavaCore.VERSION_9;
        }
        if (longVersion.startsWith(JavaCore.VERSION_1_8)) {
            return JavaCore.VERSION_1_8;
        }
        if (longVersion.startsWith(JavaCore.VERSION_1_7)) {
            return JavaCore.VERSION_1_7;
        }
        if (longVersion.startsWith(JavaCore.VERSION_1_6)) {
            return JavaCore.VERSION_1_6;
        }
        if (longVersion.startsWith(JavaCore.VERSION_1_5)) {
            return JavaCore.VERSION_1_5;
        }
        if (longVersion.startsWith(JavaCore.VERSION_1_4)) {
            return JavaCore.VERSION_1_4;
        }
        if (longVersion.startsWith(JavaCore.VERSION_1_3)) {
            return JavaCore.VERSION_1_3;
        }
        if (longVersion.startsWith(JavaCore.VERSION_1_2)) {
            return JavaCore.VERSION_1_2;
        }
        if (longVersion.startsWith(JavaCore.VERSION_1_1)) {
            return JavaCore.VERSION_1_1;
        }
        // default to JAVA 8
        return JavaCore.VERSION_1_8;
    }
}
