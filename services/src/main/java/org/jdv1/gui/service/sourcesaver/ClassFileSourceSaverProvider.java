/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jdv1.gui.service.sourcesaver;

import org.jd.core.v1.ClassFileToJavaSourceDecompiler;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ByteCodeWriter;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.core.v1.util.StringConstants;
import org.jd.gui.api.API;
import org.jd.gui.api.model.Container;
import org.jd.gui.service.sourcesaver.AbstractSourceSaverProvider;
import org.jd.gui.util.decompiler.ContainerLoader;
import org.jdv1.gui.util.MethodPatcher;
import org.jdv1.gui.util.decompiler.LineNumberStringBuilderPrinter;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.apache.bcel.Const.MAJOR_1_1;
import static org.apache.bcel.Const.MAJOR_1_5;
import static org.jd.gui.util.decompiler.GuiPreferences.ESCAPE_UNICODE_CHARACTERS;
import static org.jd.gui.util.decompiler.GuiPreferences.JD_CORE_VERSION;
import static org.jd.gui.util.decompiler.GuiPreferences.REALIGN_LINE_NUMBERS;
import static org.jd.gui.util.decompiler.GuiPreferences.USE_JD_CORE_V0;
import static org.jd.gui.util.decompiler.GuiPreferences.WRITE_LINE_NUMBERS;
import static org.jd.gui.util.decompiler.GuiPreferences.WRITE_METADATA;

public class ClassFileSourceSaverProvider extends AbstractSourceSaverProvider {

    protected static final ClassFileToJavaSourceDecompiler DECOMPILER = new ClassFileToJavaSourceDecompiler();

    protected ContainerLoader loader = new ContainerLoader();
    protected LineNumberStringBuilderPrinter printer = new LineNumberStringBuilderPrinter();

    protected org.jdv0.gui.service.sourcesaver.ClassFileSourceSaverProvider v0Saver = new org.jdv0.gui.service.sourcesaver.ClassFileSourceSaverProvider();

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
        try {
            // Call listener
            if (path.toString().indexOf('$') == -1) {
                listener.pathSaved(path);
            }
            // Init preferences
            Map<String, String> preferences = api.getPreferences();
            boolean realignmentLineNumbers = Boolean.parseBoolean(preferences.getOrDefault(REALIGN_LINE_NUMBERS, Boolean.TRUE.toString()));
            boolean unicodeEscape = Boolean.parseBoolean(preferences.getOrDefault(ESCAPE_UNICODE_CHARACTERS, Boolean.FALSE.toString()));
            boolean showLineNumbers = Boolean.parseBoolean(preferences.getOrDefault(WRITE_LINE_NUMBERS, Boolean.TRUE.toString()));

            if (Boolean.parseBoolean(preferences.getOrDefault(USE_JD_CORE_V0, Boolean.FALSE.toString()))) {
                v0Saver.saveContent(api, controller, listener, rootPath, path, entry);
                return;
            }

            Map<String, Object> configuration = new HashMap<>();
            configuration.put("realignLineNumbers", realignmentLineNumbers);

            // Init loader
            loader.setEntry(entry);

            // Init printer
            printer.setRealignmentLineNumber(realignmentLineNumbers);
            printer.setUnicodeEscape(unicodeEscape);
            printer.setShowLineNumbers(showLineNumbers);

            // Format internal name
            String entryPath = entry.getPath();
            assert entryPath.endsWith(StringConstants.CLASS_FILE_SUFFIX);
            String entryInternalName = entryPath.substring(0, entryPath.length() - 6); // 6 = ".class".length()

            // Decompile class file
            DECOMPILER.decompile(loader, printer, entryInternalName, configuration);

            StringBuilder stringBuffer = printer.getStringBuffer();

            // Metadata
            if (Boolean.parseBoolean(preferences.getOrDefault(WRITE_METADATA, "true"))) {
                // Add location
                String location =
                    new File(entry.getUri()).getPath()
                    // Escape "\ u" sequence to prevent "Invalid unicode" errors
                    .replaceAll("(^|[^\\\\])\\\\u", "\\\\\\\\u");
                stringBuffer.append("\n\n/* Location:              ");
                stringBuffer.append(location);
                // Add Java compiler version
                int majorVersion = printer.getMajorVersion();

                if (majorVersion >= MAJOR_1_1) {
                    stringBuffer.append("\n * Java compiler version: ");

                    if (majorVersion >= MAJOR_1_5) {
                        stringBuffer.append(majorVersion - (MAJOR_1_5 - 5));
                    } else {
                        stringBuffer.append(majorVersion - (MAJOR_1_1 - 1));
                    }

                    stringBuffer.append(" (");
                    stringBuffer.append(majorVersion);
                    stringBuffer.append('.');
                    stringBuffer.append(printer.getMinorVersion());
                    stringBuffer.append(')');
                }
                // Add JD-Core version
                stringBuffer.append("\n * JD-Core Version:       ");
                stringBuffer.append(preferences.get(JD_CORE_VERSION));
                stringBuffer.append("\n */");
            }

			String sourceCodeV1 = stringBuffer.toString();
			if (sourceCodeV1.contains(ByteCodeWriter.DECOMPILATION_FAILED_AT_LINE)) {
				String sourceCodeV0 = v0Saver.decompileV0(api, entry);
				String patchedCode = MethodPatcher.patchCode(sourceCodeV1, sourceCodeV0, entry);
				writeCodeToFile(path, patchedCode);
			} else {
				writeCodeToFile(path, sourceCodeV1);
			}
        } catch (Exception t) {
            assert ExceptionUtil.printStackTrace(t);
            if (!Boolean.parseBoolean(api.getPreferences().getOrDefault(USE_JD_CORE_V0, "false"))) {
                v0Saver.saveContent(api, controller, listener, rootPath, path, entry);
            }
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
