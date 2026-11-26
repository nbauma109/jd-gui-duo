/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.util;

import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ControlFlowGraphMaker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import javax.tools.ToolProvider;

import static org.assertj.core.api.Assertions.assertThat;

/** The drawing of the control flow graph of a method. The image is written, not opened. */
class CFGViewerTest {

    @TempDir
    Path directory;

    @Test
    void theGraphOfAMethodIsDrawnIntoATemporaryImage() throws IOException {
        Path source = Files.writeString(directory.resolve("Loop.java"),
                "public class Loop { int sum(int n) { int s = 0; for (int i = 0; i < n; i++) { s += i; } return s; } }");
        assertThat(ToolProvider.getSystemJavaCompiler().run(null, null, null, "-d", directory.toString(), source.toString())).isZero();
        JavaClass type = new ClassParser(directory.resolve("Loop.class").toString()).parse();
        Method method = Arrays.stream(type.getMethods()).filter(m -> "sum".equals(m.getName())).findFirst().orElseThrow();

        File image = CFGViewer.writeGraph(new ControlFlowGraphMaker().make(method), "demo/Loop");

        assertThat(image).isFile().content().contains("<svg");
        assertThat(image.getName()).startsWith("demo.Loop.sum").endsWith(".svg");
    }
}
