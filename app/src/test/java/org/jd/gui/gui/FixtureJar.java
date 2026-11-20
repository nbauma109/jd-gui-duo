/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.gui;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

import javax.tools.JavaCompiler;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

/**
 * Builds a small, deterministic jar (with sources compiled on the fly) that the GUI tests open in the application.
 */
final class FixtureJar {

    static final String PACKAGE = "demo";
    static final String GREETER = "Greeter";
    static final String SHAPE = "Shape";

    private static final String GREETER_SOURCE = """
            package demo;

            public class Greeter implements Shape {
                private final String name;

                public Greeter(String name) {
                    this.name = name;
                }

                public String greet() {
                    return "Hello, " + name + "!";
                }

                @Override
                public double area() {
                    return 42.0;
                }

                public static class Nested {
                    public int answer() {
                        return 42;
                    }
                }
            }
            """;

    private static final String SHAPE_SOURCE = """
            package demo;

            public interface Shape {
                double area();
            }
            """;

    /** A tiny program that keeps running, used as the target of the remote debugging tests. */
    private static final String MAIN_SOURCE = """
            package demo;

            public class Main {
                public static void main(String[] args) throws Exception {
                    Greeter greeter = new Greeter("debugger");
                    while (true) {
                        greeter.greet();
                        Thread.sleep(50);
                    }
                }
            }
            """;

    private FixtureJar() {
    }

    /** Directory holding the compiled classes of the last {@link #create(Path)} call. */
    static Path classesDir(Path dir) {
        return dir.resolve("classes");
    }

    /** Compiles the fixture sources and packs them into {@code <dir>/fixture.jar}. */
    static Path create(Path dir) throws IOException {
        Path classes = Files.createDirectories(dir.resolve("classes"));
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("A JDK is required to build the GUI test fixtures");
        }
        List<SimpleJavaFileObject> sources = List.of(source(GREETER, GREETER_SOURCE), source(SHAPE, SHAPE_SOURCE), source("Main", MAIN_SOURCE));
        boolean ok = compiler.getTask(null, null, null, List.of("-g", "-d", classes.toString()), null, sources).call();
        if (!ok) {
            throw new IllegalStateException("Could not compile the GUI test fixtures");
        }
        Path jar = dir.resolve("fixture.jar");
        try (OutputStream out = Files.newOutputStream(jar);
             JarOutputStream jos = new JarOutputStream(out);
             Stream<Path> files = Files.walk(classes)) {
            for (Path file : (Iterable<Path>) files.filter(Files::isRegularFile).sorted()::iterator) {
                jos.putNextEntry(new JarEntry(classes.relativize(file).toString().replace('\\', '/')));
                Files.copy(file, jos);
                jos.closeEntry();
            }
        }
        return jar;
    }

    private static SimpleJavaFileObject source(String simpleName, String code) {
        return new SimpleJavaFileObject(URI.create("string:///" + PACKAGE + "/" + simpleName + ".java"), SimpleJavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return code;
            }
        };
    }
}
