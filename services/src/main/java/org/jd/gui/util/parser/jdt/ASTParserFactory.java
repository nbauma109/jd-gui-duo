/*
 * © 2021-2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.util.parser.jdt;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.jd.core.v1.util.StringConstants;
import org.jd.gui.api.API;
import org.jd.gui.api.model.Container;
import org.jd.gui.util.JavaHomeResolver;
import org.jd.gui.util.decompiler.ContainerLoader;

import com.heliosdecompiler.transformerapi.common.ClasspathUtil;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.eclipse.jdt.core.JavaCore.COMPILER_COMPLIANCE;
import static org.eclipse.jdt.core.JavaCore.COMPILER_SOURCE;
import static org.eclipse.jdt.core.JavaCore.COMPILER_PB_MAX_PER_UNIT;
import static org.eclipse.jdt.core.JavaCore.COMPILER_PB_UNNECESSARY_TYPE_CHECK;
import static org.eclipse.jdt.core.JavaCore.CORE_ENCODING;
import static org.jd.gui.util.decompiler.GuiPreferences.INCLUDE_RUNNING_VM_BOOT_CLASSPATH;
import static org.jd.gui.util.decompiler.GuiPreferences.JRE_SYSTEM_LIBRARY_PATH;

public final class ASTParserFactory {

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

    private final boolean resolveBindings;
    private final boolean bindingRecovery;
    private final boolean statementRecovery;

    public ASTParser newASTParser(API api, Container.Entry entry) throws IOException {
        URI jarURI = entry.getContainer().getRoot().getParent().getUri();
        char[] source = ContainerLoader.loadEntry(entry, StandardCharsets.UTF_8);
        String unitName = entry.getPath();
        return newASTParser(api, source, unitName, jarURI);
    }

    public ASTParser newASTParser(API api, char[] source, String unitName, URI jarURI) {
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(source);
        parser.setResolveBindings(resolveBindings);
        parser.setBindingsRecovery(bindingRecovery);
        parser.setStatementsRecovery(statementRecovery);
        List<String> jdkClassPath;
        boolean includeRunningVMBootclasspath;
        if (!"false".equals(api.getPreferences().get(INCLUDE_RUNNING_VM_BOOT_CLASSPATH))) {
            includeRunningVMBootclasspath = true;
            jdkClassPath = Collections.emptyList();
        } else {
            includeRunningVMBootclasspath = false;
            jdkClassPath = getJDKClasspath(api.getPreferences().get(JRE_SYSTEM_LIBRARY_PATH));
        }
        String[] classpathEntries = ClasspathUtil.createClasspathEntries(jarURI, jdkClassPath);
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

        Map<String, String> options = getOptions(api);
        options.put(COMPILER_PB_MAX_PER_UNIT, String.valueOf(Integer.MAX_VALUE));
        options.put(COMPILER_PB_UNNECESSARY_TYPE_CHECK, "warning");
        parser.setCompilerOptions(options);
        return parser;
    }

    public static List<String> getJDKClasspath(String javaHome) {
        List<String> cpEntries = new ArrayList<>();

        if (StringUtils.isBlank(javaHome)) {
            return cpEntries;
        }

        File home = new File(javaHome.trim());
        if (!home.isDirectory()) {
            return cpEntries;
        }

        File normalizedHome = JavaHomeResolver.normalizeJavaHome(home);
        if (normalizedHome != null) {
            home = normalizedHome;
        }

        // Java 9+ : JRT via jrt-fs.jar (must be a real file path, not "jrt:/")
        File jrtFsJar = new File(home, "lib/jrt-fs.jar");
        if (jrtFsJar.isFile()) {
            cpEntries.add(jrtFsJar.getAbsolutePath());
            return cpEntries;
        }

        // Java 8- : rt.jar (JDK layout vs JRE layout)
        File rtJar = new File(home, "jre/lib/rt.jar");
        if (rtJar.isFile()) {
            cpEntries.add(rtJar.getAbsolutePath());
            return cpEntries;
        }

        rtJar = new File(home, "lib/rt.jar");
        if (rtJar.isFile()) {
            cpEntries.add(rtJar.getAbsolutePath());
        }

        return cpEntries;
    }

    private static Map<String, String> getOptions(API api) {
        Map<String, String> options = JavaCore.getOptions();
        options.put(CORE_ENCODING, StandardCharsets.UTF_8.name());
        options.put(COMPILER_COMPLIANCE, api.getPreferences().getOrDefault(COMPILER_COMPLIANCE, JavaCore.latestSupportedJavaVersion()));
        options.put(COMPILER_SOURCE, api.getPreferences().getOrDefault(COMPILER_SOURCE, JavaCore.latestSupportedJavaVersion()));
        return options;
    }
}
