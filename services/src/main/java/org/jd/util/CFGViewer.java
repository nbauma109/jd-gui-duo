/*
 * Copyright (c) 2008, 2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.util;

import org.apache.bcel.classfile.Method;
import org.apache.bcel.classfile.Utility;
import org.jd.core.v1.cfg.ControlFlowGraphPlantUMLWriter;
import org.jd.core.v1.service.converter.classfiletojavasyntax.model.cfg.ControlFlowGraph;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;

import java.awt.Desktop;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;

/**
 * A state diagram writer.
 *
 * http://plantuml.com/state.html
 * http://plantuml.com/plantuml
 */
public final class CFGViewer {

    private CFGViewer() {
    }

    public static void showGraph(ControlFlowGraph cfg, String className) {
        try {
            SourceStringReader reader = new SourceStringReader(ControlFlowGraphPlantUMLWriter.write(cfg));
            final ByteArrayOutputStream os = new ByteArrayOutputStream();
            // Write the first image to "os"
            reader.outputImage(os, new FileFormatOption(FileFormat.SVG));

            // The XML is stored into svg
            final String svg = new String(os.toByteArray(), StandardCharsets.UTF_8);
            Method method = cfg.getMethod();
            String svgFileName = Utility.pathToPackage(className) + '.' + method.getName().replaceAll("[^a-zA-Z0-9\\.\\-]", "_");
            File svgFile = File.createTempFile(svgFileName, ".svg");
            svgFile.deleteOnExit();
            Files.write(svgFile.toPath(), svg.getBytes(StandardCharsets.UTF_8));
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                if (desktop.isSupported(Desktop.Action.BROWSE)) {
                    desktop.browse(svgFile.toURI());
                }
            }
        } catch (IOException e) {
            assert ExceptionUtil.printStackTrace(e);
        }
    }
}
