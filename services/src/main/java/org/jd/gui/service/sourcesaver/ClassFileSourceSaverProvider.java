/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.sourcesaver;

import org.jd.core.v1.ClassFileToJavaSourceDecompiler;
import org.jd.core.v1.printer.LineNumberStringBuilderPrinter;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ByteCodeWriter;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.api.API;
import org.jd.gui.api.model.Container;
import org.jd.gui.util.MethodPatcher;
import org.jd.gui.util.decompiler.ContainerLoader;

import com.heliosdecompiler.transformerapi.StandardTransformers;
import com.heliosdecompiler.transformerapi.common.Loader;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static com.heliosdecompiler.transformerapi.StandardTransformers.Decompilers.ENGINE_JD_CORE_V0;
import static com.heliosdecompiler.transformerapi.StandardTransformers.Decompilers.ENGINE_JD_CORE_V1;
import static org.jd.gui.util.decompiler.GuiPreferences.DECOMPILE_ENGINE;

import jd.core.ClassUtil;
import jd.core.DecompilationResult;

public class ClassFileSourceSaverProvider extends AbstractSourceSaverProvider {

    private static final String INTERNAL_ERROR = "// INTERNAL ERROR //";

    protected static final ClassFileToJavaSourceDecompiler DECOMPILER = new ClassFileToJavaSourceDecompiler();

    protected ContainerLoader loader = new ContainerLoader();
    protected LineNumberStringBuilderPrinter printer = new LineNumberStringBuilderPrinter();

    @Override
    public String[] getSelectors() { return appendSelectors("*:file:*.class"); }

    @Override
    public String getSourcePath(Container.Entry entry) {
        String path = entry.getPath();
        int index = path.lastIndexOf('.');
        String prefix = index == -1 ? path : path.substring(0, index);
        return prefix + ".java";
    }

    @Override
    public int getFileCount(API api, Container.Entry entry) {
        if (entry.getPath().indexOf('$') == -1) {
            return 1;
        }
        return 0;
    }

    @Override
    public void save(API api, Controller controller, Listener listener, Path rootPath, Container.Entry entry) {
        String sourcePath = getSourcePath(entry);
        Path path = rootPath.resolve(sourcePath);

        saveContent(api, controller, listener, rootPath, path, entry);
    }

    @Override
    public void saveContent(API api, Controller controller, Listener listener, Path rootPath, Path path, Container.Entry entry) {

        DecompilationResult decompiledResult = new DecompilationResult();
        
        try {
            // Call listener
            if (path.toString().indexOf('$') == -1) {
                listener.pathSaved(path);
            }
            // Init preferences
            Map<String, String> preferences = api.getPreferences();

            // Init loader
            loader.setEntry(entry);

            // Format internal name
            String entryInternalName = ClassUtil.getInternalName(entry.getPath());
            
            String decompileEngine = preferences.getOrDefault(DECOMPILE_ENGINE, ENGINE_JD_CORE_V1);
            Loader apiLoader = new Loader(loader::canLoad, loader::load);
            decompiledResult = StandardTransformers.decompile(apiLoader, entryInternalName, preferences, decompileEngine);
            if (decompiledResult.getDecompiledOutput().contains(ByteCodeWriter.DECOMPILATION_FAILED_AT_LINE)) {
                DecompilationResult sourceCodeV0 = StandardTransformers.decompile(apiLoader, entryInternalName, preferences, ENGINE_JD_CORE_V0);
                decompiledResult.setDecompiledOutput(MethodPatcher.patchCode(decompiledResult.getDecompiledOutput(), sourceCodeV0.getDecompiledOutput(), entry));
            }

        } catch (Exception t) {
            decompiledResult.setDecompiledOutput(INTERNAL_ERROR);
            assert ExceptionUtil.printStackTrace(t);
        }
        
        writeCodeToFile(path, decompiledResult.getDecompiledOutput());
    }

    private static void writeCodeToFile(Path path, String sourceCode) {
        try (OutputStream os = Files.newOutputStream(path)) {
            os.write(sourceCode.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            assert ExceptionUtil.printStackTrace(e);
        }
    }
}
