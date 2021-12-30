/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jdv0.gui.service.sourcesaver;

import org.jd.core.v1.api.loader.LoaderException;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.api.API;
import org.jd.gui.api.model.Container;
import org.jd.gui.service.sourcesaver.AbstractSourceSaverProvider;
import org.jd.gui.util.decompiler.ContainerLoader;
import org.jd.gui.util.decompiler.GuiPreferences;
import org.jdv0.gui.util.decompiler.PlainTextPrinter;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.apache.bcel.Const.MAJOR_1_1;
import static org.apache.bcel.Const.MAJOR_1_5;
import static org.jd.gui.util.decompiler.GuiPreferences.ESCAPE_UNICODE_CHARACTERS;
import static org.jd.gui.util.decompiler.GuiPreferences.OMIT_THIS_PREFIX;
import static org.jd.gui.util.decompiler.GuiPreferences.REALIGN_LINE_NUMBERS;
import static org.jd.gui.util.decompiler.GuiPreferences.WRITE_DEFAULT_CONSTRUCTOR;
import static org.jd.gui.util.decompiler.GuiPreferences.WRITE_LINE_NUMBERS;
import static org.jd.gui.util.decompiler.GuiPreferences.WRITE_METADATA;

import jd.core.CoreConstants;
import jd.core.Decompiler;
import jd.core.process.DecompilerImpl;

public class ClassFileSourceSaverProvider extends AbstractSourceSaverProvider {

    protected static final Decompiler DECOMPILER = new DecompilerImpl();

    private GuiPreferences preferences = new GuiPreferences();
    private ContainerLoader loader = new ContainerLoader();
    private PlainTextPrinter printer = new PlainTextPrinter();
    private ByteArrayOutputStream baos = new ByteArrayOutputStream();

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
            String decompiledOutput = decompileV0(api, entry);
            writeToFile(path, decompiledOutput);
        } catch (Exception t) {
            assert ExceptionUtil.printStackTrace(t);

            try (BufferedWriter writer = Files.newBufferedWriter(path, Charset.defaultCharset())) {
                writer.write("// INTERNAL ERROR //");
            } catch (IOException ee) {
                assert ExceptionUtil.printStackTrace(ee);
            }
        }
    }

    private static void writeToFile(Path path, String decompiledOutput) {
        try (OutputStream os = Files.newOutputStream(path)) {
            os.write(decompiledOutput.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            assert ExceptionUtil.printStackTrace(e);
        }
    }

    public String decompileV0(API api, Container.Entry entry)
            throws UnsupportedEncodingException, LoaderException {
        // Init preferences
        Map<String, String> p = api.getPreferences();
        boolean showLineNumbers = getPreferenceValue(p, WRITE_LINE_NUMBERS, true);
        preferences.setUnicodeEscape(getPreferenceValue(p, ESCAPE_UNICODE_CHARACTERS, false));
        preferences.setShowPrefixThis(! getPreferenceValue(p, OMIT_THIS_PREFIX, false));
        preferences.setShowDefaultConstructor(getPreferenceValue(p, WRITE_DEFAULT_CONSTRUCTOR, false));
        preferences.setRealignmentLineNumber(getPreferenceValue(p, REALIGN_LINE_NUMBERS, true));
        preferences.setShowLineNumbers(showLineNumbers);

        // Init loader
        loader.setEntry(entry);

        // Init printer
        baos.reset();
        PrintStream ps = new PrintStream(baos, true, StandardCharsets.UTF_8.name());
        printer.setPrintStream(ps);
        printer.setPreferences(preferences);

        // Decompile class file
        DECOMPILER.decompile(preferences, loader, printer, entry.getPath());

        // Metadata
        if (getPreferenceValue(p, WRITE_METADATA, true)) {
            // Add location
            String location =
                new File(entry.getUri()).getPath()
                // Escape "\ u" sequence to prevent "Invalid unicode" errors
                .replaceAll("(^|[^\\\\])\\\\u", "\\\\\\\\u");
            ps.println();
            ps.println();
            ps.print("/* Location:              ");
            ps.print(location);
            // Add Java compiler version
            int majorVersion = printer.getMajorVersion();

            if (majorVersion >= MAJOR_1_1) {
                ps.println();
                ps.print(" * Java compiler version: ");

                if (majorVersion >= MAJOR_1_5) {
                    ps.print(majorVersion - (MAJOR_1_5 - 5));
                } else {
                    ps.print(majorVersion - (MAJOR_1_1 - 1));
                }

                ps.print(" (");
                ps.print(majorVersion);
                ps.print('.');
                ps.print(printer.getMinorVersion());
                ps.print(')');
            }
            // Add JD-Core version
            ps.println();
            ps.print(" * JD-Core Version:       ");
            ps.println(CoreConstants.JD_CORE_VERSION);
            ps.print(" */");
        }
        return new String(baos.toByteArray(), StandardCharsets.UTF_8);
    }

    protected static boolean getPreferenceValue(Map<String, String> preferences, String key, boolean defaultValue) {
        String v = preferences.get(key);

        if (v == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(v);
    }
}
