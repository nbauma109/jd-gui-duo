/*
 * Copyright (c) 2023 GPLv3.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.view.data;

import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.api.API;
import org.jd.gui.api.model.Container.Entry;
import org.jd.gui.util.decompiler.ContainerLoader;
import org.jd.gui.util.loader.LoaderUtils;
import org.jd.gui.util.parser.jdt.ASTParserFactory;

import com.heliosdecompiler.transformerapi.StandardTransformers;
import com.heliosdecompiler.transformerapi.common.Loader;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.swing.Icon;
import javax.swing.SwingWorker;

import static com.heliosdecompiler.transformerapi.StandardTransformers.Decompilers.ENGINE_JD_CORE_V1;
import static org.jd.gui.service.treenode.ClassFileTreeNodeFactoryProvider.CLASS_FILE_ICON;
import static org.jd.gui.service.treenode.ClassFileTreeNodeFactoryProvider.CLASS_FILE_ICON_ERROR;
import static org.jd.gui.service.treenode.ClassFileTreeNodeFactoryProvider.CLASS_FILE_ICON_WARNING;
import static org.jd.gui.util.decompiler.GuiPreferences.DECOMPILE_ENGINE;
import static org.jd.gui.util.decompiler.GuiPreferences.SHOW_COMPILER_ERRORS;
import static org.jd.gui.util.decompiler.GuiPreferences.SHOW_COMPILER_WARNINGS;

import jd.core.ClassUtil;
import jd.core.DecompilationResult;

public class ClassFileTreeNodeBean extends TreeNodeBean {
    private final API api;
    private final Entry entry;
    private final Map<PreferenceKey, ClassFileTreeNodeState> decompilerToState = new ConcurrentHashMap<>();

    public ClassFileTreeNodeBean(API api, String label, Entry entry) {
        super(label, CLASS_FILE_ICON);
        this.api = api;
        this.entry = entry;
    }

    @Override
    public Icon getIcon() {
        Map<String, String> preferences = api.getPreferences();
        ClassFileTreeNodeState state = decompilerToState.get(makeKey(preferences));
        if (state != null) {
            if ("true".equals(api.getPreferences().get(SHOW_COMPILER_ERRORS)) && state.hasErrors()) {
                return CLASS_FILE_ICON_ERROR;
            }
            if ("true".equals(api.getPreferences().get(SHOW_COMPILER_WARNINGS)) && state.hasWarnings()) {
                return CLASS_FILE_ICON_WARNING;
            }
        } else {
            getWorker().execute();
        }
        return CLASS_FILE_ICON;
    }

    private static PreferenceKey makeKey(Map<String, String> preferences) {
        return new PreferenceKey(preferences.getOrDefault(DECOMPILE_ENGINE, ENGINE_JD_CORE_V1), preferences);
    }

    public SwingWorker<Void, Void> getWorker() {
        return new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                Map<String, String> preferences = api.getPreferences();
                decompilerToState.computeIfAbsent(makeKey(preferences), k -> getStateForEntry(entry, api, k.engineName()));
                return null;
            }
            
            @Override
            protected void done() {
                api.repaint();
            }
        };
    }

    @Override
    public Icon getOpenIcon() {
        return getIcon();
    }

    public static synchronized ClassFileTreeNodeState getStateForEntry(Entry entry, API api, String engineName) {
        String unitName = entry.getPath();
        URI jarURI = entry.getContainer().getRoot().getParent().getUri();
        String entryInternalName = ClassUtil.getInternalName(entry.getPath());
        Map<String, String> preferences = api.getPreferences();
        ContainerLoader loader = new ContainerLoader(entry);
        Loader apiLoader = LoaderUtils.createLoader(preferences, loader, entry);
        DecompilationResult decompilationResult;
        try {
            decompilationResult = StandardTransformers.decompile(apiLoader, entryInternalName, preferences, engineName);
        } catch (Exception e) {
            assert ExceptionUtil.printStackTrace(e);
            return new ClassFileTreeNodeState(true, false);
        }
        String text = decompilationResult.getDecompiledOutput();
        ASTNode ast = ASTParserFactory.getInstanceWithBindings().newASTParser(text.toCharArray(), unitName, jarURI).createAST(null);
        boolean hasWarning = false;
        boolean hasError = false;
        if (ast instanceof CompilationUnit) {
            CompilationUnit cu = (CompilationUnit) ast;
            IProblem[] problems = cu.getProblems();
            for (IProblem pb : problems) {
                hasError |= pb.isError();
                hasWarning |= pb.isWarning();
            }
        }
        return new ClassFileTreeNodeState(hasError, hasWarning);
    }
}
