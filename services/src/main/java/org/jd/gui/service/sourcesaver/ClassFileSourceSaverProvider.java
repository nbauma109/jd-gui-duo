/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.sourcesaver;

import org.jd.core.v1.printer.LineNumberStringBuilderPrinter;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ByteCodeWriter;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.core.v1.util.StringConstants;
import org.jd.gui.api.API;
import org.jd.gui.api.model.Container;
import org.jd.gui.util.MethodPatcher;
import org.jd.gui.util.ProgressUtil;
import org.jd.gui.util.decompiler.ContainerLoader;
import org.jd.gui.util.loader.LoaderUtils;

import com.heliosdecompiler.transformerapi.StandardTransformers;
import com.heliosdecompiler.transformerapi.common.Loader;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

import static com.heliosdecompiler.transformerapi.StandardTransformers.Decompilers.ENGINE_JD_CORE_V0;
import static com.heliosdecompiler.transformerapi.StandardTransformers.Decompilers.ENGINE_JD_CORE_V1;
import static org.jd.gui.util.decompiler.GuiPreferences.DECOMPILE_ENGINE;

import jd.core.ClassUtil;
import jd.core.DecompilationResult;

public class ClassFileSourceSaverProvider extends AbstractSourceSaverProvider {

    private static final String INTERNAL_ERROR = "// INTERNAL ERROR //";

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
    public void save(API api, Path rootPath, Container.Entry entry, DoubleSupplier getProgressFunction, DoubleConsumer setProgressFunction, BooleanSupplier isCancelledFunction) {
        String sourcePath = getSourcePath(entry);
        Path path = rootPath.resolve(sourcePath);

        saveContent(api, rootPath, path, entry, getProgressFunction, setProgressFunction, isCancelledFunction);
    }

    @Override
    public void saveContent(API api, Path rootPath, Path path, Container.Entry entry, DoubleSupplier getProgressFunction, DoubleConsumer setProgressFunction, BooleanSupplier isCancelledFunction) {

        DecompilationResult decompiledResult = new DecompilationResult();

        try {
            // Init preferences
            Map<String, String> preferences = api.getPreferences();

            // Init loader
            loader.setEntry(entry);

            // Format internal name
            String entryInternalName = ClassUtil.getInternalName(entry.getPath());

            String decompileEngine = preferences.getOrDefault(DECOMPILE_ENGINE, ENGINE_JD_CORE_V1);
            Loader apiLoader = LoaderUtils.createLoader(preferences, loader, entry);
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

        try {
            ProgressUtil.updateProgress(entry, getProgressFunction, setProgressFunction);
            // update progress of inner classes that were filtered by PackageSourceSaverProvider
            String internalTypeName = ClassUtil.getInternalName(entry.getPath());
            for (Container.Entry e : entry.getParent().getChildren().values()) {
                if (e.getPath().startsWith(internalTypeName + StringConstants.INTERNAL_INNER_SEPARATOR)) {
                    ProgressUtil.updateProgress(e, getProgressFunction, setProgressFunction);
                }
            }

        } catch (IOException e) {
            assert ExceptionUtil.printStackTrace(e);
        }
    }

    private static void writeCodeToFile(Path path, String sourceCode) {
        try (OutputStream os = Files.newOutputStream(path)) {
            os.write(sourceCode.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            assert ExceptionUtil.printStackTrace(e);
        }
    }
}
