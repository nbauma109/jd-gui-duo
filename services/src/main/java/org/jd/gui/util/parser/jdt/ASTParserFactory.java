package org.jd.gui.util.parser.jdt;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.core.v1.util.StringConstants;
import org.jd.gui.api.model.Container;
import org.jd.gui.util.decompiler.ContainerLoader;

import com.heliosdecompiler.transformerapi.common.ClasspathUtil;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ASTParserFactory {

    private static final Pattern BUILD_JDK_VERSION = Pattern.compile("^(?:1\\.(\\d+)|(\\d+))(?:\\D.*)?$");
    private static final String DEFAULT_JDK_VERSION = JavaCore.VERSION_25;

    private static final ASTParserFactory INSTANCE = new ASTParserFactory(false, false, false);
    private static final ASTParserFactory BINDING_INSTANCE = new ASTParserFactory(true, true, true);

    private ASTParserFactory(boolean resolveBindings, boolean bindingRecovery, boolean statementRecovery) {
        this.resolveBindings = resolveBindings;
        this.bindingRecovery = bindingRecovery;
        this.statementRecovery = statementRecovery;
    }

    public static ASTParserFactory getInstance() {
        return INSTANCE;
    }

    public static ASTParserFactory getInstanceWithBindings() {
        return BINDING_INSTANCE;
    }

    private static final Map<URI, String> jarTojdkVersion = new ConcurrentHashMap<>();

    private final boolean resolveBindings;
    private final boolean bindingRecovery;
    private final boolean statementRecovery;

    public ASTParser newASTParser(Container.Entry entry) throws IOException {
        URI jarURI = entry.getContainer().getRoot().getParent().getUri();
        char[] source = ContainerLoader.loadEntry(entry, StandardCharsets.UTF_8);
        String unitName = entry.getPath();
        return newASTParser(source, unitName, jarURI);
    }

    public ASTParser newASTParser(char[] source, String unitName, URI jarURI) {
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(source);
        parser.setResolveBindings(resolveBindings);
        parser.setBindingsRecovery(bindingRecovery);
        parser.setStatementsRecovery(statementRecovery);
        List<String> jdkClasspath = ClasspathUtil.getJDKClasspath();
        String[] classpathEntries = ClasspathUtil.createClasspathEntries(jarURI, jdkClasspath);
        boolean includeRunningVMBootclasspath = jdkClasspath.isEmpty();
        if (unitName.endsWith(".java")) {
            String[] sourcepathEntries = { jarURI.getPath() };
            String[] encodings = { StandardCharsets.UTF_8.name() };
            parser.setEnvironment(classpathEntries, sourcepathEntries, encodings, includeRunningVMBootclasspath);
            parser.setUnitName(unitName);
        }
        if (unitName.endsWith(StringConstants.CLASS_FILE_SUFFIX)) {
            parser.setEnvironment(classpathEntries, null, null, includeRunningVMBootclasspath);
            parser.setUnitName(unitName.replace(StringConstants.CLASS_FILE_SUFFIX, ".java"));
        }

        Map<String, String> options = getDefaultOptions();
        String majorVersion = jarTojdkVersion.computeIfAbsent(jarURI, ASTParserFactory::resolveJDKVersion);
        options.put(JavaCore.COMPILER_COMPLIANCE, majorVersion);
        options.put(JavaCore.COMPILER_SOURCE, majorVersion);
        options.put(JavaCore.COMPILER_PB_MAX_PER_UNIT, String.valueOf(Integer.MAX_VALUE));
        options.put(JavaCore.COMPILER_PB_UNNECESSARY_TYPE_CHECK, "warning");
        parser.setCompilerOptions(options);
        return parser;
    }

    private static String resolveJDKVersion(URI jarURI) {
        File file = new File(jarURI);
        String majorVersion = DEFAULT_JDK_VERSION;
        if (file.isFile() && file.getName().endsWith(".jar")) {
            try (JarFile jarFile = new JarFile(file)) {
                Manifest manifest = jarFile.getManifest();
                if (manifest != null) {
                    Attributes mainAttributes = manifest.getMainAttributes();
                    if (mainAttributes != null) {
                        String jdkVersion = mainAttributes.getValue("Build-Jdk");
                        if (jdkVersion != null) {
                            majorVersion = resolveJDKVersion(jdkVersion);
                        }
                    }
                }
            } catch (IOException e) {
                assert ExceptionUtil.printStackTrace(e);
            }
        }
        return majorVersion;
    }

    private static Map<String, String> getDefaultOptions() {
        Map<String, String> options = JavaCore.getOptions();
        options.put(JavaCore.CORE_ENCODING, StandardCharsets.UTF_8.name());
        options.put(JavaCore.COMPILER_COMPLIANCE, DEFAULT_JDK_VERSION);
        options.put(JavaCore.COMPILER_SOURCE, DEFAULT_JDK_VERSION);
        return options;
    }

    static String resolveJDKVersion(String longVersion) {
        return Optional.ofNullable(longVersion)
                .map(String::trim)
                .filter(v -> !v.isEmpty())
                .map(ASTParserFactory::resolveWithSingleRegex)
                .orElse(DEFAULT_JDK_VERSION);
    }

    private static String resolveWithSingleRegex(String buildJdk) {
        Matcher matcher = BUILD_JDK_VERSION.matcher(buildJdk);
        if (!matcher.matches()) {
            return DEFAULT_JDK_VERSION;
        }

        Optional<String> legacyMinor = Optional.ofNullable(matcher.group(1));
        if (legacyMinor.isPresent()) {
            return "1." + legacyMinor.get();
        }
        return matcher.group(2);
    }
}
